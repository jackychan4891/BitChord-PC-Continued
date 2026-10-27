package com.music.bitchord.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The icon set, taken from the reference web player's own geometry.
 *
 * Three families, exactly as the web app ships them.
 *
 * Apple's chrome glyphs — the sidebar's rows, its library shelves, the transport,
 * the star, the queue and the lyric marks — are filled silhouettes with no pen at
 * all, in a box of Apple's choosing: [apple] for the 24-unit ones, [glyph] for the
 * transport's 28, the queue's 18 and the star's 64. Apple sizes each shape *inside*
 * the shared box by eye, and the marks it publishes differ by a fifth of the box —
 * its loupe stands 13.9 in the 24 the album box fills — so [apple] scales its family
 * back onto one band, which is what makes a column of them read as one row of marks
 * rather than as a row of squares. That even weight is the only way a set this mixed
 * holds together, and the reason nothing here is drawn with both a fill and a stroke.
 *
 * Everything else — chevrons, menus, settings rows — is Lucide: a 24-unit box, a
 * 2-unit round-capped pen, never filled — [lucide]. The web app does the same, and
 * draws those marks a step smaller than Apple's, since a pen spreads past its
 * geometry where a silhouette cannot.
 *
 * The phone tab bar is the one exception, in [TabExplore]; it says why.
 */
object BitChordIcons {

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
     * How tall an Apple chrome glyph stands in its box once the family is drawn as
     * one column — 15 of the 24 units the sidebar's box is given.
     */
    private const val AppleBand = 0.625f

