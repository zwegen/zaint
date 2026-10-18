package de.zwegen.zpaint.test.tools.implementation

import android.graphics.Color
import com.caverock.androidsvg.SVG
import de.zwegen.zpaint.tools.implementation.IconifyClient
import de.zwegen.zpaint.tools.implementation.SvgBitmapRenderer
import java.net.URI
import java.net.URLDecoder
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconifyClientTest {
    @Test
    fun groupsCollectionsSortsRequestsAndKeepsResultOrder() {
        val requests = mutableListOf<String>()
        val client = client(requests)
        val ids = listOf("mdi:z", "bi:a", "mdi:a", "mdi:z", "local:simple-star")
        val result = client.loadSvgs(ids)
        assertEquals(ids.distinct(), result.keys.toList())
        assertTrue(result.values.all { it.isSuccess })
        assertEquals(listOf(
            "https://api.iconify.design/mdi.json?icons=a,z",
            "https://api.iconify.design/bi.json?icons=a"
        ), requests)
        client.loadSvgs(ids.reversed())
        assertEquals(result.getValue("mdi:z").getOrThrow(), client.loadSvg("mdi:z"))
        assertEquals(result.getValue("bi:a").getOrThrow(), client.loadSvg("bi:a"))
        assertEquals(2, requests.size)
    }

    @Test
    fun laterPageRequestsOnlyUncachedNamesAndKeepsCollectionsSeparate() {
        val requests = mutableListOf<String>()
        val client = client(requests)
        client.loadSvgs(listOf("mdi:a", "bi:a"))
        client.loadSvgs(listOf("mdi:a", "mdi:b", "bi:a", "bi:b"))
        assertEquals(listOf("a", "a", "b", "b"), requests.map { names(it).single() })
        assertEquals(listOf("/mdi.json", "/bi.json", "/mdi.json", "/bi.json"), requests.map { URI(it).path })
    }

    @Test
    fun directSvgAndLocalStarReuseCacheWithoutBatchRequests() {
        val requests = mutableListOf<String>()
        val client = IconifyClient { url -> requests.add(url); "<svg/>" }
        assertEquals("<svg/>", client.loadSvg("mdi:a"))
        assertEquals("<svg/>", client.loadSvgs(listOf("mdi:a")).getValue("mdi:a").getOrThrow())
        val star = client.loadSvg("local:simple-star")
        assertEquals(star, client.loadSvgs(listOf("local:simple-star")).getValue("local:simple-star").getOrThrow())
        assertTrue(client.loadSvgs(emptyList()).isEmpty())
        assertEquals(listOf("https://api.iconify.design/mdi/a.svg?color=%23000000"), requests)
    }

    @Test
    fun splitsEncodedUrlsAt500CharactersWithoutDroppingOrRepeatingIcons() {
        val requests = mutableListOf<String>()
        val ids = (0 until 60).map { "mdi:some-long-icon-name-$it" }
        val result = client(requests).loadSvgs(ids)
        assertEquals(ids, result.keys.toList())
        assertTrue(result.values.all { it.isSuccess })
        assertTrue(requests.size in 2..5)
        assertTrue(requests.all { it.length <= 500 })
        assertEquals(ids.map { it.substringAfter(':') }.sorted(), requests.flatMap(::names))
    }

    @Test
    fun handlesEncodedNamesAndRejectsOversizedSingleNamesWithoutNetwork() {
        val requests = mutableListOf<String>()
        val client = client(requests)
        val result = client.loadSvgs(listOf("mdi:a b", "mdi:${"x".repeat(500)}", "invalid", "mdi:c"))
        assertTrue(result.getValue("mdi:a b").isSuccess)
        assertTrue(result.getValue("mdi:c").isSuccess)
        assertTrue(result.getValue("invalid").isFailure)
        assertTrue(result.getValue("mdi:${"x".repeat(500)}").isFailure)
        assertEquals(1, requests.size)
        assertTrue(requests.single().contains("a+b,c"))
    }

    @Test
    fun failedCollectionDoesNotHideOthersOrTriggerIndividualSvgFallback() {
        val requests = mutableListOf<String>()
        val client = IconifyClient { url ->
            requests.add(url)
            if (url.contains("/bad.json")) error("Iconify request failed: HTTP 503")
            response(url)
        }
        val result = client.loadSvgs(listOf("bad:a", "good:a", "bad:b", "local:simple-star"))
        assertTrue(result.getValue("bad:a").isFailure)
        assertTrue(result.getValue("bad:b").isFailure)
        assertTrue(result.getValue("good:a").isSuccess)
        assertTrue(result.getValue("local:simple-star").isSuccess)
        assertEquals(2, requests.size)
        assertTrue(requests.all { URI(it).path.endsWith(".json") })
        client.loadSvgs(listOf("bad:a", "good:a"))
        assertEquals(3, requests.size)
    }

    @Test
    fun missingBrokenAndCyclicAliasesAreIsolatedAndNotCached() {
        var calls = 0
        val client = IconifyClient {
            calls++
            """{"prefix":"test","icons":{"ok":{"body":"<path/>"}},"not_found":["missing"],
                "aliases":{"broken":{"parent":"absent"},"cycle":{"parent":"cycle"}}}"""
        }
        val ids = listOf("test:ok", "test:missing", "test:broken", "test:cycle")
        val result = client.loadSvgs(ids)
        assertTrue(result.getValue("test:ok").isSuccess)
        assertTrue(ids.drop(1).all { result.getValue(it).isFailure })
        client.loadSvgs(ids)
        assertEquals(2, calls)
    }

    @Test
    fun malformedResponseAndWrongPrefixFailWithoutSvgFallback() {
        for (response in listOf("not JSON", """{"prefix":"other","icons":{}}""")) {
            var calls = 0
            val result = IconifyClient { calls++; response }.loadSvgs(listOf("test:a", "test:b"))
            assertTrue(result.values.all { it.isFailure })
            assertEquals(1, calls)
        }
    }

    @Test
    fun searchPreservesOrderingDuplicatesAndLimitAndPrependsOnlyOneLocalStar() {
        val requests = mutableListOf<String>()
        val client = IconifyClient { url ->
            requests.add(url)
            """{"icons":["mdi:b","local:simple-star","mdi:a","mdi:b"]}"""
        }
        assertEquals(listOf("mdi:b", "local:simple-star", "mdi:a", "mdi:b"), client.search("shape", 49))
        assertEquals(listOf("local:simple-star", "mdi:b", "mdi:a", "mdi:b"), client.search("stern", 97))
        assertTrue(requests[0].endsWith("query=shape&limit=49"))
        assertTrue(requests[1].endsWith("query=stern&limit=97"))
    }

    @Test
    fun collectionDefaultsAliasDimensionOverridesAndAliasCacheArePreserved() {
        var calls = 0
        val client = IconifyClient {
            calls++
            """{"prefix":"test","left":2,"top":3,"width":20,"height":10,
                "icons":{"base":{"body":"<path fill='currentColor' d='M2 3h5v5H2z'/>"}},
                "aliases":{"first":{"parent":"base","width":30},"second":{"parent":"first","top":5}}}"""
        }
        val result = client.loadSvgs(listOf("test:base", "test:second"))
        val baseBox = SVG.getFromString(result.getValue("test:base").getOrThrow()).documentViewBox!!
        val aliasSvg = result.getValue("test:second").getOrThrow()
        val aliasBox = SVG.getFromString(aliasSvg).documentViewBox!!
        assertEquals(20f, baseBox.width())
        assertEquals(10f, baseBox.height())
        assertEquals(2f, aliasBox.left)
        assertEquals(5f, aliasBox.top)
        assertEquals(30f, aliasBox.width())
        assertEquals(10f, aliasBox.height())
        assertFalse(aliasSvg.contains("currentColor"))
        assertEquals(aliasSvg, client.loadSvg("test:second"))
        assertEquals(1, calls)
    }

    @Test
    fun defaultDimensionsBlackCurrentColorAndExplicitColorsRender() {
        val svg = IconifyClient {
            """{"prefix":"test","icons":{"a":{"body":"<path fill='currentColor' d='M0 0h8v16H0z'/><path fill='#ff0000' d='M8 0h8v16H8z'/>"}}}"""
        }.loadSvgs(listOf("test:a")).getValue("test:a").getOrThrow()
        assertEquals(16f, SVG.getFromString(svg).documentViewBox!!.width())
        val bitmap = SvgBitmapRenderer().render(svg, 96)
        assertEquals(Color.BLACK, bitmap.getPixel(24, 48))
        assertEquals(Color.RED, bitmap.getPixel(72, 48))
    }

    @Test
    fun chainedFlipsCancelAndRotationsComposeBeforeRenderingRectangularIcon() {
        val client = IconifyClient {
            """{"prefix":"test","width":20,"height":10,
                "icons":{"base":{"hFlip":true,"rotate":1,"body":"<path fill='currentColor' d='M0 0h10v5H0z'/>"}},
                "aliases":{"first":{"parent":"base","hFlip":true,"rotate":1},
                "second":{"parent":"first","rotate":-1}}}"""
        }
        val svg = client.loadSvgs(listOf("test:second")).getValue("test:second").getOrThrow()
        val box = SVG.getFromString(svg).documentViewBox!!
        assertEquals(10f, box.width())
        assertEquals(20f, box.height())
        val bitmap = SvgBitmapRenderer().render(svg, 100)
        // The original top-left quadrant becomes top-right after a clockwise quarter turn.
        assertEquals(Color.BLACK, bitmap.getPixel(60, 25))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(40, 25))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(60, 75))
    }

    @Test
    fun flipsAndAllQuarterTurnsRenderCorrectQuadrantsWithNonzeroOrigin() {
        for (hFlip in listOf(false, true)) {
            for (vFlip in listOf(false, true)) {
                for (rotation in -1..3) {
                    val svg = IconifyClient {
                        """{"prefix":"test","width":20,"height":10,"left":2,"top":2,
                            "icons":{"a":{"hFlip":$hFlip,"vFlip":$vFlip,"rotate":$rotation,
                            "body":"<path fill='currentColor' d='M2 2h10v5H2z'/>"}}}"""
                    }.loadSvgs(listOf("test:a")).getValue("test:a").getOrThrow()
                    val bitmap = SvgBitmapRenderer().render(svg, 100)
                    var x = if (hFlip) 0.75 else 0.25
                    var y = if (vFlip) 0.75 else 0.25
                    repeat((rotation + 4) % 4) {
                        val oldX = x
                        x = 1 - y
                        y = oldX
                    }
                    val box = SVG.getFromString(svg).documentViewBox!!
                    val scale = 100 / maxOf(box.width(), box.height())
                    val pixelX = ((100 - box.width() * scale) / 2 + x * box.width() * scale).toInt()
                    val pixelY = ((100 - box.height() * scale) / 2 + y * box.height() * scale).toInt()
                    assertEquals("h=$hFlip v=$vFlip rotate=$rotation", Color.BLACK, bitmap.getPixel(pixelX, pixelY))
                    assertEquals(Color.TRANSPARENT, bitmap.getPixel(99 - pixelX, 99 - pixelY))
                }
            }
        }
    }

    @Test
    fun rootTransformDefaultsComposeWithIconAndAliasTransformations() {
        val svg = IconifyClient {
            """{"prefix":"test","rotate":1,"hFlip":true,"icons":{
                "a":{"rotate":2,"body":"<path fill='currentColor' d='M0 0h8v8H0z'/>"}},
                "aliases":{"b":{"parent":"a","rotate":1,"hFlip":true}}}"""
        }.loadSvgs(listOf("test:b")).getValue("test:b").getOrThrow()
        val bitmap = SvgBitmapRenderer().render(svg, 96)
        assertEquals(Color.BLACK, bitmap.getPixel(24, 24))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(72, 72))
    }

    @Test
    fun transformedXlinkReferencesDeclareNamespaceAndRender() {
        val svg = IconifyClient {
            """{"prefix":"test","icons":{"a":{"hFlip":true,
                "body":"<defs><path id='shape' fill='#ff0000' d='M0 0h8v8H0z'/></defs><use xlink:href='#shape'/>"}}}"""
        }.loadSvgs(listOf("test:a")).getValue("test:a").getOrThrow()
        assertTrue(svg.contains("xmlns:xlink=\"http://www.w3.org/1999/xlink\""))
        val bitmap = SvgBitmapRenderer().render(svg, 96)
        assertEquals(Color.RED, bitmap.getPixel(72, 24))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(24, 24))
    }

    @Test
    fun transformedIconsRetainDefinitionsOutsideTheTransformGroup() {
        val svg = IconifyClient {
            """{"prefix":"test","icons":{"a":{"hFlip":true,
                "body":"<defs><linearGradient id='g'><stop stop-color='#ff0000'/></linearGradient></defs><path fill='url(#g)' d='M0 0h8v8H0z'/>"}}}"""
        }.loadSvgs(listOf("test:a")).getValue("test:a").getOrThrow()
        assertTrue(svg.indexOf("</defs>") < svg.indexOf("<g transform="))
        val bitmap = SvgBitmapRenderer().render(svg, 96)
        assertEquals(Color.RED, bitmap.getPixel(72, 24))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(24, 24))
    }

    private fun client(requests: MutableList<String>) = IconifyClient { url ->
        requests.add(url)
        response(url)
    }

    private fun names(url: String): List<String> = URI(url).rawQuery.substringAfter("icons=").split(',')
        .map { URLDecoder.decode(it, "UTF-8") }

    private fun response(url: String): String {
        val icons = JSONObject()
        names(url).forEach { icons.put(it, JSONObject().put("body", "<path fill=\"currentColor\"/>")) }
        return JSONObject().put("prefix", URI(url).path.removePrefix("/").removeSuffix(".json"))
            .put("icons", icons).toString()
    }
}
