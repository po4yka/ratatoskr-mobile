package com.ratatoskr.mobile.github

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

enum class GithubActionMode(
    val wireName: String,
) {
    Metadata("metadata"),
    Track("track"),
    Star("star"),
}

data class GithubRepositoryTarget(
    val numericId: Long,
    val fullName: String,
    val canonicalUrl: String,
)

data class GithubRepositoryPreview(
    val target: GithubRepositoryTarget,
    val description: String?,
    val stargazerCount: Long,
    val primaryLanguage: String?,
    val accountRef: String?,
    val availableActions: Set<GithubActionMode>,
)

enum class GithubActionAggregate {
    Succeeded,
    Partial,
    Failed,
}

enum class GithubComponentStatus {
    Succeeded,
    AlreadyApplied,
    Accepted,
    Refused,
    Failed,
    Skipped,
}

enum class GithubActionReason {
    NotAuthorized,
    AccountRequired,
    AccountSelectionRequired,
    ScopeMissing,
    TargetChanged,
    DependencyUnavailable,
    ProviderUnavailable,
    OutcomeUnknown,
    CatalogPersistenceFailed,
    PolicyPublicationFailed,
    NotApplicable,
    PrerequisiteFailed,
}

data class GithubComponentOutcome(
    val status: GithubComponentStatus,
    val reason: GithubActionReason? = null,
)

data class GithubActionResult(
    val aggregate: GithubActionAggregate,
    val metadata: GithubComponentOutcome,
    val providerStar: GithubComponentOutcome,
    val desiredBackup: GithubComponentOutcome,
)

data class GithubActionPresentation(
    val aggregateLabel: String,
    val metadataLabel: String,
    val providerStarLabel: String,
    val desiredBackupLabel: String,
)

object GithubContractCodec {
    fun encodePreviewRequest(canonicalUrl: String): String? =
        canonicalUrl.takeIf(CANONICAL_URL::matches)?.let { value ->
            buildJsonObject { put("repository_url", value) }.toString()
        }

    fun encodeActionRequest(request: GithubActionRequest): String? {
        if (
            request.target.numericId <= 0 ||
            !FULL_NAME.matches(request.target.fullName) ||
            !CANONICAL_URL.matches(request.target.canonicalUrl) ||
            !CONFIRMATION_EVIDENCE.matches(request.confirmationEvidenceRef) ||
            !IDEMPOTENCY_KEY.matches(request.idempotencyKey) ||
            (request.mode == GithubActionMode.Star) != (request.accountRef != null) ||
            (request.accountRef != null && !ACCOUNT_REF.matches(request.accountRef))
        ) {
            return null
        }
        return buildJsonObject {
            put("mode", request.mode.wireName)
            put(
                "target",
                buildJsonObject {
                    put("github_repository_numeric_id", request.target.numericId)
                    put("repository_full_name", request.target.fullName)
                    put("canonical_url", request.target.canonicalUrl)
                },
            )
            request.accountRef?.let { put("account_ref", it) }
            put("confirmation_evidence_ref", request.confirmationEvidenceRef)
            put("idempotency_key", request.idempotencyKey)
        }.toString()
    }

    fun decodePreview(json: String): GithubRepositoryPreview? {
        val root = parseObject(json) ?: return null
        if (!root.hasExactShape(REQUIRED_PREVIEW_KEYS, OPTIONAL_PREVIEW_KEYS)) return null
        val target = root["target"].objectOrNull()?.decodeTarget() ?: return null
        val description = root.optionalString("description") ?: if (root.hasNonNull("description")) return null else null
        val language = root.optionalString("primary_language") ?: if (root.hasNonNull("primary_language")) return null else null
        val account = root.optionalString("account_ref") ?: if (root.hasNonNull("account_ref")) return null else null
        val stars = root["stargazer_count"].longOrNull() ?: return null
        val actions = root["available_actions"].actionModes() ?: return null
        if (
            stars < 0 ||
            (description != null && !DESCRIPTION.matches(description)) ||
            (language != null && !LANGUAGE.matches(language)) ||
            (account != null && !ACCOUNT_REF.matches(account))
        ) {
            return null
        }
        return GithubRepositoryPreview(target, description, stars, language, account, actions)
    }

