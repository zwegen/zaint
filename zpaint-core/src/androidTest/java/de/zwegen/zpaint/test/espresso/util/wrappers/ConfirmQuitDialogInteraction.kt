package de.zwegen.zpaint.test.espresso.util.wrappers

import android.widget.Button
import androidx.test.espresso.Espresso
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withText
import de.zwegen.zpaint.R
import org.hamcrest.Matchers.allOf

class ConfirmQuitDialogInteraction private constructor() :
    CustomViewInteraction(
        Espresso.onView(withText(R.string.closing_security_question))
            .inRoot(RootMatchers.isDialog())
    ) {
    fun onPositiveButton(): ViewInteraction {
        return Espresso.onView(
            allOf(
                ViewMatchers.withId(android.R.id.button1),
                withText(R.string.save_button_text),
                ViewMatchers.isAssignableFrom(
                    Button::class.java
                )
            )
        )
    }

    fun checkPositiveButton(matcher: ViewAssertion?): ConfirmQuitDialogInteraction {
        onPositiveButton()
            .check(matcher)
        return this
    }

    fun onNegativeButton(): ViewInteraction {
        return Espresso.onView(
            allOf(
                ViewMatchers.withId(android.R.id.button2),
                withText(R.string.discard_button_text),
                ViewMatchers.isAssignableFrom(
                    Button::class.java
                )
            )
        )
    }

    fun checkNegativeButton(matcher: ViewAssertion?): ConfirmQuitDialogInteraction {
        onNegativeButton()
            .check(matcher)
        return this
    }

    fun checkNeutralButton(matcher: ViewAssertion?): ConfirmQuitDialogInteraction {
        Espresso.onView(ViewMatchers.withId(android.R.id.button3))
            .check(matcher)
        return this
    }

    fun checkMessage(matcher: ViewAssertion?): ConfirmQuitDialogInteraction {
        Espresso.onView(withText(R.string.closing_security_question))
            .check(matcher)
        return this
    }

    fun checkTitle(matcher: ViewAssertion?): ConfirmQuitDialogInteraction {
        Espresso.onView(withText(R.string.closing_security_question_title))
            .check(matcher)
        return this
    }

    companion object {
        fun onConfirmQuitDialog(): ConfirmQuitDialogInteraction = ConfirmQuitDialogInteraction()
    }
}
