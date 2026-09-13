package de.zwegen.zpaint.model

import de.zwegen.zpaint.command.Command

/**
 * Immutable replay snapshot used when a Zaint document restores its undo history.
 *
 * Keeping a private copy prevents a serializer-owned mutable list from changing after the
 * snapshot has been handed to the command manager.
 */
class CommandManagerModel(
    val initialCommand: Command,
    commands: Collection<Command>
) {
    val commands: List<Command> = commands.toList()
}
