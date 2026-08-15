package com.alsaeeddev.recapp

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.alsaeeddev.recapp.data.model.RecordingSettings
import com.alsaeeddev.recapp.data.model.RecordingState
import com.alsaeeddev.recapp.ui.screens.HomeScreen
import com.alsaeeddev.recapp.ui.theme.ScreenRecorderTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    composeTestRule.setContent {
      ScreenRecorderTheme {
        HomeScreen(
          recordingState = RecordingState.Idle,
          settings = RecordingSettings(),
          recentRecords = emptyList(),
          onStartRecording = {},
          onPauseRecording = {},
          onResumeRecording = {},
          onStopRecording = {},
          onToggleFloatingBubble = {},
          onOpenSettings = {},
          onSelectRecordItem = {},
          onTakeScreenshot = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
