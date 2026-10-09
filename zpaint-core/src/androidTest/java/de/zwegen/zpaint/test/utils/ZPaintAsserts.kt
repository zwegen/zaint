package de.zwegen.zpaint.test.utils

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import org.junit.Assert

object ZPaintAsserts {
    @JvmStatic
    fun assertPaintEquals(
        expectedPaint: Paint,
        actualPaint: Paint
    ) { assertPaintEquals(null, expectedPaint, actualPaint) }

    @JvmStatic
    fun assertPaintEquals(message: String?, expectedPaint: Paint, actualPaint: Paint) {
        Assert.assertEquals(message, expectedPaint.color.toLong(), actualPaint.color.toLong())
        Assert.assertEquals(message, expectedPaint.strokeCap, actualPaint.strokeCap)
        Assert.assertEquals(message, expectedPaint.strokeWidth, actualPaint.strokeWidth, Float.MIN_VALUE)
    }

    @JvmStatic
    fun assertPathEquals(expectedPath: Path, actualPath: Path) { assertPathEquals(null, expectedPath, actualPath) }

    @JvmStatic
    fun assertPathEquals(message: String?, expectedPath: Path, actualPath: Path) {
        expectedPath.close()
        actualPath.close()
        val expectedPathBounds = RectF()
        val actualPathBounds = RectF()
        expectedPath.computeBounds(expectedPathBounds, true)
        actualPath.computeBounds(actualPathBounds, true)
        Assert.assertEquals(message, expectedPathBounds.bottom, actualPathBounds.bottom, Float.MIN_VALUE)
        Assert.assertEquals(message, expectedPathBounds.top, actualPathBounds.top, Float.MIN_VALUE)
        Assert.assertEquals(message, expectedPathBounds.left, actualPathBounds.left, Float.MIN_VALUE)
        Assert.assertEquals(message, expectedPathBounds.right, actualPathBounds.right, Float.MIN_VALUE)
    }

   @JvmStatic
    fun assertBitmapEquals(
        expectedBitmap: Bitmap,
        actualBitmap: Bitmap?
    ) { assertBitmapEquals(null, expectedBitmap, actualBitmap) }

    @JvmStatic
    fun assertBitmapEquals(
        message: String?,
        expectedBitmap: Bitmap,
        actualBitmap: Bitmap?
    ) { Assert.assertTrue(message, expectedBitmap.sameAs(actualBitmap)) }
}
