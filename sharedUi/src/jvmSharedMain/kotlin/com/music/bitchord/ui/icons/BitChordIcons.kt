package com.music.bitchord.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The icon set, taken from the reference web player's own geometry.
 *
 * Two families, exactly as the web app has them. Most glyphs are Lucide: a 24-unit
 * box, a 2-unit round-capped pen, no fill — [lucide]. The player's transport, its
 * star, its queue and its lyric marks are the filled Apple shapes the web app
 * ships alongside Lucide: one silhouette in a box of its own, tinted flat —
 * [glyph].
 *
 * [Home] and the phone tab bar are the exception and say why they are one.
 */
object BitChordIcons {

    private const val STROKE = 2.2f
    private val stroke = SolidColor(Color.Black)

    /** A Lucide glyph: one or more round-pen strokes in the 24-unit box, never filled. */
    private fun lucide(name: String, vararg pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            for (d in pathData) {
                addPath(
                    pathData = addPathNodes(d),
                    stroke = stroke,
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    /**
     * A flat filled glyph in a square box of [viewport] units.
     *
     * `addPathNodes` keeps the web app's own subpath directions, which is what
     * makes the two-contour shapes — the star's ring, the lyric bubble's wall —
     * come out as outlines rather than solid blocks, under the default non-zero
     * fill.
     */
    private fun glyph(name: String, viewport: Float, vararg pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = viewport, viewportHeight = viewport,
        ).apply {
            for (d in pathData) addPath(pathData = addPathNodes(d), fill = stroke)
        }.build()

    /**
     * The Apple transport, as the web app draws it.
     *
     * The web sets these in a `2 0 28 28` viewBox, so every coordinate below is
     * the published one two units left: [ImageVector] can only start a viewport
     * at the origin, and a box that is two units wider is a glyph that is two
     * units smaller.
     */
    private const val TRANSPORT_BOX = 28f

    /** The queue button's glyph. */
    val Queue: ImageVector by lazy {
        glyph(
            "bc_queue", 18f,
            "M2.634 5.537a.906.906 0 1 0 0-1.813.906.906 0 1 0 0 1.813m3.192-.325h9.865a.576.576 0 0 0 .585-.578.58.58 0 0 0-.585-.585H5.826a.574.574 0 0 0-.585.585c0 .325.253.578.585.578M2.634 9.906c.506 0 .91-.404.91-.91a.906.906 0 0 0-.91-.91.906.906 0 0 0-.91.91c0 .506.405.91.91.91m3.192-.325h9.865a.582.582 0 1 0 0-1.162H5.826a.57.57 0 0 0-.585.577c0 .325.253.585.585.585m-3.192 4.694a.91.91 0 1 0-.001-1.82.91.91 0 0 0 0 1.82zm3.192-.332h9.865a.576.576 0 0 0 .585-.577.58.58 0 0 0-.585-.585H5.826a.574.574 0 0 0-.585.585c0 .324.253.577.585.577",
        )
    }

    /**
     * The lyric mark, in both states: two sixes in a bubble, filled when the
     * sheet is up and outlined when it is not.
     */
    val Lyrics: ImageVector by lazy {
        glyph(
            "bc_lyrics_on", 64f,
            "M17.347 62.821c1.254 0 2.21-.572 3.705-1.933L31.78 51.26h18.443C58.943 51.26 64 46.13 64 37.504V14.956C64 6.33 58.944 1.179 50.223 1.179H13.777C5.057 1.179 0 6.322 0 14.956v22.548C0 46.137 5.27 51.26 13.456 51.26h.994v8.327c0 1.97 1.095 3.235 2.897 3.235zm-.108-39.64c0-3.71 2.79-6.37 6.53-6.37 4.18 0 6.89 3.383 6.89 7.694 0 7.102-5.871 11.086-9.19 11.086-.917 0-1.596-.593-1.596-1.43 0-.742.387-1.242 1.403-1.474 2.4-.587 4.629-2.31 5.53-4.606h-.407c-.823.983-2.108 1.318-3.512 1.318-3.417 0-5.648-2.669-5.648-6.217zm16.387 0c0-3.71 2.77-6.37 6.508-6.37 4.18 0 6.912 3.383 6.912 7.694 0 7.102-5.871 11.086-9.179 11.086-.928 0-1.617-.593-1.617-1.43 0-.742.39-1.242 1.392-1.474 2.436-.587 4.654-2.31 5.551-4.606h-.407c-.823.983-2.108 1.318-3.523 1.318-3.405 0-5.637-2.669-5.637-6.217z",
        )
    }

    val LyricsOff: ImageVector by lazy {
        glyph(
            "bc_lyrics_off", 64f,
            "M18.53 62.724c1.764 0 3.115-.81 5.257-2.707l9.816-8.638h16.62c8.72 0 13.777-5.152 13.777-13.777V15.053c0-8.625-5.056-13.777-13.777-13.777H13.777C5.057 1.276 0 6.42 0 15.053v22.549c0 8.633 5.27 13.777 13.456 13.777h1.016v6.793c0 2.812 1.511 4.552 4.057 4.552zm1.57-7.16v-8.11c0-1.81-.805-2.485-2.486-2.485h-3.55c-5.165 0-7.654-2.603-7.654-7.654V15.34c0-5.033 2.489-7.632 7.654-7.632h35.872c5.149 0 7.654 2.599 7.654 7.632v21.975c0 5.051-2.505 7.654-7.654 7.654H33.188c-1.835 0-2.702.33-4.012 1.65zm-2.212-32.177c0 3.398 2.156 5.936 5.388 5.936 1.361 0 2.592-.302 3.372-1.263h.385c-.868 2.231-3 3.845-5.303 4.4-.95.243-1.327.737-1.327 1.425 0 .8.658 1.36 1.51 1.36 3.174 0 8.8-3.775 8.8-10.6 0-4.138-2.602-7.336-6.588-7.336-3.576 0-6.237 2.518-6.237 6.078m15.663 0c0 3.398 2.134 5.936 5.387 5.936 1.34 0 2.593-.302 3.373-1.263h.39c-.865 2.231-3.023 3.845-5.308 4.4-.947.243-1.327.737-1.327 1.425 0 .8.636 1.36 1.51 1.36 3.178 0 8.779-3.775 8.779-10.6 0-4.138-2.577-7.336-6.567-7.336-3.577 0-6.237 2.518-6.237 6.078",
        )
    }

    /**
     * Apple's filled play. It is a rounded triangle, not three straight edges:
     * the web app's play is drawn with corner radii of its own, and a plain
     * polygon at 48dp reads as a hard wedge next to the round-capped set.
     */
    val TransportPlay: ImageVector by lazy {
        glyph(
            "bc_transport_play", TRANSPORT_BOX,
            "M8.345 23.287c0.415 0 0.763 -0.15 1.22 -0.407l12.742 -7.404c0.838 -0.481 1.178 -0.855 1.178 -1.46 0 -0.599 -0.34 -0.972 -1.178 -1.462L9.565 5.158c-0.457 -0.265 -0.805 -0.407 -1.22 -0.407 -0.789 0 -1.345 0.606 -1.345 1.57V21.71c0 0.971 0.556 1.577 1.345 1.577z",
        )
    }

    val TransportPause: ImageVector by lazy {
        glyph(
            "bc_transport_pause", TRANSPORT_BOX,
            "M11.293 22.772c0.955 0 1.436 -0.481 1.436 -1.436V6.677c0 -0.98 -0.481 -1.427 -1.436 -1.427h-2.457c-0.954 0 -1.436 0.473 -1.436 1.427v14.66c-0.008 0.954 0.473 1.435 1.436 1.435h2.457zm7.87 0c0.954 0 1.427 -0.481 1.427 -1.436V6.677c0 -0.98 -0.473 -1.427 -1.428 -1.427h-2.465c-0.955 0 -1.428 0.473 -1.428 1.427v14.66c0 0.954 0.473 1.435 1.428 1.435h2.465z",
        )
    }

    val TransportNext: ImageVector by lazy {
        glyph(
            "bc_transport_next", TRANSPORT_BOX,
            "M16.14 20.68c0.365 0 0.672 -0.107 1.038 -0.323l8.508 -4.997c0.623 -0.365 0.938 -0.814 0.938 -1.37 0 -0.564 -0.307 -0.988 -0.938 -1.361l-8.508 -4.997c-0.366 -0.216 -0.68 -0.324 -1.046 -0.324 -0.73 0 -1.337 0.556 -1.337 1.569v4.773c-0.108 -0.399 -0.406 -0.73 -0.904 -1.021L5.382 7.632c-0.357 -0.216 -0.672 -0.324 -1.037 -0.324 -0.73 0 -1.345 0.556 -1.345 1.569v10.235c0 1.013 0.614 1.569 1.345 1.569 0.365 0 0.68 -0.108 1.037 -0.324l8.509 -4.997c0.49 -0.29 0.796 -0.631 0.904 -1.038v4.79c0 1.013 0.615 1.569 1.345 1.569z",
        )
    }

    /**
     * Back is forward drawn the wrong way round, which is exactly how the web
     * app does it — one shape, mirrored in its layer rather than a second set of
     * numbers to keep in step. [androidx.compose.ui.graphics.vector.ImageVector]
     * has no per-path transform, so the mirror is the same arithmetic done once
     * here: every abscissa flipped about the box's centre.
     */
    val TransportPrevious: ImageVector by lazy {
        glyph(
            "bc_transport_previous", TRANSPORT_BOX,
            "M11.86 20.68c-0.365 0 -0.672 -0.107 -1.038 -0.323l-8.508 -4.997c-0.623 -0.365 -0.938 -0.814 -0.938 -1.37 0 -0.564 0.307 -0.988 0.938 -1.361l8.508 -4.997c0.366 -0.216 0.68 -0.324 1.046 -0.324 0.73 0 1.337 0.556 1.337 1.569v4.773c0.108 -0.399 0.406 -0.73 0.904 -1.021L22.618 7.632c0.357 -0.216 0.672 -0.324 1.037 -0.324 0.73 0 1.345 0.556 1.345 1.569v10.235c0 1.013 -0.614 1.569 -1.345 1.569 -0.365 0 -0.68 -0.108 -1.037 -0.324l-8.509 -4.997c-0.49 -0.29 -0.796 -0.631 -0.904 -1.038v4.79c0 1.013 -0.615 1.569 -1.345 1.569z",
        )
    }

    val TransportShuffle: ImageVector by lazy {
        glyph(
            "bc_transport_shuffle", TRANSPORT_BOX,
            "M18.767 20.44a0.81 0.81 0 0 0 0.49 -0.183l2.58 -2.174c0.316 -0.266 0.316 -0.681 0 -0.955l-2.58 -2.183a0.81 0.81 0 0 0 -0.49 -0.183c-0.415 0 -0.673 0.258 -0.673 0.673v1.245h-1.162c-0.739 0 -1.195 -0.233 -1.718 -0.847l-1.527 -1.801 1.527 -1.81c0.54 -0.63 0.946 -0.847 1.677 -0.847h1.203v1.279c0 0.407 0.258 0.664 0.673 0.664a0.801 0.801 0 0 0 0.49 -0.174l2.58 -2.175c0.316 -0.266 0.316 -0.69 0 -0.955l-2.58 -2.183a0.761 0.761 0 0 0 -0.49 -0.183c-0.415 0 -0.673 0.258 -0.673 0.665v1.386h-1.212c-1.228 0 -1.992 0.34 -2.863 1.386l-1.412 1.668 -1.469 -1.751c-0.805 -0.946 -1.569 -1.303 -2.747 -1.303H6.896c-0.53 0 -0.896 0.348 -0.896 0.838s0.365 0.838 0.896 0.838h1.437c0.697 0 1.162 0.225 1.685 0.847l1.519 1.801 -1.52 1.81c-0.53 0.623 -0.954 0.847 -1.643 0.847H6.896c-0.53 0 -0.896 0.348 -0.896 0.838s0.365 0.838 0.896 0.838h1.536c1.179 0 1.901 -0.356 2.706 -1.303l1.478 -1.751 1.444 1.718c0.822 0.98 1.627 1.336 2.822 1.336h1.212v1.412c0 0.415 0.258 0.672 0.673 0.672z",
        )
    }

    val TransportRepeat: ImageVector by lazy {
        glyph(
            "bc_transport_repeat", TRANSPORT_BOX,
            "M7.545 14.272a0.856 0.856 0 0 0 0.863 -0.855v-0.448c0 -1.004 0.706 -1.677 1.785 -1.677h5.005v1.362c0 0.407 0.258 0.664 0.673 0.664a0.745 0.745 0 0 0 0.49 -0.183l2.581 -2.166c0.316 -0.266 0.316 -0.69 0 -0.955l-2.581 -2.183a0.745 0.745 0 0 0 -0.49 -0.183c-0.415 0 -0.672 0.258 -0.672 0.665v1.294h-4.881c-2.217 0 -3.628 1.254 -3.628 3.213v0.597c0 0.474 0.382 0.855 0.855 0.855zm4.864 5.952c0.407 0 0.664 -0.257 0.664 -0.664v-1.303h4.881c2.225 0 3.628 -1.254 3.628 -3.213v-0.597a0.854 0.854 0 1 0 -1.71 0v0.448c0 1.004 -0.714 1.677 -1.793 1.677h-5.006v-1.353c0 -0.407 -0.257 -0.664 -0.664 -0.664a0.767 0.767 0 0 0 -0.498 0.182l-2.573 2.175c-0.324 0.257 -0.315 0.68 0 0.946l2.573 2.192a0.807 0.807 0 0 0 0.498 0.174z",
        )
    }

    val TransportRepeatOne: ImageVector by lazy {
        glyph(
            "bc_transport_repeat_one", TRANSPORT_BOX,
            "M20.752 12.313c0.473 0 0.747 -0.257 0.747 -0.771V8.503c0 -0.54 -0.357 -0.904 -0.888 -0.904 -0.44 0 -0.698 0.14 -1.038 0.398l-0.838 0.656c-0.2 0.15 -0.266 0.299 -0.266 0.473 0 0.257 0.19 0.465 0.498 0.465 0.133 0 0.24 -0.042 0.349 -0.125l0.614 -0.514h0.058v2.59c0 0.514 0.274 0.771 0.764 0.771zm-13.207 1.96a0.84 0.84 0 0 0 0.863 -0.856v-0.448c0 -1.004 0.706 -1.677 1.785 -1.677h3.403v1.362c0 0.407 0.258 0.664 0.673 0.664a0.745 0.745 0 0 0 0.49 -0.183l2.581 -2.166c0.316 -0.266 0.316 -0.69 0 -0.955L14.76 7.831a0.745 0.745 0 0 0 -0.49 -0.183c-0.415 0 -0.673 0.258 -0.673 0.665v1.294h-3.278c-2.217 0 -3.628 1.254 -3.628 3.213v0.597c0 0.49 0.374 0.855 0.855 0.855zm4.864 5.951c0.407 0 0.664 -0.257 0.664 -0.664v-1.303h4.881c2.225 0 3.628 -1.254 3.628 -3.213v-0.597a0.838 0.838 0 0 0 -0.855 -0.855 0.833 0.833 0 0 0 -0.855 0.855v0.448c0 1.004 -0.714 1.677 -1.793 1.677h-5.006v-1.353c0 -0.407 -0.257 -0.664 -0.664 -0.664a0.767 0.767 0 0 0 -0.498 0.182l-2.573 2.175c-0.324 0.257 -0.315 0.68 0 0.946l2.573 2.192a0.807 0.807 0 0 0 0.498 0.174z",
        )
    }

    /**
     * The speaker, with as many of its three arcs lit as the level asks for.
     *
     * The web app dims the arcs it has not reached to a quarter rather than
     * leaving them out, so the glyph never changes shape as the volume moves —
     * only how much of it is lit.
     */
    fun TransportVolume(level: Float): ImageVector = volume(level)

    private val volumeArcs = listOf(
        "M36.864 41.108c0.7065 0.4987 1.7246 0.3532 2.3271 -0.4779 1.6205 -2.1817 2.5971 -5.3815 2.5971 -8.6436 0 -3.2621 -0.9766 -6.4411 -2.5971 -8.6436 -0.6025 -0.8311 -1.5999 -0.9765 -2.3271 -0.4987 -0.8934 0.6234 -1.0181 1.683 -0.3532 2.5765 1.2051 1.6207 1.9323 4.0932 1.9323 6.5658 0 2.4726 -0.7688 4.9451 -1.9531 6.5866 -0.6233 0.8935 -0.5194 1.9116 0.374 2.5349z",
        "M45.154 46.718c0.8102 0.5402 1.8077 0.3325 2.3894 -0.4987 2.7426 -3.8231 4.3426 -8.9137 4.3426 -14.233 0 -5.3399 -1.5583 -10.451 -4.3426 -14.254 -0.6025 -0.8103 -1.5791 -1.0181 -2.3894 -0.4779 -0.7898 0.5402 -0.9145 1.5583 -0.2911 2.4518 2.2647 3.3245 3.6779 7.6878 3.6779 12.28s-1.3923 8.9969 -3.6779 12.28c-0.6025 0.8935 -0.4987 1.9116 0.2911 2.4518z",
        "M53.527 52.39c0.7689 0.5195 1.7872 0.3117 2.3898 -0.5402 3.8438 -5.423 6.0255 -12.446 6.0255 -19.864s-2.2443 -14.42 -6.0255 -19.864c-0.6025 -0.8727 -1.6209 -1.0805 -2.3898 -0.5402 -0.7893 0.561 -0.914 1.5791 -0.3115 2.4518 3.3451 4.9244 5.423 11.241 5.423 17.952 0 6.7113 -1.9945 13.132 -5.423 17.952 -0.6025 0.8727 -0.4779 1.8908 0.3115 2.4518z",
    )

    private fun volume(level: Float): ImageVector {
        val lit = when {
            level <= 0f -> 0
            level < 1f / 3f -> 1
            level < 2f / 3f -> 2
            else -> 3
        }
        return ImageVector.Builder(
            name = "bc_transport_volume_$lit",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 64f, viewportHeight = 64f,
        ).apply {
            addPath(
                pathData = addPathNodes(
                    "M25.477 51.06c1.4129 0 2.431 -1.0389 2.431 -2.431v-33.141c0 -1.3921 -1.0181 -2.5349 -2.4726 -2.5349 -1.0181 0 -1.7038 0.4363 -2.805 1.4752l-9.2046 8.6644c-0.1454 0.1246 -0.3117 0.187 -0.5194 0.187h-6.2126c-2.9297 0 -4.5088 1.5999 -4.5088 4.7374v8.0411c0 3.1167 1.5791 4.7166 4.5088 4.7166h6.2126c0.2078 0 0.374 0.0623 0.5195 0.187l9.2046 8.7475c0.9973 0.935 1.8285 1.3506 2.8466 1.3506z",
                ),
                fill = stroke,
            )
            volumeArcs.forEachIndexed { index, arc ->
                addPath(
                    pathData = addPathNodes(arc),
                    fill = stroke,
                    fillAlpha = if (index < lit) 1f else 0.25f,
                )
            }
        }.build()
    }

    /**
     * The player's like control, in two weights — Apple Music's star, drawn as
     * the web app draws it.
     *
     * Filled rather than merely tinted when set: the player draws every glyph
     * white on artwork, where a colour change alone is the one signal the
     * backdrop can swallow. A shape change survives any album cover.
     *
     * [Star] is a ring, not a stroked outline: one contour for the points and a
     * second, wound the other way, for the inside of them.
     */
    val Star: ImageVector by lazy {
        glyph(
            "bc_star", 64f,
            "M13.559 60.051c1.102 0.86 2.5 0.565 4.166 -0.645l14.218 -10.455L46.19 59.406c1.666 1.21 3.037 1.505 4.166 0.645 1.102 -0.833 1.344 -2.204 0.672 -4.166l-5.618 -16.718 14.353 -10.32c1.666 -1.183 2.338 -2.42 1.908 -3.764 -0.43 -1.29 -1.693 -1.935 -3.763 -1.908l-17.605 0.108 -5.348 -16.8C34.308 4.496 33.34 3.5 31.944 3.5c-1.372 0 -2.34 0.995 -2.984 2.984L23.61 23.283l-17.605 -0.108c-2.07 -0.027 -3.333 0.618 -3.763 1.908 -0.457 1.344 0.242 2.58 1.909 3.763l14.352 10.321 -5.617 16.718c-0.672 1.962 -0.43 3.333 0.672 4.166Zm3.87 -5.321c-0.054 -0.054 -0.027 -0.081 0 -0.242l5.349 -15.374c0.376 -1.049 0.161 -1.882 -0.78 -2.527L8.613 27.341c-0.134 -0.08 -0.161 -0.134 -0.134 -0.215 0.027 -0.08 0.08 -0.08 0.242 -0.08l16.26 0.295c1.103 0.027 1.802 -0.43 2.151 -1.532l4.677 -15.562c0.027 -0.162 0.08 -0.215 0.134 -0.215 0.08 0 0.135 0.053 0.162 0.215l4.676 15.562c0.35 1.102 1.048 1.559 2.15 1.532l16.261 -0.296c0.162 0 0.216 0 0.243 0.081 0.027 0.08 -0.027 0.134 -0.135 0.215l-13.385 9.246c-0.94 0.645 -1.156 1.478 -0.78 2.527l5.35 15.374c0.026 0.161 0.053 0.188 0 0.242 -0.055 0.08 -0.135 0.026 -0.243 -0.054l-12.928 -9.864c-0.86 -0.672 -1.855 -0.672 -2.715 0l-12.928 9.864c-0.107 0.08 -0.188 0.134 -0.242 0.054Z",
        )
    }

    val StarFilled: ImageVector by lazy {
        glyph(
            "bc_star_filled", 64f,
            "M13.559 60.051c1.102 0.86 2.5 0.565 4.166 -0.645l14.218 -10.455L46.19 59.406c1.666 1.21 3.037 1.505 4.166 0.645 1.102 -0.833 1.344 -2.204 0.672 -4.166l-5.618 -16.718 14.353 -10.32c1.666 -1.183 2.338 -2.42 1.908 -3.764 -0.43 -1.29 -1.693 -1.935 -3.763 -1.908l-17.605 0.108 -5.348 -16.8C34.308 4.496 33.34 3.5 31.944 3.5c-1.372 0 -2.34 0.995 -2.984 2.984L23.61 23.283l-17.605 -0.108c-2.07 -0.027 -3.333 0.618 -3.763 1.908 -0.457 1.344 0.242 2.58 1.909 3.763l14.352 10.321 -5.617 16.718c-0.672 1.962 -0.43 3.333 0.672 4.166Z",
        )
    }

    // ---- The rest of the app: Lucide, at Lucide's own weight ----

    /** Close. The player's way out, in its top-left corner. */
    val Close: ImageVector by lazy {
        lucide("bc_close", "M18 6 6 18", "m6 6 12 12")
    }

    /** More, in a row of three dots. */
    val Ellipsis: ImageVector by lazy {
        lucide(
            "bc_ellipsis",
            "M5 11a1 1 0 1 0 0 2a1 1 0 1 0 0 -2z",
            "M12 11a1 1 0 1 0 0 2a1 1 0 1 0 0 -2z",
            "M19 11a1 1 0 1 0 0 2a1 1 0 1 0 0 -2z",
        )
    }

    val Check: ImageVector by lazy {
        lucide("bc_check", "M20 6 9 17l-5-5")
    }

    /** Adding something — a new playlist, on the library shelf. */
    val Plus: ImageVector by lazy {
        lucide("bc_plus", "M5 12h14", "M12 5v14")
    }

    val Minus: ImageVector by lazy {
        lucide("bc_minus", "M5 12h14")
    }

    val ChevronRight: ImageVector by lazy {
        lucide("bc_chevron_right", "m9 18 6-6-6-6")
    }

    val ChevronLeft: ImageVector by lazy {
        lucide("bc_chevron_left", "m15 18-6-6 6-6")
    }

    val ChevronDown: ImageVector by lazy {
        lucide("bc_chevron_down", "m6 9 6 6 6-6")
    }

    /** Both ways at once — a sort control. */
    val ChevronsUpDown: ImageVector by lazy {
        lucide("bc_chevrons_up_down", "m7 15 5 5 5-5", "m7 9 5-5 5 5")
    }

    val ArrowUpRight: ImageVector by lazy {
        lucide("bc_arrow_up_right", "M7 7h10v10", "M7 17 17 7")
    }

    val ArrowUp: ImageVector by lazy {
        lucide("bc_arrow_up", "m5 12 7-7 7 7", "M12 19V5")
    }

    val ArrowDown: ImageVector by lazy {
        lucide("bc_arrow_down", "M12 5v14", "m19 12-7 7-7-7")
    }

    val Search: ImageVector by lazy {
        lucide(
            "bc_search",
            "m21 21-4.34-4.34",
            "M11 3a8 8 0 1 0 0 16a8 8 0 1 0 0 -16z",
        )
    }

    val Explore: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_explore",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Compass dial
                moveTo(3.4f, 12f)
                arcToRelative(8.6f, 8.6f, 0f, isMoreThanHalf = true, isPositiveArc = true, 17.2f, 0f)
                arcToRelative(8.6f, 8.6f, 0f, isMoreThanHalf = true, isPositiveArc = true, -17.2f, 0f)
                // Needle
                moveTo(15.4f, 8.6f)
                lineTo(13.6f, 13.6f)
                lineTo(8.6f, 15.4f)
                lineTo(10.4f, 10.4f)
                close()
            }
        }.build()
    }

