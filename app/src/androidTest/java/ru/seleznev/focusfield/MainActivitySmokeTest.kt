package ru.seleznev.focusfield

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** Инструментальный UI-тест (Espresso): главная активность запускается и отображает тулбар. */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @Test
    fun mainActivityShowsToolbar() {
        ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.toolbar)).check(matches(isDisplayed()))
    }
}
