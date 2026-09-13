package de.zwegen.zpaint.command.serialization

import android.graphics.Paint
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import de.zwegen.zpaint.tools.ZaintFontFamily

data class ZaintTextStyle(
    val font: ZaintFontFamily,
    val bold: Boolean,
    val underline: Boolean,
    val italic: Boolean,
    val textSize: Float,
    val textSkewX: Float,
    val textAlignment: Paint.Align = Paint.Align.LEFT,
    val outline: Boolean = false,
    val shadow: Boolean = false,
    val lineSpacingPercent: Int = 100,
    val textBackground: Boolean = false,
    val customFontName: String? = null,
    val customFontUri: String? = null,
    val letterSpacingPercent: Int = 0,
    val justified: Boolean = false
) {

    class TypefaceSerializer(version: Int) : ZaintVersionedSerializer<ZaintTextStyle>(version) {
        override fun write(kryo: Kryo, output: Output, typeface: ZaintTextStyle) {
            with(output) {
                writeString(typeface.font.name)
                writeBoolean(typeface.bold)
                writeBoolean(typeface.underline)
                writeBoolean(typeface.italic)
                writeFloat(typeface.textSize)
                writeFloat(typeface.textSkewX)
                writeString(typeface.textAlignment.name)
                writeBoolean(typeface.outline)
                writeBoolean(typeface.shadow)
                writeInt(typeface.lineSpacingPercent)
                writeBoolean(typeface.textBackground)
                writeBoolean(typeface.customFontName != null && typeface.customFontUri != null)
                if (typeface.customFontName != null && typeface.customFontUri != null) {
                    writeString(typeface.customFontName)
                    writeString(typeface.customFontUri)
                }
            }
        }

        override fun read(kryo: Kryo, input: Input, type: Class<out ZaintTextStyle>): ZaintTextStyle =
            super.handleVersions(this, kryo, input, type)

        override fun readV2(
            serializer: ZaintVersionedSerializer<ZaintTextStyle>,
            kryo: Kryo,
            input: Input,
            type: Class<out ZaintTextStyle>
        ): ZaintTextStyle {
            return with(input) {
                ZaintTextStyle(
                    ZaintFontFamily.savedNameOrDefault(readString()),
                    readBoolean(),
                    readBoolean(),
                    readBoolean(),
                    readFloat(),
                    readFloat()
                )
            }
        }

        override fun readV3(
            serializer: ZaintVersionedSerializer<ZaintTextStyle>,
            kryo: Kryo,
            input: Input,
            type: Class<out ZaintTextStyle>
        ): ZaintTextStyle {
            return with(input) {
                ZaintTextStyle(
                    ZaintFontFamily.savedNameOrDefault(readString()),
                    readBoolean(),
                    readBoolean(),
                    readBoolean(),
                    readFloat(),
                    readFloat(),
                    Paint.Align.valueOf(readString())
                )
            }
        }

        override fun readV4(
            serializer: ZaintVersionedSerializer<ZaintTextStyle>,
            kryo: Kryo,
            input: Input,
            type: Class<out ZaintTextStyle>
        ): ZaintTextStyle {
            return with(input) {
                ZaintTextStyle(
                    ZaintFontFamily.savedNameOrDefault(readString()),
                    readBoolean(),
                    readBoolean(),
                    readBoolean(),
                    readFloat(),
                    readFloat(),
                    Paint.Align.valueOf(readString()),
                    readBoolean(),
                    readBoolean()
                )
            }
        }

        override fun readCurrentVersion(kryo: Kryo, input: Input, type: Class<out ZaintTextStyle>): ZaintTextStyle {
            return with(input) {
                ZaintTextStyle(
                    ZaintFontFamily.savedNameOrDefault(readString()),
                    readBoolean(),
                    readBoolean(),
                    readBoolean(),
                    readFloat(),
                    readFloat(),
                    Paint.Align.valueOf(readString()),
                    readBoolean(),
                    readBoolean(),
                    readInt(),
                    readBoolean()
                ).let { typeface ->
                    val typefaceWithCustomFont = if (readBoolean()) {
                        typeface.copy(customFontName = readString(), customFontUri = readString())
                    } else {
                        typeface
                    }
                    typefaceWithCustomFont
                }
            }
        }

        override fun readV15(
            serializer: ZaintVersionedSerializer<ZaintTextStyle>,
            kryo: Kryo,
            input: Input,
            type: Class<out ZaintTextStyle>
        ): ZaintTextStyle =
            readCurrentVersion(kryo, input, type).copy(textBackground = input.readString() != "NONE")

        override fun readV14(
            serializer: ZaintVersionedSerializer<ZaintTextStyle>,
            kryo: Kryo,
            input: Input,
            type: Class<out ZaintTextStyle>
        ): ZaintTextStyle {
            return with(input) {
                ZaintTextStyle(
                    ZaintFontFamily.savedNameOrDefault(readString()),
                    readBoolean(),
                    readBoolean(),
                    readBoolean(),
                    readFloat(),
                    readFloat(),
                    Paint.Align.valueOf(readString()),
                    readBoolean(),
                    readBoolean(),
                    readInt(),
                    readBoolean()
                ).let { typeface ->
                    if (readBoolean()) {
                        typeface.copy(customFontName = readString(), customFontUri = readString())
                    } else {
                        typeface
                    }
                }
            }
        }

        override fun readV12(
            serializer: ZaintVersionedSerializer<ZaintTextStyle>,
            kryo: Kryo,
            input: Input,
            type: Class<out ZaintTextStyle>
        ): ZaintTextStyle {
            return with(input) {
                ZaintTextStyle(
                    ZaintFontFamily.savedNameOrDefault(readString()),
                    readBoolean(),
                    readBoolean(),
                    readBoolean(),
                    readFloat(),
                    readFloat(),
                    Paint.Align.valueOf(readString()),
                    readBoolean(),
                    readBoolean(),
                    readInt(),
                    readBoolean()
                )
            }
        }

        override fun readV10(
            serializer: ZaintVersionedSerializer<ZaintTextStyle>,
            kryo: Kryo,
            input: Input,
            type: Class<out ZaintTextStyle>
        ): ZaintTextStyle {
            return with(input) {
                ZaintTextStyle(
                    ZaintFontFamily.savedNameOrDefault(readString()),
                    readBoolean(),
                    readBoolean(),
                    readBoolean(),
                    readFloat(),
                    readFloat(),
                    Paint.Align.valueOf(readString()),
                    readBoolean(),
                    readBoolean(),
                    readInt()
                )
            }
        }
    }
}
