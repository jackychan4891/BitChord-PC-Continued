# ytm-rs Jam server

The relay behind ytm-rs "Jam" (listen together). It's a Cloudflare Worker written in Rust
with [workers-rs](https://github.com/cloudflare/workers-rs). Each room is one SQLite-backed
Durable Object, which means it runs on the Workers free plan. Audio never passes through
it. Every client streams from YouTube itself, and the server only relays small JSON control
messages. It also keeps the host's last state so late joiners can catch up.

## Deploy

You need Rust with the `wasm32-unknown-unknown` target (`rustup target add wasm32-unknown-unknown`)
and Node (for `npx`). Wrangler runs the build for you through `worker-build`.

```sh
cd server
npx wrangler login
npx wrangler deploy
```

The worker comes up at `https://ytm-rs-jam.<your-subdomain>.workers.dev`.

## Develop and test

```sh
npx wrangler dev              # serves ws://127.0.0.1:8787, no account needed
cd smoke && cargo run         # end-to-end smoke test against it (JAM_URL overrides the address)
```

## Metrics

The dashboard is [grapedjuice.github.io/ytm-rs/jam/stats](https://grapedjuice.github.io/ytm-rs/jam/stats/) (`site/jam/stats/`).
`GET /stats` returns aggregate JSON from a single `Stats` Durable Object: live rooms and
listeners (and the biggest room), totals (rooms created, joins, guest joins, rooms ended,
sessions and their average length), all-time peaks, and the last 30 days of rooms, joins
and peak listeners (UTC). It never includes room codes or names.

Rooms report on every join, leave and end, plus a heartbeat every 10 minutes while people
are in them. A room that stops reporting for 25 minutes drops out of the live counts. Each
report is also logged as JSON (`{"jam": "create", "room": ..., "members": ...}`), so
Workers Logs can query it.

## Protocol (v1)

The client side is `src/jam.rs` (connection) and `src/ui/jam.rs` (sync). Here is a summary.

- **Codes** are 6 characters from `ABCDEFGHJKMNPQRSTUVWXYZ23456789`. Input is case-insensitive.
  Anything else gets HTTP 400.
- **Connect** with `GET /jam/{CODE}?create=1|0&name=<name>&v=1` as a WebSocket.
  - `create=1` makes you host. If the room has members, you get `taken`.
  - `create=0` joins. If the room doesn't exist, you get `not_found`. If you join an
    emptied room during its 5-minute grace period, you become host.
  - A room holds at most 16 members, after that you get `full`.
  - Names are trimmed and stripped of control characters, then cut to 32 chars.
    An empty name becomes "Guest".
- **Keepalive**: send the literal text `ping` and the runtime answers `pong` without waking
  the object.
- **Server to client** messages:
  - `welcome` holds you, host, control, members, the stored state and tick, and the server time.
  - `members` goes out on every join, leave and host change.
  - `control` tells everyone whether guests may control playback.
  - `state` and `tick` are stamped with `ts` and go to everyone except the sender.
  - `req` reaches the host only.
  - `pong`, `error`, and `ended` round out the set.
- **Client to server**:
  - `ping {c}` is for clock sync.
  - Only the host can send `state`, `tick`, `control {on}` and `end`. Anyone else gets `denied`.
  - `req {cmd}` is forwarded to the host. `add` and `next` are always allowed.
    `toggle`, `skip`, `prev`, `seek` and `jump` need guest control on.
  - `bye` leaves the room.
- **Errors** are `not_found`, `taken`, `full` and `bad`. These close the socket with
  4004, 4009, 4029 and 4000. `rate`, `denied`, and `bad` for a single bad frame keep the
  socket open.
- **Rules**:
  - When the host leaves, the earliest-joined member becomes host.
  - When the room empties, the storage alarm wipes it 5 minutes later unless someone rejoins.
  - Each socket is rate-limited to a burst of 20 messages, refilling at 5/s.
  - Frames are capped at 256 KB.
