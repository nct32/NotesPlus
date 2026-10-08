package com.nct32.notesplus.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [VersionComparator].
 */
class VersionComparatorTest {

    // --- parseVersion -------------------------------------------------------

    @Test
    fun parseVersion_simpleSemVer() {
        assertEquals(listOf(1, 2, 3), VersionComparator.parseVersion("v1.2.3"))
    }

    @Test
    fun parseVersion_appStyleVersion() {
        assertEquals(listOf(0, 0, 1, 4), VersionComparator.parseVersion("alpha-0.0.1-rev4"))
    }

    @Test
    fun parseVersion_plainNumbers() {
        assertEquals(listOf(2, 10, 5), VersionComparator.parseVersion("2.10.5"))
    }

    @Test
    fun parseVersion_noDigits() {
        assertEquals(emptyList<Int>(), VersionComparator.parseVersion("not-a-version"))
    }

    @Test
    fun parseVersion_emptyString() {
        assertEquals(emptyList<Int>(), VersionComparator.parseVersion(""))
    }

    @Test
    fun parseVersion_mixedSeparators() {
        assertEquals(listOf(1, 0, 0, 1), VersionComparator.parseVersion("1_0-0.beta1"))
    }

    // --- compare / isNewer --------------------------------------------------

    @Test
    fun compare_releaseNewer() {
        assertTrue(VersionComparator.compare("1.2.0", "1.1.9") > 0)
        assertTrue(VersionComparator.isNewer("1.2.0", "1.1.9"))
    }

    @Test
    fun compare_equalVersions() {
        assertEquals(0, VersionComparator.compare("1.2.3", "v1.2.3"))
        assertFalse(VersionComparator.isNewer("1.2.3", "1.2.3"))
    }

    @Test
    fun compare_releaseOlder() {
        assertTrue(VersionComparator.compare("1.0.0", "1.0.1") < 0)
        assertFalse(VersionComparator.isNewer("1.0.0", "1.0.1"))
    }

    @Test
    fun compare_numericNotLexicographic() {
        // "2.10" must be newer than "2.9" (lexicographic comparison would say otherwise).
        assertTrue(VersionComparator.isNewer("2.10.0", "2.9.0"))
        assertFalse(VersionComparator.isNewer("2.9.0", "2.10.0"))
    }

    @Test
    fun compare_differentLengths_missingGroupsAreZero() {
        // "1.2" vs "1.2.0" → equal (missing group counts as 0).
        assertEquals(0, VersionComparator.compare("1.2", "1.2.0"))
        // "1.2.1" is newer than "1.2".
        assertTrue(VersionComparator.isNewer("1.2.1", "1.2"))
        // "1.2.0" is older than "1.2.0.1".
        assertFalse(VersionComparator.isNewer("1.2.0", "1.2.0.1"))
    }

    @Test
    fun compare_nonNumericStrings() {
        // No digits at all → both parse to empty lists → equal.
        assertEquals(0, VersionComparator.compare("alpha", "beta"))
        // Digits embedded in otherwise non-numeric strings still count.
        assertTrue(VersionComparator.isNewer("alpha-0.0.1-rev4", "alpha-0.0.1-rev3"))
        assertFalse(VersionComparator.isNewer("alpha-0.0.1-rev3", "alpha-0.0.1-rev4"))
    }

    @Test
    fun compare_appVersionNames() {
        // The app's own versionName format.
        assertTrue(VersionComparator.isNewer("alpha-0.0.2-rev1", "alpha-0.0.1-rev4"))
        assertFalse(VersionComparator.isNewer("alpha-0.0.1-rev4", "alpha-0.0.1-rev4"))
        assertFalse(VersionComparator.isNewer("alpha-0.0.1-rev4", "alpha-0.0.1-rev5"))
    }
}
