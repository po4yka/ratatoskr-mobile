package com.ratatoskr.mobile.transfer

import com.ratatoskr.mobile.transfer.generated.UploadSessionRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BlobTransferContractTest {
    @Test
    fun declaration_derives_exact_chunk_lengths() {
        val plan = assertIs<TransferResult.Success<UploadPlan>>(UploadPlan.create(100_000, 65_536)).value

        assertEquals(2, plan.chunkCount)
        assertEquals(65_536, plan.chunkLength(0))
        assertEquals(34_464, plan.chunkLength(1))
        assertEquals(null, plan.chunkLength(2))
    }

    @Test
    fun malformed_fixture_values_fail_closed() {
        val valid =
            BlobTransferContractCodec.decodeSessionRequest(
                """{"declared_size_bytes":65536,"media_type":"application/pdf","digest":{"algorithm":"sha256","hex":"1111111111111111111111111111111111111111111111111111111111111111"},"chunk_size_bytes":65536}""",
            )
        val malformed =
            BlobTransferContractCodec.decodeSessionRequest(
                """{"declared_size_bytes":0,"media_type":"application/pdf","digest":{"algorithm":"sha256","hex":"ABC"},"chunk_size_bytes":1}""",
            )

        assertIs<TransferResult.Success<UploadSessionRequest>>(valid)
        assertEquals(TransferFailure.InvalidDeclaration, assertIs<TransferResult.Failure>(malformed).reason)
    }
}