    /**
     * One of Apple Music's chrome glyphs: a filled silhouette in a square box.
     *
     * These carry no pen at all, which is what lets a whole column of them stay
     * optically even. [alpha] is the web player's own 95%, the softness that
     * keeps a solid shape from shouting against 14sp of text.
     *
     * [fill] is how tall this glyph's own path stands in its box, measured from the
     * published coordinates; give it and the silhouette is scaled about the centre
     * of the box to [AppleBand], so that the nine marks a sidebar column draws all
     * measure the same. Leave it out and the glyph draws exactly as published.
     */
    private fun apple(
        name: String,
        vararg pathData: String,
        viewport: Float = 24f,
        alpha: Float = 1f,
        fill: Float = 0f,
    ): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = viewport, viewportHeight = viewport,
        ).apply {
            // A group carries the scale. `addPath` takes fill, stroke and trim but no
            // transform, and widening the box instead would shrink a glyph *and*
            // slide it off its centre, since the path data is not symmetric about the
            // box the wider viewport would put in the middle.
            if (fill > 0f) {
                val scale = AppleBand / fill
                addGroup(pivotX = viewport / 2f, pivotY = viewport / 2f, scaleX = scale, scaleY = scale)
            }
            for (d in pathData) {
                addPath(pathData = addPathNodes(d), fill = stroke, fillAlpha = alpha)
            }
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

    /** Apple's own magnifier, the mark the web player puts on its Search row. */
    val Search: ImageVector by lazy {
        apple(
            "bc_search",
            "M17.979 18.553c.476 0 .813-.366.813-.835a.8.8 0 0 0-.235-.586l-3.45-3.457a5.6 5.6 0 0 0 1.158-3.413c0-3.098-2.535-5.633-5.633-5.633C7.542 4.63 5 7.156 5 10.262c0 3.098 2.534 5.632 5.632 5.632a5.6 5.6 0 0 0 3.274-1.055l3.472 3.472a.84.84 0 0 0 .6.242m-7.347-3.875c-2.417 0-4.416-2-4.416-4.416s2-4.417 4.416-4.417 4.417 2 4.417 4.417-2 4.416-4.417 4.416",
            alpha = 0.95f,
            fill = 0.580f,
        )
    }

    /** Apple's four-square browse mark, the one the web player puts on its browsing rows. */
    val Explore: ImageVector by lazy {
        apple(
            "bc_explore",
            "M9.92 11.354c.966 0 1.453-.487 1.453-1.49v-3.4c0-1.004-.487-1.483-1.453-1.483H6.452C5.487 4.981 5 5.46 5 6.464v3.4c0 1.003.487 1.49 1.452 1.49zm7.628 0c.965 0 1.452-.487 1.452-1.49v-3.4c0-1.004-.487-1.483-1.452-1.483h-3.46c-.974 0-1.46.479-1.46 1.483v3.4c0 1.003.486 1.49 1.46 1.49zm-7.65-1.073h-3.43q-.397.002-.396-.418v-3.4q-.002-.41.396-.41h3.43c.265 0 .402.137.402.41v3.4c0 .281-.137.418-.403.418m7.634 0h-3.43c-.273 0-.402-.137-.402-.418v-3.4c0-.273.129-.41.403-.41h3.43q.396 0 .395.41v3.4q.002.42-.396.418m-7.612 8.7c.966 0 1.453-.48 1.453-1.483v-3.407c0-.996-.487-1.483-1.453-1.483H6.452c-.965 0-1.452.487-1.452 1.483v3.407c0 1.004.487 1.483 1.452 1.483zm7.628 0c.965 0 1.452-.48 1.452-1.483v-3.407c0-.996-.487-1.483-1.452-1.483h-3.46c-.974 0-1.46.487-1.46 1.483v3.407c0 1.004.486 1.483 1.46 1.483zm-7.65-1.072h-3.43q-.397 0-.396-.41v-3.4c0-.282.13-.418.396-.418h3.43c.265 0 .402.136.402.418v3.4c0 .273-.137.41-.403.41m7.634 0h-3.43c-.273 0-.402-.137-.402-.41v-3.4c0-.282.129-.418.403-.418h3.43c.265 0 .395.136.395.418v3.4q.002.41-.396.41",
            alpha = 0.95f,
            fill = 0.583f,
        )
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

    /** Lucide's own quotation marks — the lyrics row in a menu. */
    val LyricsQuote: ImageVector by lazy {
        lucide(
            "bc_lyrics_quote",
            "M16 3a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2 1 1 0 0 1 1 1v1a2 2 0 0 1-2 2 1 1 0 0 0-1 1v2a1 1 0 0 0 1 1 6 6 0 0 0 6-6V5a2 2 0 0 0-2-2z",
            "M5 3a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2 1 1 0 0 1 1 1v1a2 2 0 0 1-2 2 1 1 0 0 0-1 1v2a1 1 0 0 0 1 1 6 6 0 0 0 6-6V5a2 2 0 0 0-2-2z",
        )
    }

    /** Lucide's own play: a rounded triangle in outline, for menu rows. */
    val Play: ImageVector by lazy {
        lucide(
            "bc_play",
            "M5 5a2 2 0 0 1 3.008-1.728l11.997 6.998a2 2 0 0 1 .003 3.458l-12 7A2 2 0 0 1 5 19z",
        )
    }

    /**
     * Apple's own house, the mark the web player puts on its Listen Now row.
     *
     * Three subpaths under one non-zero fill: the outer silhouette, the wall
     * that is hollowed out of it, and the door left solid. That is how Apple
     * draws an outline without a pen, and it is why this glyph keeps its weight
     * when the row is tinted at any size — nothing about it depends on stroke
     * width.
     */
    val Home: ImageVector by lazy {
        apple(
            "bc_home",
            "M5.93 20.16a1.94 1.94 0 0 1-1.43-.502c-.334-.335-.502-.794-.502-1.393v-7.142c0-.362.062-.688.177-.953.123-.264.326-.529.6-.75l6.145-5.157c.176-.141.344-.247.52-.318q.263-.105.564-.105c.194 0 .388.035.565.105s.352.177.52.318l6.146 5.158c.273.23.467.476.59.75.124.264.177.59.177.96v7.134c0 .59-.159 1.058-.503 1.393q-.503.503-1.428.503zm12.14-1.172q.334 0 .547-.212a.69.69 0 0 0 .22-.511v-7.142c0-.177-.026-.344-.087-.459a1 1 0 0 0-.265-.353l-6.154-5.149a.8.8 0 0 0-.177-.115.4.4 0 0 0-.15-.035.4.4 0 0 0-.158.035l-.177.115-6.145 5.15a1 1 0 0 0-.274.352 1.1 1.1 0 0 0-.088.468v7.133c0 .203.08.379.23.511a.74.74 0 0 0 .546.212zm-8.323-4.7c0-.176.062-.326.177-.432a.6.6 0 0 1 .423-.159h3.315c.176 0 .326.053.432.16s.159.255.159.431v4.973H9.756v-4.973Z",
            fill = 0.680f,
        )
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

    /** Apple's own single note, the mark the web player puts on its Songs row. */
    val MusicNote: ImageVector by lazy {
        apple(
            "bc_music_note",
            "M9.732 19.241c1.077 0 2.688-.79 2.688-2.922V9.617c0-.388.074-.469.418-.542l3.347-.732a.48.48 0 0 0 .403-.484V5.105c0-.388-.315-.637-.689-.563l-3.764.82c-.47.102-.725.359-.725.769l.014 8.144c.037.36-.132.594-.454.66l-1.164.241c-1.465.308-2.154 1.055-2.154 2.16 0 1.122.864 1.905 2.08 1.905",
            fill = 0.613f,
        )
    }

    /** Lucide's own download: a stem, a chevron, and the tray it falls into. */
    val Download: ImageVector by lazy {
        lucide(
            "bc_download",
            "M12 15V3",
            "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4",
            "m7 10 5 5 5-5",
        )
    }

    /**
     * Apple's own clock — the dial and hands as one silhouette with the middle
     * hollowed out, so the mark the web player puts on its Recently Played row
     * carries no pen and stays even beside the other chrome glyphs.
     */
    val Clock: ImageVector by lazy {
        apple(
            "bc_clock",
            "M12 20c4.376 0 8-3.631 8-8 0-4.376-3.631-8-8.008-8C7.624 4 4 7.624 4 12c0 4.369 3.631 8 8 8m0-1.333A6.634 6.634 0 0 1 5.341 12a6.63 6.63 0 0 1 6.651-6.667A6.653 6.653 0 0 1 18.667 12 6.636 6.636 0 0 1 12 18.667m-.008-5.82a.54.54 0 0 0 .55-.549V7.012a.54.54 0 0 0-.55-.541.53.53 0 0 0-.541.54v4.746H7.898a.534.534 0 0 0-.549.541c0 .314.235.55.549.55h4.094z",
            fill = 0.667f,
        )
    }

    /** Lucide's own pushpin: the head, the shoulder it sits on, the needle. */
    val Pin: ImageVector by lazy {
        lucide(
            "bc_pin",
            "M12 17v5",
            "M9 10.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24V16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V7a1 1 0 0 1 1-1 2 2 0 0 0 0-4H8a2 2 0 0 0 0 4 1 1 0 0 1 1 1z",
        )
    }

    /** Lucide's own gauge — the arc the needle sweeps, and the needle. */
    val Performance: ImageVector by lazy {
        lucide("bc_performance", "m12 14 4-4", "M3.34 19a10 10 0 1 1 17.32 0")
    }

    /** Four frame-time bars, used by refresh-rate selection. */
    val FrameRate: ImageVector by lazy {
        lucide("bc_frame_rate", "M5 15.5V8.5", "M9.7 18.5V5.5", "M14.3 16.5V7.5", "M19 14.5V9.5")
    }

    /**
     * Apple's own library grid: nine rounded tiles, each drawn as a wall with
     * the middle taken out of it.
     *
     * The web player sets this glyph inside a `translate(.7 .94) scale(.92237)` to
     * bring its nine-tile block down to the size of the rest of its sidebar. The
     * [fill][apple] below does the same job by the book: the block is published
     * taller than the loupe, so both end up on the band rather than the grid being
     * the odd one out, and the hand-set offset only ever nudged it a unit.
     */
    val Library: ImageVector by lazy {
        apple(
            "bc_library",
            "M5.55 8.73H7.4c.45 0 .8-.13 1.06-.38.25-.26.37-.62.37-1.1V5.5c0-.46-.12-.83-.37-1.08-.26-.26-.6-.39-1.06-.39H5.55c-.45 0-.8.13-1.06.39-.25.25-.37.62-.37 1.08v1.77c0 .47.12.83.37 1.09.26.25.6.38 1.06.38Zm.16-1.14c-.15 0-.26-.04-.34-.12-.07-.07-.1-.2-.1-.35V5.63c0-.15.03-.27.1-.35.08-.07.2-.11.34-.11h1.52c.14 0 .25.04.34.11.08.08.12.2.12.35v1.49c0 .16-.04.28-.12.35a.46.46 0 0 1-.34.12H5.7Zm5.62 1.14h1.84c.46 0 .8-.13 1.06-.38.25-.26.37-.62.37-1.1V5.5c0-.46-.12-.83-.37-1.08a1.4 1.4 0 0 0-1.06-.39h-1.84q-.675 0-1.05.39A1.5 1.5 0 0 0 9.9 5.5v1.77c0 .47.13.83.38 1.09.25.25.6.38 1.05.38Zm.16-1.14c-.15 0-.26-.04-.34-.12-.07-.07-.11-.2-.11-.35V5.63c0-.15.04-.27.11-.35.08-.07.19-.11.34-.11h1.53c.14 0 .25.04.33.11.08.08.11.2.11.35v1.49c0 .16-.04.28-.11.35-.08.08-.2.12-.33.12zm5.63 1.14h1.84c.45 0 .8-.13 1.06-.38.25-.26.38-.62.38-1.1V5.5c0-.46-.13-.83-.38-1.08-.25-.26-.6-.39-1.06-.39h-1.84q-.675 0-1.05.39c-.26.25-.38.62-.38 1.08v1.77c0 .47.12.83.38 1.09.25.25.6.38 1.05.38Zm.16-1.14c-.15 0-.26-.04-.34-.12-.08-.07-.12-.2-.12-.35V5.63c0-.15.04-.27.12-.35.08-.07.2-.11.34-.11h1.52c.14 0 .25.04.33.11.08.08.12.2.12.35v1.49c0 .16-.04.28-.12.35q-.105.12-.33.12zM5.55 14.35H7.4c.45 0 .8-.13 1.06-.38.25-.26.37-.62.37-1.08V11.1c0-.47-.12-.83-.37-1.09-.26-.25-.6-.38-1.06-.38H5.55c-.45 0-.8.13-1.06.38-.25.26-.37.62-.37 1.09v1.78c0 .46.12.82.37 1.08.26.25.6.38 1.06.38Zm.16-1.14c-.15 0-.26-.04-.34-.12-.07-.08-.1-.2-.1-.35v-1.5c0-.15.03-.26.1-.34.08-.08.2-.12.34-.12h1.52c.14 0 .25.04.34.12.08.08.12.2.12.34v1.5c0 .15-.04.27-.12.35a.45.45 0 0 1-.34.12H5.7Zm5.62 1.14h1.84c.46 0 .8-.13 1.06-.38.25-.26.37-.62.37-1.08V11.1c0-.47-.12-.83-.37-1.09a1.4 1.4 0 0 0-1.06-.38h-1.84c-.45 0-.8.13-1.05.38-.25.26-.38.62-.38 1.09v1.78c0 .46.13.82.38 1.08.25.25.6.38 1.05.38Zm.16-1.14c-.15 0-.26-.04-.34-.12a.52.52 0 0 1-.11-.35v-1.5c0-.15.04-.26.11-.34.08-.08.19-.12.34-.12h1.53q.21 0 .33.12c.08.08.11.2.11.34v1.5c0 .15-.04.27-.11.35-.08.08-.2.12-.33.12zm5.63 1.14h1.84c.45 0 .8-.13 1.06-.38.25-.26.38-.62.38-1.08V11.1c0-.47-.13-.83-.38-1.09-.25-.25-.6-.38-1.06-.38h-1.84c-.45 0-.8.13-1.05.38-.26.26-.38.62-.38 1.09v1.78c0 .46.12.82.38 1.08.25.25.6.38 1.05.38Zm.16-1.14c-.15 0-.26-.04-.34-.12s-.12-.2-.12-.35v-1.5c0-.15.04-.26.12-.34s.2-.12.34-.12h1.52q.21 0 .33.12c.08.08.12.2.12.34v1.5c0 .15-.04.27-.12.35q-.105.12-.33.12zM5.55 19.97H7.4c.45 0 .8-.13 1.06-.38.25-.26.37-.62.37-1.1v-1.76c0-.47-.12-.83-.37-1.09-.26-.25-.6-.38-1.06-.38H5.55c-.45 0-.8.13-1.06.38-.25.26-.37.62-.37 1.09v1.77c0 .47.12.83.37 1.09.26.25.6.38 1.06.38m.16-1.15c-.15 0-.26-.04-.34-.11-.07-.08-.1-.2-.1-.35v-1.49c0-.16.03-.28.1-.36.08-.07.2-.11.34-.11h1.52c.14 0 .25.04.34.11q.12.12.12.36v1.49c0 .15-.04.27-.12.35a.46.46 0 0 1-.34.11H5.7Zm5.62 1.15h1.84c.46 0 .8-.13 1.06-.38.25-.26.37-.62.37-1.1v-1.76c0-.47-.12-.83-.37-1.09a1.4 1.4 0 0 0-1.06-.38h-1.84c-.45 0-.8.13-1.05.38-.25.26-.38.62-.38 1.09v1.77c0 .47.13.83.38 1.09.25.25.6.38 1.05.38m.16-1.15c-.15 0-.26-.04-.34-.11a.52.52 0 0 1-.11-.35v-1.49c0-.16.04-.28.11-.36.08-.07.19-.11.34-.11h1.53c.14 0 .25.04.33.11.08.08.11.2.11.36v1.49c0 .15-.04.27-.11.35-.08.07-.2.11-.33.11zm5.63 1.15h1.84c.45 0 .8-.13 1.06-.38.25-.26.38-.62.38-1.1v-1.76c0-.47-.13-.83-.38-1.09-.25-.25-.6-.38-1.06-.38h-1.84c-.45 0-.8.13-1.05.38-.26.26-.38.62-.38 1.09v1.77c0 .47.12.83.38 1.09.25.25.6.38 1.05.38m.16-1.15c-.15 0-.26-.04-.34-.11-.08-.08-.12-.2-.12-.35v-1.49q0-.24.12-.36c.08-.07.2-.11.34-.11h1.52c.14 0 .25.04.33.11q.12.12.12.36v1.49c0 .15-.04.27-.12.35-.07.07-.18.11-.33.11z",
            fill = 0.664f,
        )
    }

    /** Lucide's own layout grid — four rounded tiles, for switching to tile view. */
    val GridView: ImageVector by lazy {
        lucide(
            "bc_grid_view",
            "M4 3h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z",
            "M15 3h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z",
            "M15 14h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1v-5a1 1 0 0 1 1-1z",
            "M4 14h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-5a1 1 0 0 1 1-1z",
        )
    }

    /** Lucide's own list — three bulleted rows, for switching to list view. */
    val ListView: ImageVector by lazy {
        lucide(
            "bc_list_view",
            "M3 5h.01", "M3 12h.01", "M3 19h.01",
            "M8 5h13", "M8 12h13", "M8 19h13",
        )
    }

    // ---- Settings, sidebar and menu glyphs, at Lucide's own weight ----

    val Moon: ImageVector by lazy {
        lucide("bc_moon",
            "M20.985 12.486a9 9 0 1 1-9.473-9.472c.405-.022.617.46.402.803a6 6 0 0 0 8.268 8.268c.344-.215.825-.004.803.401",
        )
    }

    val FastForward: ImageVector by lazy {
        lucide("bc_fast_forward",
            "M12 6a2 2 0 0 1 3.414-1.414l6 6a2 2 0 0 1 0 2.828l-6 6A2 2 0 0 1 12 18z",
            "M2 6a2 2 0 0 1 3.414-1.414l6 6a2 2 0 0 1 0 2.828l-6 6A2 2 0 0 1 2 18z",
        )
    }

    val Ban: ImageVector by lazy {
        lucide("bc_ban",
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0z",
            "M4.929 4.929 19.07 19.071",
        )
    }

    val Blur: ImageVector by lazy {
        lucide("bc_blur",
            "M12 3v18",
            "M5.5 6a10 10 0 0 1 0 12",
            "M18.5 6a10 10 0 0 0 0 12",
            "M8.5 9a5 5 0 0 1 0 6",
            "M15.5 9a5 5 0 0 0 0 6",
        )
    }

    val Languages: ImageVector by lazy {
        lucide("bc_languages",
            "m5 8 6 6",
            "m4 14 6-6 2-3",
            "M2 5h12",
            "M7 2h1",
            "m22 22-5-10-5 10",
            "M14 18h6",
        )
    }

    val HardDrive: ImageVector by lazy {
        lucide("bc_hard_drive",
            "M10 16h.01",
            "M2.212 11.577a2 2 0 0 0-.212.896V18a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-5.527a2 2 0 0 0-.212-.896L18.55 5.11A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z",
            "M21.946 12.013H2.054",
            "M6 16h.01",
        )
    }

    val Trash: ImageVector by lazy {
        lucide("bc_trash",
            "M3 6h18",
            "M19 6v14a2 2 0 0 1 -2 2H7a2 2 0 0 1 -2 -2V6",
            "M8 6V4a2 2 0 0 1 2 -2h4a2 2 0 0 1 2 2v2",
            "M10 11v6",
            "M14 11v6",
        )
    }

    val Picture: ImageVector by lazy {
        lucide("bc_image",
            "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2z",
            "M7 9a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z",
            "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21",
        )
    }

    val Folder: ImageVector by lazy {
        lucide("bc_folder",
            "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z",
        )
    }

    val Funnel: ImageVector by lazy {
        lucide("bc_funnel",
            "M3 4h18l-7 8v6l-4 2v-8z",
        )
    }

    val EyeOff: ImageVector by lazy {
        lucide("bc_eye_off",
            "M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49",
            "M14.084 14.158a3 3 0 0 1-4.242-4.242",
            "M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143",
            "m2 2 20 20",
        )
    }

    val Monitor: ImageVector by lazy {
        lucide("bc_monitor",
            "M4 3h16a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-10a2 2 0 0 1 2 -2z",
            "M8 21h8",
            "M12 17v4",
        )
    }

    val Brush: ImageVector by lazy {
        lucide("bc_brush",
            "m11 10 3 3",
            "M6.5 21A3.5 3.5 0 1 0 3 17.5a2.62 2.62 0 0 1-.708 1.792A1 1 0 0 0 3 21z",
            "M9.969 17.031 21.378 5.624a1 1 0 0 0-3.002-3.002L6.967 14.031",
        )
    }

    val Wallpaper: ImageVector by lazy {
        lucide("bc_wallpaper",
            "M12 17v4",
            "M8 21h8",
            "m9 17 6.1-6.1a2 2 0 0 1 2.81.01L22 15",
            "M6 9a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z",
            "M4 3h16a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-10a2 2 0 0 1 2 -2z",
        )
    }

    val Sun: ImageVector by lazy {
        lucide("bc_sun",
            "M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0z",
            "M12 2v2",
            "M12 20v2",
            "m4.93 4.93 1.41 1.41",
            "m17.66 17.66 1.41 1.41",
            "M2 12h2",
            "M20 12h2",
            "m6.34 17.66-1.41 1.41",
            "m19.07 4.93-1.41 1.41",
        )
    }

    val Disc: ImageVector by lazy {
        lucide("bc_disc_3",
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0z",
            "M6 12c0-1.7.7-3.2 1.8-4.2",
            "M10 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z",
            "M18 12c0 1.7-.7 3.2-1.8 4.2",
        )
    }

    val Palette: ImageVector by lazy {
        lucide("bc_palette",
            "M12 22a1 1 0 0 1 0-20 10 9 0 0 1 10 9 5 5 0 0 1-5 5h-2.25a1.75 1.75 0 0 0-1.4 2.8l.3.4a1.75 1.75 0 0 1-1.4 2.8z",
            "M13 6.5a0.5 0.5 0 1 0 1 0a0.5 0.5 0 1 0 -1 0z",
            "M17 10.5a0.5 0.5 0 1 0 1 0a0.5 0.5 0 1 0 -1 0z",
            "M6 12.5a0.5 0.5 0 1 0 1 0a0.5 0.5 0 1 0 -1 0z",
            "M8 7.5a0.5 0.5 0 1 0 1 0a0.5 0.5 0 1 0 -1 0z",
        )
    }

    val Film: ImageVector by lazy {
        lucide("bc_film",
            "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2z",
            "M7 3v18",
            "M3 7.5h4",
            "M3 12h18",
            "M3 16.5h4",
            "M17 3v18",
            "M17 7.5h4",
            "M17 16.5h4",
        )
    }

    val VolumeMute: ImageVector by lazy {
        lucide("bc_volume_x",
            "M11 4.702a.7.7 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 .997.413l3.383 3.384A.7.7 0 0 0 11 19.298z",
            "m16.5 14.5 5-5",
            "m16.5 9.5 5 5",
        )
    }

    val BarChart: ImageVector by lazy {
        lucide("bc_chart_column",
            "M3 3v16a2 2 0 0 0 2 2h16",
            "M18 17V9",
            "M13 17V5",
            "M8 17v-3",
        )
    }

    val Pencil: ImageVector by lazy {
        lucide("bc_pencil",
            "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z",
            "m15 5 4 4",
        )
    }

    val Archive: ImageVector by lazy {
        lucide("bc_archive",
            "M3 3h18a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-18a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1z",
            "M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8",
            "M10 12h4",
        )
    }

    val AudioLines: ImageVector by lazy {
        lucide("bc_audio_lines",
            "M2 10v3",
            "M6 6v11",
            "M10 3v18",
            "M14 8v7",
            "M18 5v13",
            "M22 10v3",
        )
    }

    val AudioWaveform: ImageVector by lazy {
        lucide("bc_audio_waveform",
            "M2 13a2 2 0 0 0 2-2V7a2 2 0 0 1 4 0v13a2 2 0 0 0 4 0V4a2 2 0 0 1 4 0v13a2 2 0 0 0 4 0v-4a2 2 0 0 1 2-2",
        )
    }

    val SlidersVertical: ImageVector by lazy {
        lucide("bc_sliders_vertical",
            "M10 8h4",
            "M12 21v-9",
            "M12 8V3",
            "M17 16h4",
            "M19 12V3",
            "M19 21v-5",
            "M3 14h4",
            "M5 10V3",
            "M5 21v-7",
        )
    }

    val Speaker: ImageVector by lazy {
        lucide("bc_speaker",
            "M6 2h12a2 2 0 0 1 2 2v16a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2v-16a2 2 0 0 1 2 -2z",
            "M12 6h.01",
            "M8 14a4 4 0 1 0 8 0a4 4 0 1 0 -8 0z",
            "M12 14h.01",
        )
    }

    val Activity: ImageVector by lazy {
        lucide("bc_activity",
            "M22 12h-2.48a2 2 0 0 0-1.93 1.46l-2.35 8.36a.25.25 0 0 1-.48 0L9.24 2.18a.25.25 0 0 0-.48 0l-2.35 8.36A2 2 0 0 1 4.49 12H2",
        )
    }

    val Puzzle: ImageVector by lazy {
        lucide("bc_puzzle",
            "M15.39 4.39a1 1 0 0 0 1.68-.474 2.5 2.5 0 1 1 3.014 3.015 1 1 0 0 0-.474 1.68l1.683 1.682a2.414 2.414 0 0 1 0 3.414L19.61 15.39a1 1 0 0 1-1.68-.474 2.5 2.5 0 1 0-3.014 3.015 1 1 0 0 1 .474 1.68l-1.683 1.682a2.414 2.414 0 0 1-3.414 0L8.61 19.61a1 1 0 0 0-1.68.474 2.5 2.5 0 1 1-3.014-3.015 1 1 0 0 0 .474-1.68l-1.683-1.682a2.414 2.414 0 0 1 0-3.414L4.39 8.61a1 1 0 0 1 1.68.474 2.5 2.5 0 1 0 3.014-3.015 1 1 0 0 1-.474-1.68l1.683-1.682a2.414 2.414 0 0 1 3.414 0z",
        )
    }

    val CirclePlay: ImageVector by lazy {
        lucide("bc_circle_play",
            "M9 9.003a1 1 0 0 1 1.517-.859l4.997 2.997a1 1 0 0 1 0 1.718l-4.997 2.997A1 1 0 0 1 9 14.996z",
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0z",
        )
    }

    val ChevronUp: ImageVector by lazy {
        lucide("bc_chevron_up",
            "m18 15-6-6-6 6",
        )
    }

    val Calendar: ImageVector by lazy {
        lucide("bc_calendar",
            "M8 2v3",
            "M16 2v3",
            "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2z",
            "M3 9h18",
        )
    }

    val Radio: ImageVector by lazy {
        lucide("bc_radio",
            "M16.247 7.761a6 6 0 0 1 0 8.478",
            "M19.075 4.933a10 10 0 0 1 0 14.134",
            "M4.925 19.067a10 10 0 0 1 0-14.134",
            "M7.753 16.239a6 6 0 0 1 0-8.478",
            "M10 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z",
        )
    }

    /** Apple's own microphone, the mark the web player puts on its Artists row. */
    val Mic: ImageVector by lazy {
        apple(
            "bc_mic",
            "M18.39 9.42c1.289-1.282 1.34-2.908.102-4.139-1.23-1.216-2.85-1.186-4.138.103zm-6.08 9.858a.66.66 0 0 0 .667-.667v-4.328l-.051-1.048 2.234-2.072c.842.11 1.728-.235 2.49-1.004l-4.03-4.035c-.776.761-1.098 1.633-.988 2.475l-6.805 7.332c-.278.3-.322.717.022 1.062L4.913 18.2a.31.31 0 0 0 .037.418l.212.22a.31.31 0 0 0 .425.029l1.208-.945c.33.344.755.3 1.048.022l3.8-3.516v4.182a.66.66 0 0 0 .667.667zm-5.053-2.073-.674-.674 6.453-6.84q.186.307.461.593.274.276.571.476z",
            fill = 0.620f,
        )
    }

    /** Apple's own album box, the mark the web player puts on its Albums row. */
    val Album: ImageVector by lazy {
        apple(
            "bc_album",
            "M15.477 3.937c-.044-.61-.396-.937-1.07-.937H9.431c-.675 0-1.027.327-1.07.937h7.115zm1.24 2.013c-.11-.654-.425-1.025-1.159-1.025H8.222c-.741 0-1.057.37-1.167 1.025zm-.3 14.05c1.313 0 2.083-.756 2.083-2.252v-8.37c0-1.496-.778-2.252-2.304-2.252H7.804Q5.502 7.124 5.5 9.38v8.369C5.5 19.244 6.27 20 7.804 20h8.612zm-.023-1.17H7.818c-.733 0-1.137-.392-1.137-1.148V9.437c0-.756.404-1.14 1.137-1.14h8.356c.727 0 1.145.384 1.145 1.14v8.245c0 .756-.418 1.148-.925 1.148",
            fill = 0.708f,
        )
    }

    /** Apple's own made-for-you card, the mark the web player puts on that shelf. */
    val Person: ImageVector by lazy {
        apple(
            "bc_person",
            "M6.729 20.011H17.27c1.825 0 2.729-.903 2.729-2.694V6.706c0-1.79-.904-2.695-2.729-2.695H6.73C4.913 4.011 4 4.906 4 6.706v10.611c0 1.8.913 2.694 2.729 2.694m5.275-5.101c-3.085 0-5.31 1.486-6.24 3.372-.243-.235-.365-.574-.365-1.034V6.775c0-.904.478-1.365 1.347-1.365h10.508c.86 0 1.347.461 1.347 1.365v10.473c0 .451-.122.8-.356 1.025-.93-1.886-3.086-3.363-6.24-3.363zm0-1.452c1.66.018 2.964-1.399 2.964-3.259 0-1.747-1.304-3.19-2.964-3.19s-2.98 1.443-2.972 3.19c.009 1.86 1.312 3.233 2.972 3.26z",
            fill = 0.667f,
        )
    }

    val PersonCircle: ImageVector by lazy {
        lucide("bc_circle_user",
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0z",
            "M9 10a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z",
            "M7 20.662V19a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v1.662",
        )
    }

    val Ratio: ImageVector by lazy {
        lucide("bc_ratio",
            "M8 2h8a2 2 0 0 1 2 2v16a2 2 0 0 1 -2 2h-8a2 2 0 0 1 -2 -2v-16a2 2 0 0 1 2 -2z",
            "M4 6h16a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-8a2 2 0 0 1 2 -2z",
        )
    }

    val ListVideo: ImageVector by lazy {
        lucide("bc_list_video",
            "M21 5H3",
            "M10 12H3",
            "M10 19H3",
            "M15 12.003a1 1 0 0 1 1.517-.859l4.997 2.997a1 1 0 0 1 0 1.718l-4.997 2.997a1 1 0 0 1-1.517-.86z",
        )
    }

    val Clapperboard: ImageVector by lazy {
        lucide("bc_clapperboard",
            "m12.296 3.464 3.02 3.956",
            "M20.2 6 3 11l-.9-2.4c-.3-1.1.3-2.2 1.3-2.5l13.5-4c1.1-.3 2.2.3 2.5 1.3z",
            "M3 11h18v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
            "m6.18 5.276 3.1 3.899",
        )
    }

    val Sparkles: ImageVector by lazy {
        lucide("bc_sparkles",
            "M11.017 2.814a1 1 0 0 1 1.966 0l1.051 5.558a2 2 0 0 0 1.594 1.594l5.558 1.051a1 1 0 0 1 0 1.966l-5.558 1.051a2 2 0 0 0-1.594 1.594l-1.051 5.558a1 1 0 0 1-1.966 0l-1.051-5.558a2 2 0 0 0-1.594-1.594l-5.558-1.051a1 1 0 0 1 0-1.966l5.558-1.051a2 2 0 0 0 1.594-1.594z",
            "M20 2v4",
            "M22 4h-4",
            "M2 20a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z",
        )
    }


    val Equalizer: ImageVector by lazy {
        lucide("bc_equalizer",
            "M10 5H3",
            "M12 19H3",
            "M14 3v4",
            "M16 17v4",
            "M21 12h-9",
            "M21 19h-5",
            "M21 5h-7",
            "M8 10v4",
            "M8 12H3",
        )
    }

    val Grip: ImageVector by lazy {
        lucide("bc_grip",
            "M8 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0z",
            "M8 5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0z",
            "M8 19a1 1 0 1 0 2 0a1 1 0 1 0 -2 0z",
            "M14 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0z",
            "M14 5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0z",
            "M14 19a1 1 0 1 0 2 0a1 1 0 1 0 -2 0z",
        )
    }

    /**
     * Stop, as the transport draws it: a filled block, not an outline.
     *
     * Nothing else in the row is hollow, and an outline of that size reads as a
     * frame rather than as a control — so the block is set inside the box, at
     * the measure the transport's own marks reach.
     */
    val Stop: ImageVector by lazy {
        glyph("bc_stop", 24f,
            "M8 6h8a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-8a2 2 0 0 1 -2 -2v-8a2 2 0 0 1 2 -2z",
        )
    }

    val Headphones: ImageVector by lazy {
        lucide("bc_headphones",
            "M3 14h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a9 9 0 0 1 18 0v7a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3",
        )
    }

    val Undo: ImageVector by lazy {
        lucide("bc_undo",
            "M9 14 4 9l5-5",
            "M4 9h10.5a5.5 5.5 0 0 1 5.5 5.5a5.5 5.5 0 0 1-5.5 5.5H11",
        )
    }

    val ThumbDown: ImageVector by lazy {
        lucide("bc_thumbdown",
            "M9 18.12 10 14H4.17a2 2 0 0 1-1.92-2.56l2.33-8A2 2 0 0 1 6.5 2H20a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-2.76a2 2 0 0 0-1.79 1.11L12 22a3.13 3.13 0 0 1-3-3.88Z",
            "M17 14V2",
        )
    }


    val Lock: ImageVector by lazy {
        lucide("bc_lock",
            "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-7a2 2 0 0 1 2 -2z",
            "M7 11V7a5 5 0 0 1 10 0v4",
        )
    }

    val Link: ImageVector by lazy {
        lucide("bc_link",
            "M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71",
            "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71",
        )
    }

    val Globe: ImageVector by lazy {
        lucide("bc_globe",
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0z",
            "M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20",
            "M2 12h20",
        )
    }


    val Copy: ImageVector by lazy {
        lucide("bc_copy",
            "M10 8h10a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-10a2 2 0 0 1 2 -2z",
            "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2",
        )
    }

    val LockOpen: ImageVector by lazy {
        lucide("bc_lock-open",
            "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-7a2 2 0 0 1 2 -2z",
            "M7 11V7a5 5 0 0 1 9.9-1",
        )
    }

}
