package com.preferans.scorer.i18n

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Build-time safety net for translations.
 *
 * Walks every `res/values-XX/` directory and verifies its `strings.xml` and
 * (where present) `plurals.xml` have exactly the same set of keys as the
 * default `res/values/` files. Any missing or extra key fails the build with
 * a precise diff, so the next person who drops in a new locale can't ship a
 * half-translated UI.
 *
 * Unit tests run from the `app/` working directory under Gradle.
 */
class StringResourceParityTest {

    private val resDir = File("src/main/res")

    @Test
    fun allLocalesHaveSameStringKeysAsDefault() {
        val baseKeys = extractKeys(File(resDir, "values/strings.xml"))
        val errors = compareAcrossLocales("strings.xml", baseKeys)
        assertTrue(
            "String-key parity check failed:\n" + errors.joinToString("\n"),
            errors.isEmpty(),
        )
    }

    @Test
    fun allLocalesHaveSamePluralKeysAsDefault() {
        val basePluralsFile = File(resDir, "values/plurals.xml")
        if (!basePluralsFile.exists()) return // nothing to check
        val baseKeys = extractKeys(basePluralsFile, tag = "plurals")
        val errors = compareAcrossLocales("plurals.xml", baseKeys, tag = "plurals")
        assertTrue(
            "Plural-key parity check failed:\n" + errors.joinToString("\n"),
            errors.isEmpty(),
        )
    }

    private fun compareAcrossLocales(
        filename: String,
        baseKeys: Set<String>,
        tag: String = "string",
    ): List<String> {
        val localeDirs = resDir.listFiles { f ->
            f.isDirectory && f.name.startsWith("values-")
        } ?: emptyArray()
        val errors = mutableListOf<String>()
        for (dir in localeDirs) {
            val file = File(dir, filename)
            if (!file.exists()) continue // locale doesn't override this resource type
            val keys = extractKeys(file, tag = tag)
            val missing = baseKeys - keys
            val extra = keys - baseKeys
            if (missing.isNotEmpty()) {
                errors += "  ${dir.name}/$filename is MISSING keys: ${missing.sorted()}"
            }
            if (extra.isNotEmpty()) {
                errors += "  ${dir.name}/$filename has EXTRA keys not in default: ${extra.sorted()}"
            }
        }
        return errors
    }

    private fun extractKeys(file: File, tag: String = "string"): Set<String> {
        val regex = Regex("""<$tag\s+name="([^"]+)"""")
        return regex.findAll(file.readText())
            .map { it.groupValues[1] }
            .toSet()
    }
}
