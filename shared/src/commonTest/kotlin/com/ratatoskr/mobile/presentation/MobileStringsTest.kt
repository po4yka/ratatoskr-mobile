package com.ratatoskr.mobile.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MobileStringsTest {
    @Test
    fun english_and_russian_cover_every_new_state_and_action() {
        MobileStringKey.entries.forEach { key ->
            val english = MobileStrings.value(key, MobileLocale.English)
            val russian = MobileStrings.value(key, MobileLocale.Russian)
            assertTrue(english.isNotBlank(), "missing English $key")
            assertTrue(russian.isNotBlank(), "missing Russian $key")
        }
        assertNotEquals(
            MobileStrings.value(MobileStringKey.NotificationsIntegrationPending, MobileLocale.English),
            MobileStrings.value(MobileStringKey.NotificationsIntegrationPending, MobileLocale.Russian),
        )
        assertEquals(
            MobileStrings.value(MobileStringKey.SearchAction, MobileLocale.English),
            MobileStrings.value(MobileStringKey.SearchAction, MobileLocale.English),
        )
    }
}
