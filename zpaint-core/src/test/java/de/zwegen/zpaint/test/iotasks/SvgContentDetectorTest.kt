package de.zwegen.zpaint.iotasks

import java.io.ByteArrayInputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SvgContentDetectorTest {
    @Test
    fun identifiesInkscapeSvgWithXmlDeclarationAndMetadata() {
        val inkscapeSvg = """
            <?xml version="1.0" encoding="UTF-8" standalone="no"?>
            <svg width="1024" height="1024" viewBox="0 0 1024 1024" version="1.1"
                inkscape:version="1.4.4" xmlns:inkscape="http://www.inkscape.org/namespaces/inkscape"
                xmlns="http://www.w3.org/2000/svg">
                <sodipodi:namedview inkscape:current-layer="layer1" />
                <path d="M 0,0 H 10 V 10 Z" />
            </svg>
        """.trimIndent()

        assertTrue(SvgContentDetector.isSvg(ByteArrayInputStream(inkscapeSvg.toByteArray())))
    }

    @Test
    fun identifiesUtf16Svg() {
        val svg = "<?xml version=\"1.0\"?><svg xmlns=\"http://www.w3.org/2000/svg\" />"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + svg.toByteArray(Charsets.UTF_16LE)

        assertTrue(SvgContentDetector.isSvg(bytes))
    }

    @Test
    fun doesNotIdentifyNonSvgContent() {
        assertFalse(SvgContentDetector.isSvg("{\"type\":\"image\"}".toByteArray()))
    }
}