    val Menu: ImageVector by lazy {
        lucide("bc_menu", "M4 5h16", "M4 12h16", "M4 19h16")
    }

    val CogIcon: ImageVector by lazy {
        lucide(
            "bc_settings",
            "M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915",
            "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0 -6z",
        )
    }

    /** Sliders — the web app's second settings mark. */
    val Sliders: ImageVector by lazy {
        lucide(
            "bc_settings_2",
            "M14 17H5",
            "M19 7h-9",
            "M17 14a3 3 0 1 0 0 6a3 3 0 1 0 0 -6z",
            "M7 4a3 3 0 1 0 0 6a3 3 0 1 0 0 -6z",
        )
    }

    /** The queue's own mark: words of a list with a tail going down to its end. */
    val ListMusic: ImageVector by lazy {
        lucide(
            "bc_list_music",
            "M16 5H3", "M11 12H3", "M11 19H3", "M21 16V5",
            "M18 13a3 3 0 1 0 0 6a3 3 0 1 0 0 -6z",
        )
    }

    val ListStart: ImageVector by lazy {
        lucide(
            "bc_list_start",
            "M3 5h6", "M3 12h13", "M3 19h13", "m16 8-3-3 3-3",
            "M21 19V7a2 2 0 0 0-2-2h-6",
        )
    }

