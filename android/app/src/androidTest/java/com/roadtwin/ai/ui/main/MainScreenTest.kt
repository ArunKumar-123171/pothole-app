package com.roadtwin.ai.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.roadtwin.ai.core.theme.RoadTwinTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setup() {
    composeTestRule.setContent {
      RoadTwinTheme {
        Text("RoadTwin AI")
      }
    }
  }

  @Test
  fun appTitle_exists() {
    composeTestRule.onNodeWithText("RoadTwin AI").assertExists()
  }
}
