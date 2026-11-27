package app.georgia.offline

import app.georgia.offline.data.GpxCodec
import app.georgia.offline.data.GpxTrack
import app.georgia.offline.data.TrackPoint
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class GpxCodecTest {
    @Test
    fun readsTrackSegmentsNamesElevationAndTimestamps() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
              <metadata><name>Mountain &amp; valley</name></metadata>
              <trk><name>Test route</name><trkseg>
                <trkpt lat="41.7" lon="44.8"><ele>1250.5</ele><time>2026-01-01T10:00:00Z</time></trkpt>
                <trkpt lat="41.8" lon="44.9"><ele>1300</ele><time>2026-01-01T10:01:00Z</time></trkpt>
              </trkseg><trkseg>
                <trkpt lat="41.9" lon="45.0"/>
              </trkseg></trk>
            </gpx>
        """.trimIndent()

        val track = GpxCodec.parse(ByteArrayInputStream(xml.toByteArray()))
        assertEquals("Mountain & valley", track.name)
        assertEquals(2, track.segments.size)
        assertEquals(3, track.pointCount)
        assertEquals(1250.5, track.segments[0][0].elevationMeters!!, 0.0)
        assertNotNull(track.segments[0][0].timeEpochMillis)
    }

    @Test
    fun ignoresInvalidCoordinatesAndRejectsTracksWithTooFewPoints() {
        val xml = """
            <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
              <trk><trkseg>
                <trkpt lat="91" lon="44"/>
                <trkpt lat="41.7" lon="44.8"/>
              </trkseg></trk>
            </gpx>
        """.trimIndent()
        assertThrows(IllegalArgumentException::class.java) {
            GpxCodec.parse(ByteArrayInputStream(xml.toByteArray()))
        }
    }

    @Test
    fun roundTripsEscapedNamesAndPointMetadata() {
        val source = GpxTrack(
            name = "Казбеги & ущелье <высоко>",
            segments = listOf(listOf(
                TrackPoint(42.0, 44.0, 1800.0, 1_767_264_000_000L),
                TrackPoint(42.1, 44.1)
            )),
            createdAtEpochMillis = 1_767_264_000_000L,
            description = "Смотровая площадка & старый маршрут"
        )
        val encoded = GpxCodec.write(source)
        val decoded = GpxCodec.parse(ByteArrayInputStream(encoded.toByteArray()))
        assertEquals(source.name, decoded.name)
        assertEquals(source.description, decoded.description)
        assertEquals(source.pointCount, decoded.pointCount)
        assertEquals(1800.0, decoded.segments[0][0].elevationMeters!!, 0.0)
        assertEquals(source.segments[0][0].timeEpochMillis, decoded.segments[0][0].timeEpochMillis)
    }

    @Test
    fun rejectsDoctypeAndExternalEntities() {
        val xml = """
            <!DOCTYPE gpx [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <gpx version="1.1" creator="test"><metadata><name>&xxe;</name></metadata></gpx>
        """.trimIndent()
        assertThrows(Exception::class.java) {
            GpxCodec.parse(ByteArrayInputStream(xml.toByteArray()))
        }
    }
}
