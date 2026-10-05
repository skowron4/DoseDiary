package com.example.dosediary.i18n

import com.example.dosediary.domain.model.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards the translations: a missing or malformed string would otherwise silently fall back to
 * English (or crash at runtime on a bad format placeholder).
 *
 * Gradle runs unit tests with the module directory (`app/`) as working directory.
 */
class StringResourcesTest {

    private data class Entry(val value: String, val translatable: Boolean)

    private val resDir = File("src/main/res")

    private fun parseStrings(path: String): Map<String, Entry> {
        val file = File(resDir, path)
        assertTrue("Missing resource file: ${file.absolutePath}", file.exists())
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).associate { index ->
            val element = nodes.item(index) as Element
            element.getAttribute("name") to Entry(
                value = element.textContent,
                translatable = element.getAttribute("translatable") != "false",
            )
        }
    }

    private fun placeholders(text: String): List<String> =
        Regex("%\\d+\\$[a-zA-Z]").findAll(text).map { it.value }.sorted().toList()

    private val base get() = parseStrings("values/strings.xml")

    @Test
    fun `every translatable string exists in each translation`() {
        val translatableKeys = base.filterValues { it.translatable }.keys
        for (tag in translatedTags()) {
            val translated = parseStrings("values-$tag/strings.xml")
            val missing = translatableKeys - translated.keys
            assertTrue("values-$tag is missing: $missing", missing.isEmpty())
        }
    }

    @Test
    fun `translations contain no unknown or non-translatable keys`() {
        for (tag in translatedTags()) {
            val translated = parseStrings("values-$tag/strings.xml")
            val unknown = translated.keys - base.keys
            assertTrue("values-$tag has keys that do not exist in the default file: $unknown", unknown.isEmpty())
            val shouldNotBeTranslated = translated.keys.filter { base[it]?.translatable == false }
            assertTrue("values-$tag translates non-translatable keys: $shouldNotBeTranslated", shouldNotBeTranslated.isEmpty())
        }
    }

    @Test
    fun `translations keep the same format placeholders as the default`() {
        for (tag in translatedTags()) {
            val translated = parseStrings("values-$tag/strings.xml")
            for ((key, entry) in translated) {
                assertEquals(
                    "values-$tag/$key placeholders differ from the default string",
                    placeholders(base.getValue(key).value),
                    placeholders(entry.value),
                )
            }
        }
    }

    @Test
    fun `no string is blank`() {
        val all = mapOf("values" to base) + translatedTags().associate { "values-$it" to parseStrings("values-$it/strings.xml") }
        for ((folder, strings) in all) {
            val blank = strings.filterValues { it.value.isBlank() }.keys
            assertTrue("$folder has blank strings: $blank", blank.isEmpty())
        }
    }

    @Test
    fun `locales config lists every supported language`() {
        val file = File(resDir, "xml/locales_config.xml")
        assertTrue("Missing ${file.absolutePath}", file.exists())
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("locale")
        val declared = (0 until nodes.length)
            .map { (nodes.item(it) as Element).getAttribute("android:name") }
            .toSet()

        val supported = AppLanguage.entries.mapNotNull { it.languageTag }.toSet()
        assertEquals(supported, declared)
        assertEquals("every supported language needs a values-<tag> folder", supported - "en", translatedTags().toSet())
    }

    /** Language tags that have a `values-<tag>/strings.xml` next to the default file. */
    private fun translatedTags(): List<String> =
        resDir.listFiles { file -> file.isDirectory && file.name.matches(Regex("values-[a-z]{2,3}")) }
            .orEmpty()
            .filter { File(it, "strings.xml").exists() }
            .map { it.name.removePrefix("values-") }
            .sorted()
}
