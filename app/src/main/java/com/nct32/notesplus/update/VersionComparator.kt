package com.nct32.notesplus.update

/**
 * Compares free-form version strings (e.g. `v1.2.3`, `alpha-0.0.1-rev4`).
 *
 * Versions are compared as sequences of numeric groups extracted from the
 * raw string; groups missing on one side count as `0`.
 */
object VersionComparator {

    /**
     * Extracts every run of digits from [s] as a list of integers.
     *
     * Examples:
     * - `"alpha-0.0.1-rev4"` → `[0, 0, 1, 4]`
     * - `"v1.2.3"` → `[1, 2, 3]`
     * - `"not-a-version"` → `[]`
     */
    fun parseVersion(s: String): List<Int> =
        s.split(Regex("[^0-9]+"))
            .filter { it.isNotEmpty() }
            .mapNotNull { it.toIntOrNull() }

    /**
     * Compares [release] against [installed] element-wise.
     *
     * @return a negative value if [release] is older, `0` if the two are
     *   equivalent, and a positive value if [release] is newer.
     */
    fun compare(release: String, installed: String): Int {
        val a = parseVersion(release)
        val b = parseVersion(installed)
        val size = maxOf(a.size, b.size)
        for (i in 0 until size) {
            val x = a.getOrNull(i) ?: 0
            val y = b.getOrNull(i) ?: 0
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    /** @return `true` if [release] is strictly newer than [installed]. */
    fun isNewer(release: String, installed: String): Boolean = compare(release, installed) > 0
}
