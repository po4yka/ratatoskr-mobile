import groovy.json.JsonOutput
import groovy.json.JsonSlurper

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
