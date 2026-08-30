package com.ratatoskr.mobile.library

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ratatoskr.mobile.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidAppLinkIntentTest {
    @get:Rule
    val activity = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun canonical_configured_https_routes_forward_raw_and_foreign_or_ambiguous_links_fail_closed() {
        activity.scenario.onActivity { mainActivity ->
            val analysis = "https://links.ratatoskr.test/analyses/$ID"
            mainActivity.acceptIntent(Intent(Intent.ACTION_VIEW, Uri.parse(analysis)))
            assertEquals(ID, mainActivity.pendingContentRouteId)

            val collection = "https://links.ratatoskr.test/collections/research"
            mainActivity.acceptIntent(Intent(Intent.ACTION_VIEW, Uri.parse(collection)))
            assertEquals("research", mainActivity.pendingContentRouteId)

            val repository = "https://links.ratatoskr.test/repos/ratatoskr/mobile"
            mainActivity.acceptIntent(Intent(Intent.ACTION_VIEW, Uri.parse(repository)))
            assertEquals("ratatoskr/mobile", mainActivity.pendingContentRouteId)

            val before = mainActivity.pendingContentRouteId
            assertFalse(mainActivity.acceptLibraryLink("https://foreign.example/analyses/$ID"))
            assertFalse(mainActivity.acceptLibraryLink("https://links.ratatoskr.test/analyses/$ID?query=private"))
            assertEquals(before, mainActivity.pendingContentRouteId)
            assertTrue(mainActivity.acceptLibraryLink("ratatoskr://library/analyses/$ID"))
        }
    }

    private companion object {
        const val ID = "abcdef01-0000-4000-8000-000000000001"
    }
}
