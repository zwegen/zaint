package de.zwegen.zpaint.tools.implementation

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import org.json.JSONObject

private const val ICONIFY_API_BASE_URL = "https://api.iconify.design"
private const val ICONIFY_TIMEOUT_MS = 12000
private const val LOCAL_SIMPLE_STAR_ID = "local:simple-star"
private const val LOCAL_SIMPLE_STAR_SVG =
    """<svg xmlns="http://www.w3.org/2000/svg" width="1em" height="1em" viewBox="0 0 24 24"><path fill="#000000" d="M12 2.5l2.9 6.1 6.6.9-4.8 4.7 1.2 6.6-5.9-3.1-5.9 3.1 1.2-6.6-4.8-4.7 6.6-.9z"/></svg>"""

class IconifyClient {
    fun search(query: String, limit: Int): List<String> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val response = readUrl("$ICONIFY_API_BASE_URL/search?query=$encodedQuery&limit=$limit")
        val icons = JSONObject(response).optJSONArray("icons") ?: return emptyList()
        val results = (0 until icons.length()).mapNotNull { icons.optString(it).takeIf(String::isNotBlank) }
        return if (query.trim().lowercase(Locale.getDefault()) in setOf("star", "stern")) {
            listOf(LOCAL_SIMPLE_STAR_ID) + results.filterNot { it == LOCAL_SIMPLE_STAR_ID }
        } else {
            results
        }
    }

    fun loadSvg(iconId: String): String {
        if (iconId == LOCAL_SIMPLE_STAR_ID) {
            return LOCAL_SIMPLE_STAR_SVG
        }
        val parts = iconId.split(':', limit = 2)
        require(parts.size == 2) { "Invalid icon id: $iconId" }
        val prefix = URLEncoder.encode(parts[0], "UTF-8")
        val name = URLEncoder.encode(parts[1], "UTF-8")
        return readUrl("$ICONIFY_API_BASE_URL/$prefix/$name.svg?color=%23000000")
    }

    private fun readUrl(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = ICONIFY_TIMEOUT_MS
        connection.readTimeout = ICONIFY_TIMEOUT_MS
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json,image/svg+xml,text/plain")
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("Iconify request failed: HTTP $code")
            }
            BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                return reader.readText()
            }
        } finally {
            connection.disconnect()
        }
    }
}