    val ListEnd: ImageVector by lazy {
        lucide(
            "bc_list_end",
            "M16 5H3", "M16 12H3", "M9 19H3", "m16 16-3 3 3 3",
            "M21 5v12a2 2 0 0 1-2 2h-6",
        )
    }

    val ListPlus: ImageVector by lazy {
        lucide(
            "bc_list_plus",
            "M16 5H3", "M11 12H3", "M16 19H3", "M18 9v6", "M21 12h-6",
        )
    }

    /** An open ring — the one glyph in the set that is meant to be spun. */
    val Loader: ImageVector by lazy {
        lucide("bc_loader", "M21 12a9 9 0 1 1-6.219-8.56")
    }

    /** Filling the screen. */
    val Maximize: ImageVector by lazy {
        lucide(
            "bc_maximize",
            "M8 3H5a2 2 0 0 0-2 2v3",
            "M21 8V5a2 2 0 0 0-2-2h-3",
            "M3 16v3a2 2 0 0 0 2 2h3",
            "M16 21h3a2 2 0 0 0 2-2v-3",
        )
    }

    val Minimize: ImageVector by lazy {
        lucide(
            "bc_minimize",
            "M8 3v3a2 2 0 0 1-2 2H3",
            "M21 8h-3a2 2 0 0 1-2-2V3",
            "M3 16h3a2 2 0 0 1 2 2v3",
            "M16 21v-3a2 2 0 0 1 2-2h3",
        )
    }

