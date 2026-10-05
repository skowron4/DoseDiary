package com.example.dosediary

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dosediary.domain.model.SymptomTag
import com.example.dosediary.presentation.common.labelRes
import com.example.dosediary.presentation.symptom.SymptomTagRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Regression tests for the Symptom Diary crash: reading `shownItemCount` of a `FlowRow` overflow
 * indicator during composition threw `IllegalStateException: Accessing noOfItemsShown before it is set`.
 * That only happens during real layout, so it cannot be caught by a plain JVM unit test.
 */
class SymptomTagRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun overflowingTagsRenderWithAHiddenCountInsteadOfCrashing() {
        composeRule.setContent {
            SymptomTagRow(tags = SymptomTag.entries.toSet(), modifier = Modifier.width(240.dp))
        }

        val shown = SymptomTag.entries.count { tag -> isTagDisplayed(tag) }
        assertTrue("at least one tag is shown", shown >= 1)
        assertTrue("not all tags fit in 240dp", shown < SymptomTag.entries.size)
        // The "+N" chip accounts for every tag that did not fit.
        composeRule.onNodeWithText("+${SymptomTag.entries.size - shown}").assertIsDisplayed()
    }

    @Test
    fun fewTagsAreAllShownWithoutAnIndicator() {
        composeRule.setContent {
            SymptomTagRow(tags = setOf(SymptomTag.HEADACHE, SymptomTag.NAUSEA), modifier = Modifier.width(320.dp))
        }

        composeRule.onNodeWithText(str(R.string.tag_headache)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.tag_nausea)).assertIsDisplayed()
        // The indicator may be composed off-screen, but it must not be visible when nothing is hidden.
        val indicators = composeRule.onAllNodes(hasText("+", substring = true))
        val visible = indicators.fetchSemanticsNodes().indices.count { indicators[it].isDisplayed() }
        assertEquals(0, visible)
    }
    @Test
    fun noTagsRendersNothing() {
        composeRule.setContent { SymptomTagRow(tags = emptySet()) }
        assertEquals(0, composeRule.onAllNodes(hasText("", substring = true)).fetchSemanticsNodes().size)
    }

    private fun isTagDisplayed(tag: SymptomTag): Boolean {
        val nodes = composeRule.onAllNodesWithText(str(tag.labelRes()))
        return nodes.fetchSemanticsNodes().indices.any { nodes[it].isDisplayed() }
    }

    /** Localised text, so the test also passes on a device set to Polish. */
    private fun str(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
}
