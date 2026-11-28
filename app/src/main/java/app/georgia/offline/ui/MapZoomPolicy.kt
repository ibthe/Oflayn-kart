package app.georgia.offline.ui

/** Wait long enough for the route to settle before applying the closer overview. */
internal const val ROUTE_AUTO_ZOOM_DELAY_MS = 7_000L

/** Lower zoom values show a wider area; zoom backs out as riding speed rises. */
internal fun speedBasedZoomLevel(speedKmh: Float): Byte = when {
    speedKmh >= 90f -> 11
    speedKmh >= 60f -> 12
    speedKmh >= 35f -> 13
    speedKmh >= 15f -> 14
    else -> 16
}