    /** Two arrows apart — opening something out of its own frame. */
    val Maximize2: ImageVector by lazy {
        lucide(
            "bc_maximize_2",
            "M15 3h6v6", "m21 3-7 7", "m3 21 7-7", "M9 21H3v-6",
        )
    }

    /** A second pair of people — Listen Together. */
    val Users: ImageVector by lazy {
        lucide(
            "bc_users",
            "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
            "M16 3.128a4 4 0 0 1 0 7.744",
            "M22 21v-2a4 4 0 0 0-3-3.87",
            "M9 3a4 4 0 1 0 0 8a4 4 0 1 0 0 -8z",
        )
    }

    val Shuffle: ImageVector by lazy {
        lucide(
            "bc_shuffle",
            "m18 14 4 4-4 4",
            "m18 2 4 4-4 4",
            "M2 18h1.973a4 4 0 0 0 3.3-1.7l5.454-8.6a4 4 0 0 1 3.3-1.7H22",
            "M2 6h1.972a4 4 0 0 1 3.6 2.2",
            "M22 18h-6.041a4 4 0 0 1-3.3-1.8l-.359-.45",
        )
    }

    val Repeat: ImageVector by lazy {
        lucide(
            "bc_repeat",
            "m17 2 4 4-4 4",
            "M3 11V9a4 4 0 0 1 4-4h14",
            "m7 10-4 4 4 4",
            "M21 13v2a4 4 0 0 1-4 4H3",
        )
    }

