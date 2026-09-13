/*
 * Zaint: Keeps the tool and filter bars on a shared five-column grid.
 */
package de.zwegen.zpaint.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Space
import android.widget.TableLayout
import android.widget.TableRow

/**
 * Reflows the existing toolbar buttons into rows of five and fills the last
 * row with empty cells. Buttons can therefore be added or removed from the
 * layout without manually maintaining row breaks or placeholder views.
 */
object FiveColumnToolbarOrganizer {
    private const val COLUMN_COUNT = 5
    private const val FIRST_ROW_TOP_MARGIN_DP = 12
    private const val ROW_MARGIN_DP = 6
    private const val LAST_ROW_BOTTOM_MARGIN_DP = 12

    fun organize(table: TableLayout) {
        val buttons = table.rows()
            .flatMap { row -> row.children.filterNot { it is Space } }

        val rowCount = requiredRowCount(buttons.size)
        table.ensureRowCount(rowCount)
        table.rows().forEach { row -> row.removeAllViews() }

        buttons.forEachIndexed { index, button ->
            table.rowAt(index / COLUMN_COUNT).addView(button)
        }

        table.rows().forEachIndexed { index, row ->
            row.updateMargins(index, rowCount)
            repeat(COLUMN_COUNT - row.childCount) {
                row.addView(row.emptyCell(table.context))
            }
        }
    }

    private fun requiredRowCount(buttonCount: Int): Int =
        (buttonCount + COLUMN_COUNT - 1) / COLUMN_COUNT

    private fun TableLayout.ensureRowCount(rowCount: Int) {
        while (childCount < rowCount) {
            addView(TableRow(context))
        }
        while (childCount > rowCount) {
            removeViewAt(childCount - 1)
        }
    }

    private fun TableLayout.rows(): List<TableRow> =
        (0 until childCount).mapNotNull { getChildAt(it) as? TableRow }

    private fun TableLayout.rowAt(index: Int): TableRow = getChildAt(index) as TableRow

    private val TableRow.children: List<View>
        get() = (0 until childCount).map { getChildAt(it) }

    private fun TableRow.updateMargins(index: Int, rowCount: Int) {
        val layoutParams = (layoutParams as? TableLayout.LayoutParams)
            ?: TableLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        layoutParams.topMargin = dp(index.takeIf { it == 0 }?.let { FIRST_ROW_TOP_MARGIN_DP } ?: ROW_MARGIN_DP)
        layoutParams.bottomMargin = dp(if (index == rowCount - 1) LAST_ROW_BOTTOM_MARGIN_DP else ROW_MARGIN_DP)
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        this.layoutParams = layoutParams
    }

    private fun TableRow.emptyCell(context: Context): Space =
        Space(context).apply {
            layoutParams = TableRow.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1f
            )
        }

    private fun View.dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
