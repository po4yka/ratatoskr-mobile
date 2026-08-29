package com.ratatoskr.mobile.api

import com.ratatoskr.mobile.api.generated.model.CapabilityDocument
import com.ratatoskr.mobile.api.generated.model.LibraryPage
import com.ratatoskr.mobile.api.generated.model.OperationStatus
import com.ratatoskr.mobile.api.generated.model.ReadState
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class GeneratedContractSmokeTest {
    private val json = Json

    @Test
    fun capability_document_round_trips_synthetic_public_response() {
        val payload =
            """
            {
              "api_version": "v1",
              "capabilities": ["capture.submit", "library.read"],
              "minimum_client_versions": {
                "mobile": "1.0.0",
                "web": "1.0.0"
              },
              "services": [
                {
                  "document": {
                    "capabilities": ["capture.submit"],
                    "revision": 7
                  },
                  "service": "capture",
                  "stale": false,
                  "observed_at": "2026-08-28T00:00:00Z"
                }
              ]
            }
            """.trimIndent()

        val decoded = json.decodeFromString<CapabilityDocument>(payload)

        assertEquals("v1", decoded.apiVersion)
        assertEquals(listOf("capture.submit", "library.read"), decoded.capabilities)
        assertEquals("1.0.0", decoded.minimumClientVersions.mobile)
        assertEquals("capture", decoded.services.single().service)
        assertFalse(decoded.services.single().stale)
        assertEquals(decoded, json.decodeFromString(json.encodeToString(decoded)))
    }

    @Test
    fun closed_operation_status_rejects_unknown_value() {
        assertEquals(OperationStatus.ACCEPTED, json.decodeFromString<OperationStatus>("\"accepted\""))
        assertFailsWith<SerializationException> {
            json.decodeFromString<OperationStatus>("\"future_status\"")
        }
    }

    @Test
    fun library_contract_models_round_trip() {
        val payload =
            """
            {
              "items": [{
                "analysis_id": "00000000-0000-4000-8000-000000000001",
                "document_id": "00000000-0000-4000-8000-000000000002",
                "title": "Synthetic analysis",
                "read_state": "unread"
              }],
              "limit": 25,
              "offset": 0,
              "has_more": false
            }
            """.trimIndent()

        val decoded = json.decodeFromString<LibraryPage>(payload)

        assertEquals(ReadState.UNREAD, decoded.items.single().readState)
        assertEquals("Synthetic analysis", decoded.items.single().title)
        assertFalse(decoded.hasMore)
        assertEquals(decoded, json.decodeFromString(json.encodeToString(decoded)))
    }
}