    /** AutoPlay's lemniscate. */
    val Infinity: ImageVector by lazy {
        lucide("bc_infinity", "M6 16c5 0 7-8 12-8a4 4 0 0 1 0 8c-5 0-7-8-12-8a4 4 0 1 0 0 8")
    }

    val LyricsQuote: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_lyrics_quote", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke, strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6f, 3.5f)
                lineTo(18f, 3.5f)
                quadTo(21f, 3.5f, 21f, 6.5f)
                lineTo(21f, 15f)
                quadTo(21f, 18f, 18f, 18f)
                lineTo(10f, 18f)
                lineTo(6f, 21f)
                lineTo(6f, 18f)
                quadTo(3f, 18f, 3f, 15f)
                lineTo(3f, 6.5f)
                quadTo(3f, 3.5f, 6f, 3.5f)
                close()
            }
            path(fill = stroke) {
                for (x in listOf(7.6f, 12.8f)) {
                    moveTo(x, 8f)
                    lineTo(x + 3f, 8f)
                    lineTo(x + 3f, 11.1f)
                    quadTo(x + 3f, 13.2f, x + 0.5f, 13.7f)
                    lineTo(x + 0.5f, 12.3f)
                    quadTo(x + 1.7f, 12f, x + 1.7f, 11f)
                    lineTo(x, 11f)
                    close()
                }
            }
        }.build()
    }

    val Play: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_play",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                fill = stroke,
            ) {
                moveTo(6.8f, 4.8f)
                lineTo(19.2f, 12f)
                lineTo(6.8f, 19.2f)
                close()
            }
        }.build()
    }

    /**
     * The one solid icon in the set, and the tab bar is why.
     *
     * Selection here is carried by tint alone — see `GlassNavBar`, where the
     * unselected tabs are the same glyph at 65% — so the home tab is the anchor
     * of the bar rather than a state of it, and a filled silhouette reads as
     * that anchor at 24dp where a four-stroke outline reads as detail.
     *
     * Filled *and* stroked, with the same path. The outline of a house is all
     * corners — an apex, two shoulders, two door jambs — and stroking the fill
     * with a round-joined pen is what rounds every one of them at once, for a
     * geometry that stays nine straight lines. The numbers below are therefore
     * inset by half the stroke: the pen puts back what they give up, and the
     * door notch is drawn oversized for the same reason, since the pen closes
     * in on it from both jambs.
     */
    val Home: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_home",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                fill = stroke,
                stroke = stroke,
                strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Roof, flush with the walls rather than overhanging them: an
                // eave narrower than the pen it is drawn with comes out as a
                // blob on the corner instead of a ledge.
                moveTo(12f, 4.05f)
                lineTo(19.75f, 11.35f)
                // Right wall, down to the floor
                lineTo(19.75f, 19.65f)
                // Door, cut up out of the bottom edge
                lineTo(14.95f, 19.65f)
                lineTo(14.95f, 15.6f)
                lineTo(9.05f, 15.6f)
                lineTo(9.05f, 19.65f)
                // Left wall, back up to the apex
                lineTo(4.25f, 19.65f)
                lineTo(4.25f, 11.35f)
                close()
            }
        }.build()
    }

    /**
     * The phone tab bar's Explore, Library and Search glyphs: solid-weight
     * iOS shapes rather than this file's stroked family, taken from the
     * outlines of Flutter's cupertino_icons font (MIT) — square_grid_2x2,
     * square_stack_fill and search.
     *
     * Each is the glyph laid out the way the font sets it: the 512-unit em
     * box is the viewport, the baseline sits at the font's ascent share of
     * it, and the advance is centred. So they keep the font's own optical
     * size and weight against one another at any icon size.
     */
    val TabExplore: ImageVector by lazy {
        fontGlyph(
            "bc_tab_explore",
            "M99.5 230.24Q58.5 230.24 58.5 188.24V93.24Q58.5 51.24 99.5 51.24H197.5Q238.5 51.24 238.5 93.24V188.24Q238.5 230.24 197.5 230.24Z" +
                "M314.5 230.24Q273.5 230.24 273.5 188.24V93.24Q273.5 51.24 314.5 51.24H412.5Q453.5 51.24 453.5 93.24V188.24Q453.5 230.24 412.5 230.24Z" +
                "M99.5 200.24H196.5Q207.5 200.24 207.5 188.24V93.24Q207.5 81.24 196.5 81.24H99.5Q88.5 81.24 88.5 93.24V188.24Q88.5 200.24 99.5 200.24Z" +
                "M314.5 200.24H411.5Q422.5 200.24 422.5 188.24V93.24Q422.5 81.24 411.5 81.24H314.5Q303.5 81.24 303.5 93.24V188.24Q303.5 200.24 314.5 200.24Z" +
                "M99.5 445.24Q58.5 445.24 58.5 403.24V307.24Q58.5 266.24 99.5 266.24H197.5Q238.5 266.24 238.5 307.24V403.24Q238.5 445.24 197.5 445.24Z" +
                "M314.5 445.24Q273.5 445.24 273.5 403.24V307.24Q273.5 266.24 314.5 266.24H412.5Q453.5 266.24 453.5 307.24V403.24Q453.5 445.24 412.5 445.24Z" +
                "M99.5 415.24H196.5Q207.5 415.24 207.5 403.24V308.24Q207.5 296.24 196.5 296.24H99.5Q88.5 296.24 88.5 308.24V403.24Q88.5 415.24 99.5 415.24Z" +
                "M314.5 415.24H411.5Q422.5 415.24 422.5 403.24V308.24Q422.5 296.24 411.5 296.24H314.5Q303.5 296.24 303.5 308.24V403.24Q303.5 415.24 314.5 415.24Z",
        )
    }

    /**
     * Two units lower than the font sets it: the back card's top edge rises
     * just past the em box, and the viewport would clip it flat.
     */
    val TabLibrary: ImageVector by lazy {
        fontGlyph(
            "bc_tab_library",
            "M154 27.24Q156 0.24 185 0.24H322Q352 0.24 353 27.24Z" +
                "M116 87.24Q121 56.24 150 56.24H356Q385 56.24 390 87.24Z" +
                "M133 500.24Q66 500.24 66 434.24V187.24Q66 121.24 133 121.24H378Q446 121.24 446 187.24V434.24Q446 500.24 385 500.24Z",
        )
    }

    val TabSearch: ImageVector by lazy {
        fontGlyph(
            "bc_tab_search",
            "M219 374.24Q151 374.24 102.5 325.74Q54 277.24 54 209.24Q54 141.24 102.5 92.74Q151 44.24 219 44.24Q287 44.24 335.5 92.74Q384 141.24 384 209.24Q384 264.24 350 309.24L451 410.24Q458 417.24 458 427.24Q458 438.24 451 445.24Q444 452.24 434 452.24Q423 452.24 416 445.24L315 343.24Q271 374.24 219 374.24Z" +
                "M128 300.24Q166 338.24 219 338.24Q272 338.24 310 300.24Q348 262.24 348 209.24Q348 156.24 310 118.24Q272 80.24 219 80.24Q166 80.24 128 118.24Q90 156.24 90 209.24Q90 262.24 128 300.24Z",
        )
    }

    /** A filled glyph outline in the 512-unit em box — see [TabExplore]. */
    private fun fontGlyph(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 512f, viewportHeight = 512f,
        ).addPath(pathData = addPathNodes(pathData), fill = stroke).build()

    /** Beamed pair of notes, for instrumental stretches in the lyrics. */
    val MusicNote: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_music_note",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            // Heads are solid; stems and beam keep the family's stroke weight.
            path(fill = stroke) {
                moveTo(4.2f, 17.7f)
                arcToRelative(2.9f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 5.8f, 0f)
                arcToRelative(2.9f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, -5.8f, 0f)
                close()
                moveTo(14.2f, 15.9f)
                arcToRelative(2.9f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 5.8f, 0f)
                arcToRelative(2.9f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, -5.8f, 0f)
                close()
            }
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(10f, 17.7f); lineTo(10f, 6.7f)
                moveTo(20f, 15.9f); lineTo(20f, 4.9f)
                moveTo(10f, 6.7f); lineTo(20f, 4.9f)
            }
        }.build()
    }

    /** Arrow pointing down into a tray — offline download. */
    val Download: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_download",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Vertical stem
                moveTo(12f, 4.5f); lineTo(12f, 15.5f)
                // Arrow head
                moveTo(7.5f, 11f); lineTo(12f, 15.5f); lineTo(16.5f, 11f)
                // Tray base
                moveTo(4.5f, 18f); lineTo(19.5f, 18f)
            }
        }.build()
    }

    /**
     * Clock face with two hands — a download asked for but not yet on disk.
     *
     * The dial is the same 8.6 radius as [Explore]'s, so the two sit at the same
     * optical weight when they appear in the same row of header buttons. The
     * hands are one polyline through the centre rather than two strokes: the
     * round join is then the pivot, which is what stops the middle reading as a
     * pair of lines that happen to cross.
     */
    val Clock: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_clock",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Dial (full circle from two arcs)
                moveTo(3.4f, 12f)
                arcToRelative(8.6f, 8.6f, 0f, isMoreThanHalf = true, isPositiveArc = true, 17.2f, 0f)
                arcToRelative(8.6f, 8.6f, 0f, isMoreThanHalf = true, isPositiveArc = true, -17.2f, 0f)
                // Minute hand up, hour hand down to the right
                moveTo(12f, 7.4f); lineTo(12f, 12f); lineTo(15.4f, 13.8f)
            }
        }.build()
    }

    /**
     * A pushpin, not a map marker: a round head with a straight needle
     * kicked out to one side, the way it sits once it's stuck into something.
     * A teardrop reads as "location" the moment it's this small — the two
     * disjoint strokes here are what keep it read as "pinned" instead.
     */
    val Pin: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_pin",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Round head.
                moveTo(9.6f, 8.4f)
                arcToRelative(3.4f, 3.4f, 0f, isMoreThanHalf = true, isPositiveArc = true, 6.8f, 0f)
                arcToRelative(3.4f, 3.4f, 0f, isMoreThanHalf = true, isPositiveArc = true, -6.8f, 0f)
                // Needle, kicked out from the underside of the head to a point.
                moveTo(10.8f, 10.8f)
                lineTo(5.6f, 19.6f)
            }
        }.build()
    }

    /** Speed gauge used by high-performance display settings. */
    val Performance: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_performance",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Open gauge, leaving room for the base line.
                moveTo(4.1f, 16.8f)
                arcToRelative(8.4f, 8.4f, 0f, isMoreThanHalf = true, isPositiveArc = true, 15.8f, 0f)
                // Needle and hub.
                moveTo(12f, 15.8f)
                lineTo(16.7f, 9.3f)
                moveTo(10.9f, 16.9f)
                arcToRelative(1.1f, 1.1f, 0f, isMoreThanHalf = true, isPositiveArc = true, 2.2f, 0f)
                arcToRelative(1.1f, 1.1f, 0f, isMoreThanHalf = true, isPositiveArc = true, -2.2f, 0f)
                // Stable baseline.
                moveTo(5.4f, 19.5f)
                lineTo(18.6f, 19.5f)
            }
        }.build()
    }

    /** Four frame-time bars, used by refresh-rate selection. */
    val FrameRate: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_frame_rate",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(5f, 15.5f); lineTo(5f, 8.5f)
                moveTo(9.7f, 18.5f); lineTo(9.7f, 5.5f)
                moveTo(14.3f, 16.5f); lineTo(14.3f, 7.5f)
                moveTo(19f, 14.5f); lineTo(19f, 9.5f)
            }
        }.build()
    }

    val Library: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_library",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Three upright spines + one leaning — Apple Music's library glyph, thickened
                moveTo(4.6f, 4.8f); lineTo(4.6f, 19.2f)
                moveTo(9.2f, 4.8f); lineTo(9.2f, 19.2f)
                moveTo(13.8f, 4.8f); lineTo(13.8f, 19.2f)
                moveTo(17.2f, 5.6f); lineTo(20.6f, 18.9f)
            }
        }.build()
    }

    /** 2x2 grid icon (4 rounded squares) for switching layout to grid view. */
    val GridView: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_grid_view",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Top-left
                moveTo(6.5f, 4.5f)
                lineTo(8.5f, 4.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 2f)
                lineTo(10.5f, 8.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, 2f)
                lineTo(6.5f, 10.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, -2f)
                lineTo(4.5f, 6.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, -2f)
                close()

                // Top-right
                moveTo(15.5f, 4.5f)
                lineTo(17.5f, 4.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 2f)
                lineTo(19.5f, 8.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, 2f)
                lineTo(15.5f, 10.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, -2f)
                lineTo(13.5f, 6.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, -2f)
                close()

                // Bottom-left
                moveTo(6.5f, 13.5f)
                lineTo(8.5f, 13.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 2f)
                lineTo(10.5f, 17.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, 2f)
                lineTo(6.5f, 19.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, -2f)
                lineTo(4.5f, 15.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, -2f)
                close()

                // Bottom-right
                moveTo(15.5f, 13.5f)
                lineTo(17.5f, 13.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 2f)
                lineTo(19.5f, 17.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, 2f)
                lineTo(15.5f, 19.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, -2f)
                lineTo(13.5f, 15.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, -2f)
                close()
            }
        }.build()
    }

    /** List view icon (horizontal rows) for switching layout to list view. */
    val ListView: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_list_view",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Three horizontal lines with bullet markers
                moveTo(4.5f, 6.5f); lineTo(5.5f, 6.5f)
                moveTo(9.5f, 6.5f); lineTo(19.5f, 6.5f)

                moveTo(4.5f, 12f); lineTo(5.5f, 12f)
                moveTo(9.5f, 12f); lineTo(19.5f, 12f)

                moveTo(4.5f, 17.5f); lineTo(5.5f, 17.5f)
                moveTo(9.5f, 17.5f); lineTo(19.5f, 17.5f)
            }
        }.build()
    }
}
