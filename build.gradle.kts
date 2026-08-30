import groovy.json.JsonOutput
import groovy.json.JsonSlurper

buildscript {
    dependencies {
        classpath("com.android.tools.build:gradle:8.13.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.11")
    }
}

plugins {
    base
}

group = "com.ratatoskr.mobile"
version = "1.0.0"

tasks.register("ktlintCheck") {
    group = "verification"
    description = "Run Kotlin formatting and style checks for handwritten mobile sources."
    dependsOn(":androidApp:ktlintCheck", ":shared:ktlintCheck")
}

val openApiGenerator by configurations.creating

dependencies {
    openApiGenerator("org.openapitools:openapi-generator-cli:7.25.0")
}

val platformOpenApi = providers.gradleProperty("platformOpenApi").orElse(
    layout.projectDirectory.file("contracts/platform-openapi.json").asFile.absolutePath,
)
val contractOutput = providers.gradleProperty("contractOutput").orElse(
    layout.projectDirectory.dir("shared").asFile.absolutePath,
)
val generatorInput = layout.buildDirectory.file("contracts/platform-openapi.generator.json")

val prepareContractGeneratorInput by tasks.registering {
    group = "contracts"
    description = "Prepare a validated OpenAPI Generator input without changing the pinned document."
    inputs.file(platformOpenApi)
    outputs.file(generatorInput)

    doLast {
        @Suppress("UNCHECKED_CAST")
        val document = JsonSlurper().parse(file(platformOpenApi.get())) as MutableMap<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val info = document.getValue("info") as MutableMap<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val license = info.getValue("license") as MutableMap<String, Any?>
        if (license["identifier"] == null) {
            license["identifier"] = license.getValue("name")
        }
        @Suppress("UNCHECKED_CAST")
        val components = document.getValue("components") as MutableMap<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val schemas = components.getValue("schemas") as MutableMap<String, Any?>
        for (schemaValue in schemas.values) {
            @Suppress("UNCHECKED_CAST")
            val schema = schemaValue as? MutableMap<String, Any?> ?: continue
            val variants = schema["oneOf"] as? List<*> ?: continue
            val enumValues = variants.map { variant ->
                val constant = variant as? Map<*, *>
                constant?.takeIf { it["type"] == "string" }?.get("const") as? String
            }
            if (enumValues.isNotEmpty() && enumValues.all { it != null }) {
                schema.remove("oneOf")
                schema["type"] = "string"
                schema["enum"] = enumValues
            }
        }
        val output = generatorInput.get().asFile
        output.parentFile.mkdirs()
        output.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(document)) + "\n")
    }
}

tasks.register<JavaExec>("generateContracts") {
    group = "contracts"
    description = "Generate committed Kotlin models from the pinned Platform OpenAPI document."
    dependsOn(prepareContractGeneratorInput)
    classpath = openApiGenerator
    mainClass = "org.openapitools.codegen.OpenAPIGenerator"
    args(
        "generate",
        "--generator-name",
        "kotlin",
        "--input-spec",
        generatorInput.get().asFile.absolutePath,
        "--output",
        contractOutput.get(),
        "--config",
        layout.projectDirectory.file("contracts/openapi-generator-config.json").asFile.absolutePath,
        "--global-property",
        "models,modelDocs=false,modelTests=false,apis=false,supportingFiles=false",
    )

    doLast {
        val generatedModels = file(contractOutput.get())
            .resolve("src/commonMain/kotlin/com/ratatoskr/mobile/api/generated/model")
        generatedModels.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { model ->
                val generated = model.readText()
                val normalized = generated.replace(
                    " : kotlin.collections.HashMap<String, kotlinx.serialization.json.JsonElement>()() {",
                    " {",
                ).trimEnd() + "\n"
                if (normalized != generated) {
                    model.writeText(normalized)
                }
            }
        check(generatedModels.walkTopDown().none { it.isFile && ">()() {" in it.readText() }) {
            "OpenAPI Generator emitted an invalid additionalProperties collection supertype"
        }
    }
}

val blobTransferContracts = providers.gradleProperty("blobTransferContracts").orElse(
    layout.projectDirectory.dir("contracts/blob-transfer").asFile.absolutePath,
)
val blobTransferOutput = providers.gradleProperty("blobTransferOutput").orElse(
    layout.projectDirectory
        .dir("shared/src/commonMain/kotlin/com/ratatoskr/mobile/transfer/generated")
        .asFile.absolutePath,
)

