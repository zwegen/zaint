package de.zwegen.zpaint.tools.options

import android.graphics.Paint
import android.view.View
import de.zwegen.zpaint.tools.TextFont

enum class ZaintTextEffect {
    NONE,
    OUTLINE,
    SHADOW,
    BACKGROUND
}

interface ZaintTextOptions {
    fun setState(
        bold: Boolean,
        italic: Boolean,
        underlined: Boolean,
        text: String,
        textSize: Int,
        textFont: TextFont,
        lineSpacingPercent: Int,
        letterSpacingPercent: Int,
        textAlignment: Paint.Align,
        justified: Boolean,
        outline: Boolean,
        shadow: Boolean,
        textBackground: Boolean
    )

    fun setCallback(listener: Callback)

    fun hideKeyboard()

    fun showKeyboard()

    fun showTextInputDialog(currentText: String, clearInitialText: Boolean)

    fun getTopLayout(): View

    fun getBottomLayout(): View

    fun setShapeSizeText(shapeSize: String)

    fun toggleShapeSizeVisibility(isVisible: Boolean)

    interface Callback {
        fun setText(text: String)

        fun setFont(textFont: TextFont)

        fun setUnderlined(underlined: Boolean)

        fun setItalic(italic: Boolean)

        fun setBold(bold: Boolean)

        fun setLineSpacingPercent(lineSpacingPercent: Int)

        fun setLetterSpacingPercent(letterSpacingPercent: Int)

        fun setTextAlignment(textAlignment: Paint.Align, justified: Boolean)

        fun setTextSize(size: Int)

        fun setTextEffect(textEffect: ZaintTextEffect)

        fun hideToolOptions()
    }
}
