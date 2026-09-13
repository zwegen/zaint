package de.zwegen.zpaint.ui.tools

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.text.TextPaint
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import de.zwegen.zpaint.R
import de.zwegen.zpaint.tools.GoogleFontRepository
import de.zwegen.zpaint.tools.TextFont
import kotlin.math.max

/** Zaint's picker for built-in and locally installed fonts. */
class ZaintFontPickerAdapter(
    private val context: Context,
    initialFonts: List<TextFont>,
    private val onFontSelected: (TextFont) -> Unit,
    private val onSearchRequested: () -> Unit,
    private val onCustomFontHeld: (TextFont) -> Unit
) : RecyclerView.Adapter<ZaintFontPickerAdapter.FontViewHolder>() {
    private val inflater = LayoutInflater.from(context)
    private var fonts = initialFonts
    private var selectedPosition = 0
    private var previewTypefaces = fonts.map(::typefaceFor)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FontViewHolder =
        FontViewHolder(inflater.inflate(R.layout.zpaint_item_font, parent, false))

    override fun onBindViewHolder(holder: FontViewHolder, position: Int) {
        if (position == fonts.size) {
            holder.bindSearchAction()
        } else {
            holder.bindFont(fonts[position], previewTypefaces[position], position == selectedPosition)
        }
    }

    override fun getItemCount(): Int = fonts.size + 1

    fun select(font: TextFont) {
        selectPosition(fonts.indexOfFirst { it.id == font.id }.coerceAtLeast(0))
    }

    @SuppressLint("NotifyDataSetChanged")
    fun replaceFonts(updatedFonts: List<TextFont>, selectedFont: TextFont) {
        fonts = updatedFonts
        previewTypefaces = fonts.map(::typefaceFor)
        selectedPosition = fonts.indexOfFirst { it.id == selectedFont.id }.coerceAtLeast(0)
        notifyDataSetChanged()
    }

    private fun selectPosition(position: Int) {
        if (position !in fonts.indices) return
        val previous = selectedPosition
        selectedPosition = position
        notifyItemChanged(previous)
        notifyItemChanged(selectedPosition)
    }

    private fun typefaceFor(font: TextFont): Typeface =
        font.builtInFont?.resourceFor(isBold = false, isItalic = false)?.let {
            ResourcesCompat.getFont(context, it)
        } ?: if (font.isCustom) {
            GoogleFontRepository.typefaceFor(context, font, Typeface.NORMAL)
        } else {
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

    inner class FontViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val chip: Chip = itemView.findViewById(R.id.zpaint_font_type)
        private val defaultTextSizePx = chip.textSize
        private val minimumTextSizePx = 10f * chip.resources.displayMetrics.scaledDensity
        private val maximumLabelWidthPx = 72f * chip.resources.displayMetrics.density

        fun bindFont(font: TextFont, typeface: Typeface, selected: Boolean) {
            val fullLabel = font.builtInFont?.let { context.getString(it.label) }
                ?: font.displayName.orEmpty().trim()
            chip.setTextSize(TypedValue.COMPLEX_UNIT_PX, defaultTextSizePx)
            chip.text = fullLabel
            if (font.isCustom && chip.paint.measureText(fullLabel) > maximumLabelWidthPx) {
                chip.text = fullLabel.substringBefore(' ')
            }
            fitLabelTextSize(typeface)
            chip.isChecked = selected
            chip.isChipIconVisible = false
            chip.isCheckedIconVisible = false
            chip.chipIcon = null
            chip.setOnClickListener {
                val position = layoutPosition
                if (position in fonts.indices) {
                    selectPosition(position)
                    onFontSelected(font)
                }
            }
            chip.setOnLongClickListener {
                if (font.isCustom) {
                    onCustomFontHeld(font)
                    true
                } else {
                    false
                }
            }
            // Chip resets its text paint when its size changes, so set the preview font last.
            chip.typeface = typeface
        }

        fun bindSearchAction() {
            chip.text = context.getString(R.string.text_tool_dialog_font_search)
            chip.typeface = Typeface.DEFAULT_BOLD
            chip.setTextSize(TypedValue.COMPLEX_UNIT_PX, defaultTextSizePx)
            chip.isChecked = false
            chip.isChipIconVisible = true
            chip.isCheckedIconVisible = false
            chip.chipIcon = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_zpaint_search, context.theme)
            chip.setOnClickListener { onSearchRequested() }
            chip.setOnLongClickListener(null)
        }

        private fun fitLabelTextSize(typeface: Typeface) {
            val previewPaint = TextPaint(chip.paint).apply {
                this.typeface = typeface
                textSize = defaultTextSizePx
            }
            val labelWidthPx = previewPaint.measureText(chip.text.toString())
            if (labelWidthPx > maximumLabelWidthPx) {
                val fittedTextSizePx = defaultTextSizePx * maximumLabelWidthPx / labelWidthPx
                chip.setTextSize(TypedValue.COMPLEX_UNIT_PX, max(minimumTextSizePx, fittedTextSizePx))
            }
        }
    }
}
