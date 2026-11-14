package com.titan.zedxai

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.titan.zedxai.ui.navigation.ZedxRoute
import com.titan.zedxai.ui.screens.AboutScreen
import com.titan.zedxai.ui.screens.ExploreToolsScreen
import com.titan.zedxai.ui.screens.OnboardingScreen
import com.titan.zedxai.ui.theme.ZedxTheme
import com.titan.zedxai.ui.theme.ZedxThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NavigationAndUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun allExpectedRoutesAreDefined() {
        assertTrue(ZedxRoute.entries.size >= 27)
        val routeNames = ZedxRoute.entries.map { it.route }.toSet()
        assertEquals(ZedxRoute.entries.size, routeNames.size)
    }

    @Test
    fun onboardingSkipInvokesCallback() {
        var finished = false
        composeRule.setContent {
            ZedxTheme(themeMode = ZedxThemeMode.WHITE_CRYSTAL) {
                OnboardingScreen(onFinished = { finished = true })
            }
        }
        composeRule.onNodeWithTag("onboarding_skip_button").assertIsDisplayed().performClick()
        assertTrue(finished)
    }

    @Test
    fun exploreAndAboutRenderZedxBrandingAndTools() {
        var navigated: ZedxRoute? = null
        composeRule.setContent {
            ZedxTheme(themeMode = ZedxThemeMode.DARK_SPACE) {
                ExploreToolsScreen(onNavigate = { navigated = it })
            }
        }
        composeRule.onNodeWithText("Explore ZEDX Ecosystem").assertIsDisplayed()
        composeRule.onNodeWithTag("explore_tool_chat").assertIsDisplayed().performClick()
        assertEquals(ZedxRoute.CHAT, navigated)
    }

    @Test
    fun aboutScreenDisplaysTitanBranding() {
        composeRule.setContent {
            ZedxTheme {
                AboutScreen()
            }
        }
        composeRule.onNodeWithText("BY THE TITAN AI • Version 1.0.0").assertIsDisplayed()
    }
}
