package com.example.dosediary

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.example.dosediary.presentation.symptom.SymptomTagRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * [SymptomTagRow] is a plain `Row` of precomputed chips. These tests guard the layout contract that the
 * list relies on: the "+N" chip is never pushed out, long labels truncate instead of overflowing, and
 * nothing throws during real layout (the earlier `FlowRow` version crashed there).
 */
class SymptomTagRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsAllGivenChipsAndTheHiddenCount() {
        composeRule.setContent {
            SymptomTagRow(tags = listOf("Headache", "Nausea", "Fatigue"), hiddenText = "+7", modifier = Modifier.width(320.dp))
        }

        composeRule.onNodeWithText("Headache").assertIsDisplayed()
        composeRule.onNodeWithText("Nausea").assertIsDisplayed()
        composeRule.onNodeWithText("Fatigue").assertIsDisplayed()
        composeRule.onNodeWithText("+7").assertIsDisplayed()
    }

    @Test
    fun hiddenCountStaysVisibleEvenWhenTheRowIsVeryNarrow() {
        composeRule.setContent {
            SymptomTagRow(
                tags = listOf("A very long symptom label", "Another very long symptom label"),
                hiddenText = "+3",
                modifier = Modifier.width(140.dp),
            )
        }

        composeRule.onNodeWithText("+3").assertIsDisplayed()
    }

    @Test
    fun noHiddenTextMeansNoIndicator() {
        composeRule.setContent {
            SymptomTagRow(tags = listOf("Headache"), hiddenText = null, modifier = Modifier.width(320.dp))
        }

        composeRule.onNodeWithText("Headache").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodes(hasText("+", substring = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun noTagsRendersNothing() {
        composeRule.setContent { SymptomTagRow(tags = emptyList(), hiddenText = "+1") }
        assertEquals(0, composeRule.onAllNodes(hasText("", substring = true)).fetchSemanticsNodes().size)
    }
}