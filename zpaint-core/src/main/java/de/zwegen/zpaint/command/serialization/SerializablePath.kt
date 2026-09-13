package de.zwegen.zpaint.command.serialization

import android.graphics.Path

/**
 * Mutable stroke path with an explicit operation log.
 *
 * Commands receive a copy of this object, so a live brush preview can never mutate a stroke that
 * is already in undo history. Project saving uses layer snapshots and therefore deliberately does
 * not serialize individual path operations.
 */
open class ZaintStrokePath() : Path() {
    private val operations = mutableListOf<Operation>()

    constructor(source: ZaintStrokePath) : this() {
        source.operations.forEach(::applyOperation)
    }

    override fun moveTo(x: Float, y: Float) = record(Operation.Move(x, y))
    override fun lineTo(x: Float, y: Float) = record(Operation.Line(x, y))
    override fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) =
        record(Operation.Quad(x1, y1, x2, y2))
    override fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
        record(Operation.Cubic(x1, y1, x2, y2, x3, y3))

    override fun rewind() {
        operations.clear()
        super.rewind()
    }

    private fun record(operation: Operation) {
        operations += operation
        applyToPath(operation)
    }

    private fun applyOperation(operation: Operation) {
        operations += operation
        applyToPath(operation)
    }

    private fun applyToPath(operation: Operation) = when (operation) {
        is Operation.Move -> super.moveTo(operation.x, operation.y)
        is Operation.Line -> super.lineTo(operation.x, operation.y)
        is Operation.Quad -> super.quadTo(operation.x1, operation.y1, operation.x2, operation.y2)
        is Operation.Cubic -> super.cubicTo(operation.x1, operation.y1, operation.x2, operation.y2, operation.x3, operation.y3)
    }

    private sealed interface Operation {
        data class Move(val x: Float, val y: Float) : Operation
        data class Line(val x: Float, val y: Float) : Operation
        data class Quad(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : Operation
        data class Cubic(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val x3: Float, val y3: Float) : Operation
    }
}