    fun decodeActionResult(json: String): GithubActionResult? {
        val root = parseObject(json) ?: return null
        if (root.keys != ACTION_RESULT_KEYS) return null
        val aggregate = root["aggregate"].stringOrNull()?.aggregate() ?: return null
        val metadata = root["metadata"].objectOrNull()?.component(ComponentKind.Metadata) ?: return null
        val providerStar = root["provider_star"].objectOrNull()?.component(ComponentKind.ProviderStar) ?: return null
        val desiredBackup = root["desired_backup"].objectOrNull()?.component(ComponentKind.DesiredBackup) ?: return null
        val derived = deriveAggregate(metadata, providerStar, desiredBackup)
        if (aggregate != derived) return null
        return GithubActionResult(aggregate, metadata, providerStar, desiredBackup)
    }

    private fun parseObject(value: String): JsonObject? =
        try {
            Json.parseToJsonElement(value).objectOrNull()
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun JsonObject.decodeTarget(): GithubRepositoryTarget? {
        if (keys != TARGET_KEYS) return null
        val numericId = this["github_repository_numeric_id"].longOrNull() ?: return null
        val fullName = this["repository_full_name"].stringOrNull() ?: return null
        val canonicalUrl = this["canonical_url"].stringOrNull() ?: return null
        if (numericId <= 0 || !FULL_NAME.matches(fullName) || !CANONICAL_URL.matches(canonicalUrl)) return null
        return GithubRepositoryTarget(numericId, fullName, canonicalUrl)
    }

    private fun JsonObject.component(kind: ComponentKind): GithubComponentOutcome? {
        val status = this["status"].stringOrNull()?.componentStatus() ?: return null
        if (status in kind.positiveStatuses) {
            return if (keys == setOf("status")) GithubComponentOutcome(status) else null
        }
        if (status !in NEGATIVE_STATUSES || keys != setOf("status", "reason")) return null
        val reason = this["reason"].stringOrNull()?.reason() ?: return null
        if (reason !in status.allowedReasons) return null
        return GithubComponentOutcome(status, reason)
    }

    private fun deriveAggregate(
        metadata: GithubComponentOutcome,
        providerStar: GithubComponentOutcome,
        desiredBackup: GithubComponentOutcome,
    ): GithubActionAggregate {
        val statuses = listOf(metadata.status, providerStar.status, desiredBackup.status)
        val positive = statuses.any { it in POSITIVE_STATUSES }
        val negative = statuses.any { it == GithubComponentStatus.Refused || it == GithubComponentStatus.Failed }
        return when {
            positive && negative -> GithubActionAggregate.Partial
            positive -> GithubActionAggregate.Succeeded
            else -> GithubActionAggregate.Failed
        }
    }

    private enum class ComponentKind(
        val positiveStatuses: Set<GithubComponentStatus>,
    ) {
        Metadata(setOf(GithubComponentStatus.Succeeded, GithubComponentStatus.AlreadyApplied)),
        ProviderStar(setOf(GithubComponentStatus.Succeeded, GithubComponentStatus.AlreadyApplied)),
        DesiredBackup(setOf(GithubComponentStatus.Accepted, GithubComponentStatus.AlreadyApplied)),
    }

    private val GithubComponentStatus.allowedReasons: Set<GithubActionReason>
        get() =
            when (this) {
                GithubComponentStatus.Refused -> REFUSAL_REASONS
                GithubComponentStatus.Failed -> FAILURE_REASONS
                GithubComponentStatus.Skipped -> SKIP_REASONS
                else -> emptySet()
            }

    private val REQUIRED_PREVIEW_KEYS = setOf("target", "stargazer_count", "available_actions")
    private val OPTIONAL_PREVIEW_KEYS = setOf("description", "primary_language", "account_ref")
    private val TARGET_KEYS = setOf("github_repository_numeric_id", "repository_full_name", "canonical_url")
    private val ACTION_RESULT_KEYS = setOf("aggregate", "metadata", "provider_star", "desired_backup")
    private val POSITIVE_STATUSES =
        setOf(GithubComponentStatus.Succeeded, GithubComponentStatus.AlreadyApplied, GithubComponentStatus.Accepted)
    private val NEGATIVE_STATUSES =
        setOf(GithubComponentStatus.Refused, GithubComponentStatus.Failed, GithubComponentStatus.Skipped)
    private val REFUSAL_REASONS =
        setOf(
            GithubActionReason.NotAuthorized,
            GithubActionReason.AccountRequired,
            GithubActionReason.AccountSelectionRequired,
            GithubActionReason.ScopeMissing,
            GithubActionReason.TargetChanged,
        )
    private val FAILURE_REASONS =
        setOf(
            GithubActionReason.DependencyUnavailable,
            GithubActionReason.ProviderUnavailable,
            GithubActionReason.OutcomeUnknown,
            GithubActionReason.CatalogPersistenceFailed,
            GithubActionReason.PolicyPublicationFailed,
        )
    private val SKIP_REASONS = setOf(GithubActionReason.NotApplicable, GithubActionReason.PrerequisiteFailed)
    private val ACCOUNT_REF = Regex("^github-account:[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$")
    private val FULL_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9_.-]{0,99}/[A-Za-z0-9][A-Za-z0-9_.-]{0,99}$")
    private val CANONICAL_URL =
        Regex("^https://github\\.com/[A-Za-z0-9][A-Za-z0-9_.-]{0,99}/[A-Za-z0-9][A-Za-z0-9_.-]{0,99}$")
    private val DESCRIPTION = Regex("^[^\\u0000-\\u001f\\u007f]{1,1024}$")
    private val LANGUAGE = Regex("^[A-Za-z0-9][A-Za-z0-9+.# -]{0,63}$")
    private val CONFIRMATION_EVIDENCE = Regex("^[a-z][a-z0-9_-]{0,31}:[A-Za-z0-9][A-Za-z0-9._~:@+-]{0,127}$")
    private val IDEMPOTENCY_KEY = Regex("^[A-Za-z0-9][A-Za-z0-9._~:@+-]{0,127}$")
}

