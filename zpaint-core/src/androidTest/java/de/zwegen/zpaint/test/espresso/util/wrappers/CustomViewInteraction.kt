package de.zwegen.zpaint.test.espresso.util.wrappers

import androidx.test.espresso.FailureHandler
import androidx.test.espresso.Root
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.ViewInteraction
import org.hamcrest.Matcher

open class CustomViewInteraction constructor(var viewInteraction: ViewInteraction?) {

    fun perform(vararg viewActions: ViewAction?): ViewInteraction = viewInteraction!!.perform(*viewActions)

    fun withFailureHandler(var1: FailureHandler?): ViewInteraction = viewInteraction!!.withFailureHandler(var1)

    fun inRoot(var1: Matcher<Root?>?): ViewInteraction = viewInteraction!!.inRoot(var1)

    fun noActivity(): ViewInteraction = viewInteraction!!.noActivity()

    fun check(viewAssert: ViewAssertion?): ViewInteraction = viewInteraction!!.check(viewAssert)
}
