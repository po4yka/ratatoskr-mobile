package com.ratatoskr.mobile.identity

import kotlin.test.Test
import kotlin.test.assertFailsWith

class CredentialRecordCodecTest {
    @Test
    fun corrupt_record_is_rejected_without_partial_credentials() {
        assertFailsWith<SecureCredentialStorageException> {
            CredentialRecordCodec.decode("not a credential record".encodeToByteArray())
        }
    }
}
