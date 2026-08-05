package com.myfitnesslog.feature.update.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for version ordering, which is the whole decision the update feature rests
 * on: get this wrong and the app either never offers an update or offers a
 * downgrade, both silently.
 */
class AppVersionTest {

    @Test
    fun `a higher minor is newer even when it has more digits`() {
        // The case string comparison gets wrong: "1.10.0" < "1.9.0" lexically. Left
        // unguarded this would stop offering updates the first time MINOR reached
        // 10 and look like the feature had simply stopped working.
        val ten = AppVersion.parseOrNull("1.10.0")!!
        val nine = AppVersion.parseOrNull("1.9.0")!!
        assertTrue(ten > nine)
    }

    @Test
    fun `major beats minor and patch`() {
        assertTrue(AppVersion.parseOrNull("2.0.0")!! > AppVersion.parseOrNull("1.99.99")!!)
    }

    @Test
    fun `patch is compared last`() {
        assertTrue(AppVersion.parseOrNull("1.2.3")!! > AppVersion.parseOrNull("1.2.2")!!)
        assertTrue(AppVersion.parseOrNull("1.3.0")!! > AppVersion.parseOrNull("1.2.99")!!)
    }

    @Test
    fun `equal versions are neither newer nor older`() {
        assertEquals(AppVersion.parseOrNull("1.2.0"), AppVersion.parseOrNull("1.2.0"))
        assertEquals(0, AppVersion.parseOrNull("1.2.0")!!.compareTo(AppVersion(1, 2, 0)))
    }

    @Test
    fun `a leading v is tolerated so a release tag parses directly`() {
        assertEquals(AppVersion(1, 2, 0), AppVersion.parseOrNull("v1.2.0"))
    }

    @Test
    fun `surrounding whitespace is tolerated`() {
        assertEquals(AppVersion(1, 2, 0), AppVersion.parseOrNull("  1.2.0 "))
    }

    @Test
    fun `unparseable input yields null rather than throwing`() {
        // Every one of these is reachable: a malformed backend reply, a snapshot
        // suffix, a two-part version. None may crash a launch-time check.
        assertNull(AppVersion.parseOrNull(null))
        assertNull(AppVersion.parseOrNull(""))
        assertNull(AppVersion.parseOrNull("1.2"))
        assertNull(AppVersion.parseOrNull("1.2.0-SNAPSHOT"))
        assertNull(AppVersion.parseOrNull("nightly"))
        assertNull(AppVersion.parseOrNull("1.2.0.1"))
    }

    @Test
    fun `toString drops any v prefix so the UI shows a bare version`() {
        assertEquals("1.2.0", AppVersion.parseOrNull("v1.2.0")!!.toString())
    }
}
