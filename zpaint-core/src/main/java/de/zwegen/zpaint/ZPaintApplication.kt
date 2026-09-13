package de.zwegen.zpaint

import java.io.File
import java.lang.IllegalArgumentException

@SuppressWarnings("ThrowingExceptionsWithoutMessageOrCause")
class ZPaintApplication private constructor() {
    companion object {
        @JvmStatic
        var cacheDir: File? = null
    }

    init {
        throw IllegalArgumentException()
    }
}
