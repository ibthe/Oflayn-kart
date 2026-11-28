package app.georgia.offline.data

import java.io.InputStream
import java.time.Instant
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double? = null,
    val timeEpochMillis: Long? = null
)

data class GpxTrack(
    val name: String,
    val segments: List<List<TrackPoint>>,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val description: String? = null
) {
    val pointCount: Int get() = segments.sumOf { it.size }
}

/** Secure, offline GPX 1.1 reader/writer for track and route geometry. */
object GpxCodec {
    fun parse(input: InputStream, fallbackName: String = "Импортированный трек"): GpxTrack {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val document = factory.newDocumentBuilder().parse(input)
        val root = document.documentElement
        require(root.localName == "gpx" || root.nodeName == "gpx") { "Файл не является GPX." }

        val name = firstText(root, "name")?.take(100)?.ifBlank { null } ?: fallbackName
        val segments = mutableListOf<List<TrackPoint>>()
        val trackSegments = root.getElementsByTagNameNS("*", "trkseg")
        if (trackSegments.length > 0) {
            for (i in 0 until trackSegments.length) {
                val element = trackSegments.item(i) as? Element ?: continue
                val points = parsePoints(element, "trkpt")
                if (points.isNotEmpty()) segments += points
            }
        } else {
            val trackPoints = parsePoints(root, "trkpt")
            if (trackPoints.isNotEmpty()) segments += trackPoints
            if (segments.isEmpty()) {
                val routePoints = parsePoints(root, "rtept")
                if (routePoints.isNotEmpty()) segments += routePoints
            }
        }
        val validSegments = segments.filter { it.isNotEmpty() }
        require(validSegments.sumOf { it.size } >= 2) { "В GPX должно быть не менее двух корректных точек." }
        val timestamps = validSegments.flatten().mapNotNull { it.timeEpochMillis }
        return GpxTrack(
            name,
            validSegments,
            timestamps.minOrNull() ?: System.currentTimeMillis(),
            firstText(root, "desc")?.take(1_000)
        )
    }

    fun write(track: GpxTrack): String {
        require(track.pointCount >= 2) { "Для экспорта нужно не менее двух точек." }
        val output = StringBuilder()
        output.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        output.append("""<gpx version="1.1" creator="Oflayn kart" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
        output.append("  <metadata><name>").append(escape(track.name)).append("</name>")
        track.description?.takeIf { it.isNotBlank() }?.let {
            output.append("<desc>").append(escape(it.take(1_000))).append("</desc>")
        }
        output.append("<time>").append(Instant.ofEpochMilli(track.createdAtEpochMillis)).append("</time></metadata>\n")
        output.append("  <trk><name>").append(escape(track.name)).append("</name>\n")
        track.segments.filter { it.isNotEmpty() }.forEach { segment ->
            output.append("    <trkseg>\n")
            segment.forEach { point ->
                output.append("      <trkpt lat=\"").append(point.latitude).append("\" lon=\"").append(point.longitude).append("\">")
                point.elevationMeters?.let { output.append("<ele>").append(it).append("</ele>") }
                point.timeEpochMillis?.let { output.append("<time>").append(Instant.ofEpochMilli(it)).append("</time>") }
                output.append("</trkpt>\n")
            }
            output.append("    </trkseg>\n")
        }
        output.append("  </trk>\n</gpx>\n")
        return output.toString()
    }

    private fun parsePoints(parent: Element, tag: String): List<TrackPoint> {
        val nodes = parent.getElementsByTagNameNS("*", tag)
        return (0 until nodes.length).mapNotNull { index ->
            val element = nodes.item(index) as? Element ?: return@mapNotNull null
            val lat = element.getAttribute("lat").toDoubleOrNull() ?: return@mapNotNull null
            val lon = element.getAttribute("lon").toDoubleOrNull() ?: return@mapNotNull null
            if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                return@mapNotNull null
            }
            val elevation = firstText(element, "ele")?.toDoubleOrNull()?.takeIf { it.isFinite() }
            val time = firstText(element, "time")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            TrackPoint(lat, lon, elevation, time)
        }
    }

    private fun firstText(parent: Element, tag: String): String? =
        parent.getElementsByTagNameNS("*", tag).item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
