package de.zwegen.zpaint.tools.helper

/** Creates an isolated fill operation for one command execution. */
interface FillAlgorithmFactory {
    fun createFillAlgorithm(): FillAlgorithm
}
