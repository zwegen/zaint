package de.zwegen.zpaint.test.espresso.rtl.util

class RtlUiTestUtils private constructor() {
    companion object {
        fun checkTextDirection(string: String): Boolean {
            return Character.getDirectionality(string[0]) == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
                     Character.getDirectionality(string[0]) == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
        }
    }
}