fun GithubActionResult.present(): GithubActionPresentation =
    GithubActionPresentation(
        aggregateLabel = aggregate.name,
        metadataLabel = metadata.label("Metadata"),
        providerStarLabel = providerStar.label("GitHub star"),
        desiredBackupLabel =
            if (desiredBackup.status == GithubComponentStatus.Accepted) {
                "Desired backup policy accepted for publication"
            } else {
                desiredBackup.label("Desired backup")
            },
    )

private fun GithubComponentOutcome.label(component: String): String =
    buildString {
        append(component)
        append(": ")
        append(status.name)
        reason?.let {
            append(" (")
            append(it.name)
            append(")")
        }
    }

private fun JsonObject.hasExactShape(
    required: Set<String>,
    optional: Set<String>,
): Boolean = required.all(keys::contains) && keys.all { it in required || it in optional }

private fun JsonObject.hasNonNull(name: String): Boolean = this[name] != null && this[name] !is JsonNull

private fun JsonObject.optionalString(name: String): String? =
    when (val element = this[name]) {
        null, JsonNull -> null
        else -> element.stringOrNull()
    }

private fun JsonElement?.objectOrNull(): JsonObject? = this as? JsonObject

private fun JsonElement?.longOrNull(): Long? = (this as? JsonPrimitive)?.longOrNull

private fun JsonElement?.stringOrNull(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.content.takeIf { primitive.isString }
}

private fun JsonElement?.actionModes(): Set<GithubActionMode>? {
    val values = (this as? JsonArray)?.map { it.stringOrNull() ?: return null } ?: return null
    if (values.size > GithubActionMode.entries.size || values.size != values.toSet().size) return null
    return values.map { wire -> GithubActionMode.entries.firstOrNull { it.wireName == wire } ?: return null }.toSet()
}

private fun String.aggregate(): GithubActionAggregate? =
    when (this) {
        "succeeded" -> GithubActionAggregate.Succeeded
        "partial" -> GithubActionAggregate.Partial
        "failed" -> GithubActionAggregate.Failed
        else -> null
    }

private fun String.componentStatus(): GithubComponentStatus? =
    when (this) {
        "succeeded" -> GithubComponentStatus.Succeeded
        "already_applied" -> GithubComponentStatus.AlreadyApplied
        "accepted" -> GithubComponentStatus.Accepted
        "refused" -> GithubComponentStatus.Refused
        "failed" -> GithubComponentStatus.Failed
        "skipped" -> GithubComponentStatus.Skipped
        else -> null
    }

private fun String.reason(): GithubActionReason? =
    when (this) {
        "not_authorized" -> GithubActionReason.NotAuthorized
        "account_required" -> GithubActionReason.AccountRequired
        "account_selection_required" -> GithubActionReason.AccountSelectionRequired
        "scope_missing" -> GithubActionReason.ScopeMissing
        "target_changed" -> GithubActionReason.TargetChanged
        "dependency_unavailable" -> GithubActionReason.DependencyUnavailable
        "provider_unavailable" -> GithubActionReason.ProviderUnavailable
        "outcome_unknown" -> GithubActionReason.OutcomeUnknown
        "catalog_persistence_failed" -> GithubActionReason.CatalogPersistenceFailed
        "policy_publication_failed" -> GithubActionReason.PolicyPublicationFailed
        "not_applicable" -> GithubActionReason.NotApplicable
        "prerequisite_failed" -> GithubActionReason.PrerequisiteFailed
        else -> null
    }
