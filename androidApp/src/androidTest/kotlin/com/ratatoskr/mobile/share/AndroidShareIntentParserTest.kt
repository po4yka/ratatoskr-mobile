package com.ratatoskr.mobile.share

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidShareIntentParserTest {
    private val parser = AndroidShareIntentParser()

    @Test
    fun single_url_is_staged_without_enqueue() {
        val result = parser.parse(shareText("https://example.test/article"))

        assertTrue(result is ShareIntakeResult.Accepted)
        val accepted = result as ShareIntakeResult.Accepted
        assertEquals(
            ShareIntake.Url(
                originalText = "https://example.test/article",
                url = "https://example.test/article",
            ),
            accepted.intake,
        )
    }

    @Test
    fun title_and_one_url_preserve_original_text() {
        val original = "An article title\nhttps://example.test/read?id=7"

        val result = parser.parse(shareText(original))

        assertTrue(result is ShareIntakeResult.Accepted)
        val accepted = result as ShareIntakeResult.Accepted
        assertTrue(accepted.intake is ShareIntake.Url)
        val intake = accepted.intake as ShareIntake.Url
        assertEquals(original, intake.originalText)
        assertEquals("https://example.test/read?id=7", intake.url)
    }

    @Test
    fun plain_text_is_previewed_as_contract_unavailable() {
        val result = parser.parse(shareText("A bounded selection without a link"))

        assertTrue(result is ShareIntakeResult.Accepted)
        val accepted = result as ShareIntakeResult.Accepted
        assertTrue(accepted.intake is ShareIntake.UnsupportedText)
        val intake = accepted.intake as ShareIntake.UnsupportedText
        assertEquals("A bounded selection without a link", intake.originalText)
    }

    @Test
    fun hostile_or_ambiguous_intents_are_rejected() {
        val cases =
            listOf(
                Intent(Intent.ACTION_VIEW).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "https://example.test") to
                    ShareIntakeRejection.UnsupportedAction,
                Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_TEXT, "https://example.test") to
                    ShareIntakeRejection.UnsupportedMimeType,
                shareText("") to ShareIntakeRejection.MissingText,
                shareText("x".repeat(100_001)) to ShareIntakeRejection.OversizedText,
                shareText("ftp://example.test/archive") to ShareIntakeRejection.UnsupportedScheme,
                shareText("https://one.test and https://two.test") to ShareIntakeRejection.MultipleUrls,
            )

        cases.forEach { (intent, expected) ->
            assertEquals(ShareIntakeResult.Rejected(expected), parser.parse(intent))
        }
    }

    private fun shareText(value: String): Intent =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, value)
}
