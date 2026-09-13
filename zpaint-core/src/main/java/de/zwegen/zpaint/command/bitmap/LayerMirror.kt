package de.zwegen.zpaint.command.bitmap

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import de.zwegen.zpaint.contract.ZaintLayerContracts

/** Matrix values for mirroring a document-sized bitmap without changing its dimensions. */
data class MirrorPlan(
    val scaleX: Float,
    val scaleY: Float,
    val translationX: Float,
    val translationY: Float
) {
    fun toMatrix(): Matrix = Matrix().apply {
        setScale(scaleX, scaleY)
        postTranslate(translationX, translationY)
    }
}

object MirrorPlans {
    fun horizontalLine(documentHeight: Int): MirrorPlan = MirrorPlan(1f, -1f, 0f, documentHeight.toFloat())

    fun verticalLine(documentWidth: Int): MirrorPlan = MirrorPlan(-1f, 1f, documentWidth.toFloat(), 0f)
}

interface LayerMirror {
    fun mirror(layer: ZaintLayerContracts.ZaintLayer, plan: MirrorPlan)
}

/** Android bitmap adapter for in-place layer mirroring. */
class AndroidLayerMirror : LayerMirror {
    override fun mirror(layer: ZaintLayerContracts.ZaintLayer, plan: MirrorPlan) {
        val bitmap = layer.bitmap
        val copy = bitmap.copy(bitmap.config, bitmap.isMutable)
        try {
            bitmap.eraseColor(Color.TRANSPARENT)
            Canvas(bitmap).drawBitmap(copy, plan.toMatrix(), Paint())
        } finally {
            if (!copy.isRecycled) copy.recycle()
        }
    }
}
