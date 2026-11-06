package com.neonsheep.myreceipts.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app has no destructive fallback past [WIPED_VERSIONS], so a version bump without a migration would make
 * an update crash on open. This catches it before release: the chain must run unbroken to the current version.
 */
class MigrationsTest {
    private val current = DB_VERSION

    @Test
    fun `the migrations run unbroken from the first migrated version to the current one`() {
        val first = WIPED_VERSIONS.max() + 1
        var at = first
        for (m in MIGRATIONS) {
            assertEquals("migration ${m.startVersion}→${m.endVersion} out of order", at, m.startVersion)
            at = m.endVersion
        }
        assertEquals("the last migration must reach AppDatabase's version", current, at)
    }

    @Test
    fun `only development versions may be wiped`() {
        assertTrue(WIPED_VERSIONS.all { it < MIGRATIONS.first().startVersion })
    }
}
