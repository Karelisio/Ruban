package com.karelisio.ruban.screens

import android.provider.Settings
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.karelisio.ruban.MainActivity
import com.karelisio.ruban.RubanApp
import com.karelisio.ruban.data.model.GiftStatus
import com.karelisio.ruban.data.model.Idea
import com.karelisio.ruban.data.model.Person
import com.karelisio.ruban.data.model.PricePoint
import com.karelisio.ruban.data.model.Priority
import com.karelisio.ruban.data.model.ReceivedGift
import com.karelisio.ruban.data.model.Relation
import com.karelisio.ruban.data.model.EventKind
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

class TestRubanApp : RubanApp() {
    override fun onCreate() {
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(this)
        super.onCreate()
    }
}

/**
 * Renders the real app on the JVM (Robolectric native graphics) to check screens visually and catch crashes.
 * Run with: ./gradlew :app:testDebugUnitTest -Pscreenshots — images land in app/build/shots.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h900dp-xxhdpi", application = TestRubanApp::class)
class ScreenshotTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<RubanApp>()

    /** Lets background database work land on the main thread (waitForIdle alone doesn't track Room). */
    private fun settle() {
        repeat(15) {
            Thread.sleep(40)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            compose.waitForIdle()
        }
    }

    private fun shot(name: String) {
        settle()
        compose.mainClock.advanceTimeBy(1_500)
        compose.onAllNodes(isRoot()).let { roots ->
            roots.fetchSemanticsNodes().indices.last().let { roots[it].captureRoboImage("build/shots/$name.png") }
        }
    }

    private fun click(text: String) {
        settle()
        compose.onAllNodesWithText(text, substring = true).onFirst().performClick()
        settle()
    }

    @Before
    fun seed() = runBlocking {
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val c = app.container
        c.settings.setOnboardingDone(true)
        c.settings.setDynamicColor(false)
        c.events.seedHolidays()
        val today = LocalDate.now()
        val soon = today.plusDays(12)
        val lea = c.persons.save(
            Person(
                name = "Léa", relation = Relation.PARTNER, birthdayMonth = soon.monthValue, birthdayDay = soon.dayOfMonth, birthYear = 1994,
                nameDayMonth = 3, nameDayDay = 22, interests = listOf("lecture", "the", "voyage", "plantes"),
                clothingSize = "M", shoeSize = "38", ringSize = "52", favoriteColors = "Vert sauge, terracotta",
                avoid = "Allergie aux noix", yearlyBudgetCents = 400_00,
            ),
        )
        val later = today.plusDays(47)
        val maman = c.persons.save(Person(name = "Maman", relation = Relation.PARENT, birthdayMonth = later.monthValue, birthdayDay = later.dayOfMonth, interests = listOf("jardinage", "cuisine")))
        val hugo = c.persons.save(Person(name = "Hugo Martin", relation = Relation.FRIEND, birthdayMonth = 2, birthdayDay = 3, interests = listOf("jeux_societe", "biere")))
        val birthday = c.db.events().forPerson(lea, EventKind.BIRTHDAY)!!.id
        val teapot = c.ideas.save(
            Idea(title = "Théière en fonte japonaise", priceCents = 64_90, url = "https://exemple.fr/theiere", priority = Priority.STRONG, eventId = birthday, occasionYear = soon.year),
            listOf(lea),
        )
        listOf(79_90L, 74_90L, 69_90L, 64_90L).forEachIndexed { i, p ->
            c.db.prices().insert(PricePoint(ideaId = teapot, priceCents = p, currency = "EUR", checkedAt = System.currentTimeMillis() - (4 - i) * 86_400_000L * 3))
        }
        c.ideas.save(Idea(title = "Week-end à Lisbonne", priceCents = 420_00, priority = Priority.NICE, status = GiftStatus.BOUGHT, boughtAt = System.currentTimeMillis()), listOf(lea))
        c.ideas.save(Idea(title = "Sécateur forgé", priceCents = 39_00, status = GiftStatus.WRAPPED, priority = Priority.NICE), listOf(maman))
        c.ideas.save(Idea(title = "Jeu coopératif", priceCents = 34_90, priority = Priority.MAYBE), listOf(hugo))
        c.ideas.save(Idea(title = "Carnet en lin", priority = Priority.NICE), emptyList())
        c.ideas.save(
            Idea(
                title = "Pull en mérinos", priceCents = 89_00, status = GiftStatus.GIVEN, givenAt = System.currentTimeMillis() - 200 * 86_400_000L,
                reactionEmoji = "😍", reactionNote = "Elle ne le quitte plus",
            ),
            listOf(lea),
        )
        c.received.save(ReceivedGift(title = "Coffret de thés", fromPersonId = lea, receivedOn = today.minusDays(40).toEpochDay(), occasion = EventKind.BIRTHDAY))
        c.suggestions.ensureLoaded()
    }

    @Test
    fun appTour() {
        ActivityScenario.launch(MainActivity::class.java).use {
            shot("01-idees")
            click("Proches"); shot("02-proches")
            click("Agenda"); shot("03-agenda")
            click("Calendrier"); shot("04-calendrier")
            click("Budget"); shot("05-budget")
            click("Récap"); shot("06-recap")
            compose.activityRule().pressBackUnconditionally()
            click("Idées"); click("Théière"); shot("07-idee")
            compose.activityRule().pressBackUnconditionally()
            click("Proches"); click("Léa"); shot("08-proche")
            click("Inspire-moi"); shot("09-inspire")
        }
    }

    @Test
    fun formsAndSettings() {
        ActivityScenario.launch(MainActivity::class.java).use {
            click("Proches"); click("Léa"); click("Anniversaire"); shot("10-evenement")
            compose.activityRule().pressBackUnconditionally()
            compose.activityRule().pressBackUnconditionally()
            click("Idées")
            compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Nouvelle idée")).onFirst().performClick()
            click("Écrire"); shot("11-nouvelle-idee")
        }
    }

    @Test
    fun settingsScreens() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Réglages")).onFirst().performClick()
            shot("12-reglages")
            click("Autorisations"); shot("13-autorisations")
        }
    }

    @Test
    fun onboarding() {
        runBlocking { app.container.settings.setOnboardingDone(false) }
        ActivityScenario.launch(MainActivity::class.java).use {
            shot("14-onboarding")
            click("Commencer"); shot("15-onboarding-2")
        }
    }

    @Test
    fun dynamicColors() {
        runBlocking { app.container.settings.setDynamicColor(true) }
        ActivityScenario.launch(MainActivity::class.java).use { shot("16-idees-dynamique") }
    }
}

private fun androidx.compose.ui.test.junit4.ComposeTestRule.activityRule() = this

private fun androidx.compose.ui.test.junit4.ComposeTestRule.pressBackUnconditionally() {
    androidx.test.espresso.Espresso.pressBackUnconditionally()
    waitForIdle()
}