tasks.register("generateBlobTransferContracts") {
    group = "contracts"
    description = "Generate Kotlin wire models from the pinned blob-transfer JSON Schemas."
    inputs.files(fileTree(file(blobTransferContracts.get()).resolve("schemas")) { include("*.json") })
    outputs.dir(blobTransferOutput)

    doLast {
        val schemaRoot = file(blobTransferContracts.get()).resolve("schemas")
        fun schema(fileName: String): Map<String, Any?> {
            @Suppress("UNCHECKED_CAST")
            return JsonSlurper().parse(schemaRoot.resolve(fileName)) as Map<String, Any?>
        }

        val sourceSchemas =
            linkedMapOf(
                "UploadSessionRequest" to schema("upload-session-request.v1.schema.json"),
                "UploadSessionOpened" to schema("upload-session-opened.v1.schema.json"),
                "UploadChunkReceipt" to schema("upload-chunk-receipt.v1.schema.json"),
                "UploadStatusResponse" to schema("upload-status-response.v1.schema.json"),
                "UploadFinalizeRequest" to schema("upload-finalize-request.v1.schema.json"),
                "UploadCompletionOutcome" to schema("upload-completion-outcome.v1.schema.json"),
            )
        sourceSchemas.forEach { (expectedTitle, document) ->
            check(document["title"] == expectedTitle) {
                "Schema title drifted: expected $expectedTitle, got ${document["title"]}"
            }
        }

        fun camelCase(wireName: String): String =
            wireName.split('_').mapIndexed { index, part ->
                if (index == 0) part else part.replaceFirstChar(Char::uppercase)
            }.joinToString("")

        fun refName(property: Map<String, Any?>): String? =
            (property["\$ref"] as? String)?.substringAfterLast('/')

        fun kotlinType(property: Map<String, Any?>): String {
            refName(property)?.let { reference ->
                return when (reference) {
                    "BlobRef" -> "TransferBlobRef"
                    "ContentDigest" -> "TransferContentDigest"
                    "WireTimestamp" -> "kotlin.time.Instant"
                    "BlobOwner",
                    "DigestAlgorithm",
                    "DigestHex",
                    "MediaType",
                    "UploadResumptionToken",
                    "UploadSessionState",
                    -> "String"
                    else -> error("Unsupported blob-transfer schema reference: $reference")
                }
            }
            return when (property["type"] as? String) {
                "boolean" -> "Boolean"
                "integer" -> if (property["format"] == "uint64") "Long" else "Int"
                "string" -> if (property["format"] == "date-time") "kotlin.time.Instant" else "String"
                "array" -> {
                    @Suppress("UNCHECKED_CAST")
                    val items = property["items"] as? Map<String, Any?>
                        ?: error("Array schema is missing items")
                    "List<${kotlinType(items)}>"
                }
                else -> {
                    val stringConstants =
                        (property["oneOf"] as? List<*>)?.mapNotNull { variant ->
                            (variant as? Map<*, *>)?.takeIf { it["type"] == "string" }?.get("const")
                        }
                    if (!stringConstants.isNullOrEmpty()) "String"
                    else error("Unsupported blob-transfer property schema: $property")
                }
            }
        }

        data class ObjectShape(
            val properties: LinkedHashMap<String, Map<String, Any?>>,
            val required: Set<String>,
        )

        fun objectShape(schema: Map<String, Any?>): ObjectShape {
            @Suppress("UNCHECKED_CAST")
            val variants = schema["oneOf"] as? List<Map<String, Any?>>
            if (variants != null) {
                check(variants.isNotEmpty()) { "oneOf object must have variants" }
                val properties = linkedMapOf<String, Map<String, Any?>>()
                variants.forEach { variant ->
                    check(variant["type"] == "object") { "Only object unions can become wire DTOs" }
                    @Suppress("UNCHECKED_CAST")
                    val variantProperties = variant["properties"] as? Map<String, Map<String, Any?>>
                        ?: error("Object union variant is missing properties")
                    variantProperties.forEach { (name, property) ->
                        val previous = properties.putIfAbsent(name, property)
                        check(previous == null || kotlinType(previous) == kotlinType(property)) {
                            "Union property $name has incompatible variants"
                        }
                    }
                }
                val required = variants
                    .map { variant -> (variant["required"] as? List<*>)?.filterIsInstance<String>()?.toSet().orEmpty() }
                    .reduce(Set<String>::intersect)
                return ObjectShape(LinkedHashMap(properties), required)
            }

            check(schema["type"] == "object" || schema["properties"] != null) {
                "Only JSON objects can become wire DTOs"
            }
            @Suppress("UNCHECKED_CAST")
            val properties = schema["properties"] as? Map<String, Map<String, Any?>>
                ?: error("Object schema is missing properties")
            val required = (schema["required"] as? List<*>)?.filterIsInstance<String>()?.toSet().orEmpty()
            check(required.all(properties::containsKey)) { "Required field is absent from properties" }
            return ObjectShape(LinkedHashMap(properties), required)
        }

        fun renderClass(className: String, schema: Map<String, Any?>): String {
            val shape = objectShape(schema)
            val fields = shape.properties.entries.joinToString("\n") { (wireName, property) ->
                val required = wireName in shape.required
                val type = kotlinType(property) + if (required) "" else "?"
                val annotation = if (required) " @Required" else ""
                val defaultValue = if (required) "" else " = null"
                "    @SerialName(\"$wireName\")$annotation val ${camelCase(wireName)}: $type$defaultValue,"
            }
            return """|@Serializable
                      |data class $className(
                      |$fields
                      |)
                   """.trimMargin()
        }

        val packageHeader =
            """|// Generated from pinned ratatoskr-contracts JSON Schemas. DO NOT EDIT.
               |package com.ratatoskr.mobile.transfer.generated
               |
               |import kotlinx.serialization.Required
               |import kotlinx.serialization.SerialName
               |import kotlinx.serialization.Serializable
               |
            """.trimMargin()

        @Suppress("UNCHECKED_CAST")
        val completionDefinitions = sourceSchemas.getValue("UploadCompletionOutcome")["\$defs"]
            as? Map<String, Map<String, Any?>>
            ?: error("UploadCompletionOutcome definitions are missing")
        val generated =
            linkedMapOf(
                "TransferValueTypes.kt" to
                    """|$packageHeader
                       |${renderClass("TransferContentDigest", completionDefinitions.getValue("ContentDigest"))}
                       |
                       |${renderClass("TransferBlobRef", completionDefinitions.getValue("BlobRef"))}
                    """.trimMargin(),
            )
        sourceSchemas.forEach { (className, schema) ->
            generated["$className.kt"] = packageHeader + "\n" + renderClass(className, schema)
        }
        val output = file(blobTransferOutput.get())
        output.mkdirs()
        output.listFiles()?.filter { it.isFile && it.extension == "kt" }?.forEach { it.delete() }
        generated.forEach { (name, content) -> output.resolve(name).writeText(content.trimEnd() + "\n") }
    }
}
