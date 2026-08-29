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
class LibraryDeepLinkIntentTest {
    @get:Rule
    val activity = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun cold_and_warm_links_select_the_same_shared_destination() {
        val link = "ratatoskr://library/analyses/$ID"
        activity.scenario.onActivity { mainActivity ->
            mainActivity.handleIntent(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
            assertEquals(ID, mainActivity.pendingContentRouteId)
            mainActivity.acceptIntent(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
            assertEquals(ID, mainActivity.pendingContentRouteId)
        }
    }

    @Test
    fun invalid_external_link_does_not_change_route() {
        val accepted = "ratatoskr://library/social/x/$ID"
        activity.scenario.onActivity { mainActivity ->
            assertTrue(mainActivity.acceptLibraryLink(accepted))
            val before = mainActivity.pendingContentRouteId
            assertFalse(mainActivity.acceptLibraryLink("ratatoskr://library/social/facebook/$ID"))
            assertEquals(before, mainActivity.pendingContentRouteId)
        }
    }

    @Test
    fun operation_intent_replaces_stale_library_route() {
        val link = "ratatoskr://library/analyses/$ID"
        activity.scenario.onActivity { mainActivity ->
            assertTrue(mainActivity.acceptLibraryLink(link))

            mainActivity.acceptIntent(
                Intent(MainActivity.ACTION_VIEW_OPERATION)
                    .putExtra(MainActivity.EXTRA_OPERATION_ID, OPERATION_ID),
            )

            assertEquals(OPERATION_ID, mainActivity.pendingOperationId)
            assertEquals(null, mainActivity.pendingContentRouteId)
        }
    }

    private companion object {
        const val ID = "abcdef01-0000-4000-8000-000000000001"
        const val OPERATION_ID = "abcdef01-0000-4000-8000-000000000002"
    }
}
