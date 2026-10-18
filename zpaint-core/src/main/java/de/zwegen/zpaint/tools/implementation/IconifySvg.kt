package de.zwegen.zpaint.tools.implementation

import org.json.JSONObject

/** Resolve IconifyJSON defaults and alias chains before applying Iconify's flip/rotation order. */
internal fun iconifySvg(collection: JSONObject, name: String): String {
    val icons = collection.getJSONObject("icons")
    val aliases = collection.optJSONObject("aliases")
    val chain = mutableListOf<JSONObject>()
    val visited = mutableSetOf<String>()
    var parent = name
    while (true) {
        require(visited.add(parent)) { "Cyclic Iconify alias: $name" }
        val icon = icons.optJSONObject(parent)
        if (icon != null) {
            chain.add(icon)
            break
        }
        val alias = aliases?.optJSONObject(parent)
            ?: throw IllegalStateException("Iconify icon not found: $name")
        chain.add(alias)
        parent = alias.getString("parent")
    }
    val body = chain.last().getString("body")
    val properties = JSONObject()
    // Collection defaults apply once; aliases override dimensions and compose transformations.
    for (item in listOf(collection) + chain.asReversed()) {
        for (key in listOf("left", "top", "width", "height")) {
            if (item.has(key)) properties.put(key, item.getDouble(key))
        }
        properties.put("rotate", properties.optInt("rotate") + item.optInt("rotate"))
        properties.put("hFlip", properties.optBoolean("hFlip") xor item.optBoolean("hFlip"))
        properties.put("vFlip", properties.optBoolean("vFlip") xor item.optBoolean("vFlip"))
    }
    var left = properties.optDouble("left", 0.0)
    var top = properties.optDouble("top", 0.0)
    var width = properties.optDouble("width", 16.0)
    var height = properties.optDouble("height", 16.0)
    require(listOf(left, top, width, height).all { it.isFinite() } && width > 0 && height > 0) {
        "Invalid Iconify dimensions: $name"
    }
    var rotation = properties.optInt("rotate")
    val hFlip = properties.optBoolean("hFlip")
    val vFlip = properties.optBoolean("vFlip")
    val transforms = mutableListOf<String>()
    if (hFlip && vFlip) {
        rotation += 2
    } else if (hFlip) {
        transforms.add("translate(${width + left} ${-top}) scale(-1 1)")
        left = 0.0
        top = 0.0
    } else if (vFlip) {
        transforms.add("translate(${-left} ${height + top}) scale(1 -1)")
        left = 0.0
        top = 0.0
    }
    rotation = ((rotation % 4) + 4) % 4
    when (rotation) {
        1 -> {
            val pivot = height / 2 + top
            transforms.add(0, "rotate(90 $pivot $pivot)")
        }
        2 -> transforms.add(0, "rotate(180 ${width / 2 + left} ${height / 2 + top})")
        3 -> {
            val pivot = width / 2 + left
            transforms.add(0, "rotate(-90 $pivot $pivot)")
        }
    }
    if (rotation % 2 == 1) {
        val oldLeft = left
        left = top
        top = oldLeft
        val oldWidth = width
        width = height
        height = oldWidth
    }
    val content = if (transforms.isEmpty()) body else wrapIconifyBody(body, transforms.joinToString(" "))
    // Match the existing SVG endpoint's black currentColor, preserving explicitly colored artwork.
    val xlinkNamespace = if ("xlink:" in content) " xmlns:xlink=\"http://www.w3.org/1999/xlink\"" else ""
    return """<svg xmlns="http://www.w3.org/2000/svg"$xlinkNamespace width="$width" height="$height" viewBox="$left $top $width $height">${content.replace("currentColor", "#000000")}</svg>"""
}

// Keep definitions outside the transformed group, matching Iconify's SVG builder.
private fun wrapIconifyBody(body: String, transform: String): String {
    val definitions = StringBuilder()
    val content = Regex("<defs\\b[^>]*>([\\s\\S]*?)</defs\\s*>").replace(body) {
        definitions.append(it.groupValues[1])
        ""
    }
    val defs = if (definitions.isEmpty()) "" else "<defs>$definitions</defs>"
    return "$defs<g transform=\"$transform\">$content</g>"
}
