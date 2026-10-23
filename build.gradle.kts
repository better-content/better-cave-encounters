import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipFile

plugins {
    idea
    jacoco
    `maven-publish`
    id("net.minecraftforge.gradle") version "6.0.54"
    id("org.spongepowered.mixin") version "0.7.38"
}

group = property("mod_group_id") as String
version = property("mod_version") as String
base { archivesName.set(property("artifact_name") as String) }

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    withSourcesJar()
}

// Same owning mod/package at runtime, but never part of the shipping main source set/JAR.
val nativeGameTest = sourceSets.create("nativeGameTest") {
    compileClasspath += sourceSets.main.get().output + configurations.compileClasspath.get()
    runtimeClasspath += output + compileClasspath
}

val worldGameTest = sourceSets.create("worldGameTest") {
    // Keep the authored historical descriptor, but never expose a second JPMS mod.
    resources.exclude("META-INF/mods.toml", "data/better_cave_encounters_world_tests/loot_tables/policy_sentinel.json")
    compileClasspath += sourceSets.main.get().output + configurations.compileClasspath.get()
    runtimeClasspath += output + compileClasspath
}

val geologyGameTest = sourceSets.create("geologyGameTest") {
    java.srcDir("src/worldGameTest/java")
    resources.srcDir("src/worldGameTest/resources")
    resources.exclude("META-INF/mods.toml")
    compileClasspath += sourceSets.main.get().output + configurations.compileClasspath.get()
    runtimeClasspath += output + compileClasspath
}
val rootContractSources = sourceSets.create("rootContractTest") {
    java.srcDir("src/worldGameTest/java")
    java.include("**/CatalogueRootAccount.java", "**/CatalogueRootAccountTest.java", "**/NativeInitialRootAbiTest.java", "**/FixtureGlmAbiTest.java")
    compileClasspath += sourceSets.main.get().output + sourceSets.test.get().compileClasspath
    runtimeClasspath += output + compileClasspath + configurations.testRuntimeClasspath.get() + worldGameTest.output
}
val rootContractTest = tasks.register<Test>("rootContractTest") {
    testClassesDirs = rootContractSources.output.classesDirs
    classpath = rootContractSources.runtimeClasspath
    useJUnitPlatform()
}
val qualification = JsonSlurper().parse(file("src/worldGameTest/resources/geological-qualification.json")) as Map<*, *>
val geologicalArchives = (qualification["archives"] as List<*>).map { it as Map<*, *> }
val geologicalPackInputs = (qualification["pack_inputs"] as List<*>).map { it.toString() }
val geologicalPackRoot = providers.environmentVariable("BC_PACK_ROOT").orNull?.let(::file)
    ?: file("../../better-content-modpack")
val pinnedGeologicalArchives = configurations.create("pinnedGeologicalArchives") { isTransitive = false }
val worldProfileInputFile = file("src/worldGameTest/world-profile-inputs.json")
val worldProfileInputs = JsonSlurper().parse(worldProfileInputFile) as Map<*, *>
val worldDeepVoid = worldProfileInputs["deep_void"] as Map<*, *>
val pinnedWorldDeepVoid = configurations.create("pinnedWorldDeepVoid") { isTransitive = false }
rootContractTest.configure {
    dependsOn(tasks.processResources, worldGameTest.classesTaskName)
    doFirst {
        systemProperty("bc.deepVoidOriginalArchive", pinnedWorldDeepVoid.singleFile.absolutePath)
        systemProperty("bc.initialRootRefmap", layout.buildDirectory.file("resources/main/better_cave_encounters.refmap.json").get().asFile.absolutePath)
    }
}
val nativeFixtureId = providers.gradleProperty("bc.nativeFixtureId")
    .orElse("${Instant.now().toEpochMilli()}-${UUID.randomUUID().toString().take(8)}").get()
require(nativeFixtureId.matches(Regex("[A-Za-z0-9_-]{1,80}"))) { "Unsafe native fixture ID" }
val gameTestRun = file("run-gametest/$nativeFixtureId")
val worldTestRun = file("run-world-gametest/$nativeFixtureId")
val geologyTestRun = file("run-geology-gametest/$nativeFixtureId")

minecraft {
    mappings("official", property("minecraft_version") as String)
    copyIdeResources = true
    runs {
        configureEach {
            workingDirectory(project.file("run"))
            property("forge.logging.console.level", "info")
            mods {
                create(property("mod_id") as String) { source(sourceSets.main.get()) }
            }
        }
        create("client")
        create("server") { arg("--nogui") }
        val gameTestServer = create("gameTestServer") {
            mods { getByName(property("mod_id") as String) { source(nativeGameTest) } }
            workingDirectory(gameTestRun)
            property("forge.enableGameTest", "true")
            property("forge.gameTestServer", "true")
            property("forge.enabledGameTestNamespaces", property("mod_id") as String)
            property("mixin.env.remapRefMap", "true")
            property("mixin.env.refMapRemappingFile", file("build/createSrgToMcp/output.srg").absolutePath)
            arg("--nogui")
        }
        create("worldGameTestServer") {
            parent(gameTestServer)
            workingDirectory(worldTestRun)
            property("forge.enableGameTest", "true")
            property("forge.gameTestServer", "true")
            property("forge.enabledGameTestNamespaces", "better_cave_encounters_world_tests")
            property("mixin.env.remapRefMap", "true")
            property("mixin.env.refMapRemappingFile", file("build/createSrgToMcp/output.srg").absolutePath)
            arg("--nogui")
            mods {
                getByName(property("mod_id") as String) { source(worldGameTest) }
            }
        }
        create("geologyGameTestServer") {
            parent(gameTestServer)
            workingDirectory(geologyTestRun)
            // Forge filters the explicit template namespace, not the logical profile label.
            property("forge.enabledGameTestNamespaces", "better_cave_encounters_world_tests")
            property("bc.geologyQualification", "true")
            property("mixin.env.remapRefMap", "true")
            property("mixin.env.refMapRemappingFile", file("build/createSrgToMcp/output.srg").absolutePath)
            arg("--nogui")
            mods { getByName(property("mod_id") as String) { source(geologyGameTest) } }
        }
    }
}

// CI and fresh-release builds provide verified runtime JARs explicitly.
// Ordinary local builds retain the canonical sibling build/libs convention.
fun betterContentJar(repository: String, artifact: String): java.io.File {
    val directory = providers.environmentVariable("BC_CUSTOM_MOD_JAR_DIR").orNull
    require(directory == null || directory.isNotBlank()) { "BC_CUSTOM_MOD_JAR_DIR must not be blank" }
    val jar = if (directory == null) file("../$repository/build/libs/$artifact") else file(directory).resolve(artifact)
    require(jar.isFile) {
        "Missing Better Content provider $artifact at $jar; prepare BC_CUSTOM_MOD_JAR_DIR or build $repository first"
    }
    return jar
}

repositories {
    maven("https://maven.minecraftforge.net")
    maven("https://www.cursemaven.com") { content { includeGroup("curse.maven") } }
    maven("https://maven.teamresourceful.com/repository/maven-public") {
        content {
            includeGroup("com.teamresourceful")
            includeGroup("com.teamresourceful.resourcefullib")
            includeGroup("com.teamresourceful.resourcefulconfig")
        }
    }
    ivy("https://cdn.modrinth.com/data/Lq6ojcWv/versions") {
        name = "endermanOverhaulWorldTest"
        patternLayout { artifact("[revision]/[artifact].[ext]") }
        metadataSources { artifact() }
        content { includeGroup("worldtest.enderman") }
    }
    ivy {
        name = "downedPlayerRevivalLocal"
        url = uri(betterContentJar("better-deaths-door", "better-deaths-door-1.0.0.jar").parentFile)
        patternLayout { artifact("[artifact]-[revision].[ext]") }
        metadataSources { artifact() }
        content { includeGroup("bettercontent.local") }
    }
    ivy {
        name = "betterContentFixesLocal"
        url = uri(betterContentJar("better-compat-fixes", "better-compat-fixes-0.1.9.jar").parentFile)
        patternLayout { artifact("[artifact]-[revision].[ext]") }
        metadataSources { artifact() }
        content { includeGroup("bettercontent.local") }
    }
    ivy {
        name = "betterGameplayNoticesLocal"
        url = uri(betterContentJar("better-gameplay-notices", "better-gameplay-notices-1.0.0.jar").parentFile)
        patternLayout { artifact("[artifact]-[revision].[ext]") }
        metadataSources { artifact() }
        content { includeGroup("bettercontent.notices.local") }
    }
    mavenCentral()
}

dependencies {
    minecraft("net.minecraftforge:forge:${property("minecraft_version")}-${property("forge_version")}")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    compileOnly(fg.deobf("bettercontent.local:better-deaths-door:1.0.0"))
    compileOnly(fg.deobf("bettercontent.local:better-compat-fixes:0.1.9"))
    // GameTestServer and local dev runs need the declared hard dependencies at runtime.
    runtimeOnly(fg.deobf("bettercontent.local:better-deaths-door:1.0.0"))
    runtimeOnly(fg.deobf("bettercontent.local:better-compat-fixes:0.1.9"))
    runtimeOnly(fg.deobf("bettercontent.notices.local:better-gameplay-notices:1.0.0"))
    runtimeOnly("io.github.llamalad7:mixinextras-forge:0.5.4")
    compileOnly("io.github.llamalad7:mixinextras-common:0.5.4")
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
    annotationProcessor("io.github.llamalad7:mixinextras-common:0.5.4")
    testCompileOnly("io.github.llamalad7:mixinextras-common:0.5.4")
    testCompileOnly(fg.deobf("bettercontent.local:better-compat-fixes:0.1.9"))
    testRuntimeOnly(fg.deobf("bettercontent.local:better-compat-fixes:0.1.9"))

    add("worldGameTestRuntimeOnly", fg.deobf("curse.maven:born-in-chaos-686437:7917933"))
    add("worldGameTestRuntimeOnly", fg.deobf("curse.maven:goety-586095:8087429"))
    // All six authored ecologies, including the current pinned Deep Void, are mandatory here.
    add("worldGameTestRuntimeOnly", fg.deobf(worldDeepVoid["coordinate"].toString()))
    add(pinnedWorldDeepVoid.name, worldDeepVoid["coordinate"].toString())
    add("worldGameTestRuntimeOnly", fg.deobf("curse.maven:quark-243121:6427817"))
    add("worldGameTestRuntimeOnly", fg.deobf("curse.maven:zeta-968868:7335229"))
    add("worldGameTestRuntimeOnly", fg.deobf("curse.maven:tinkers-construct-74072:7449219"))
    add("worldGameTestRuntimeOnly", fg.deobf("curse.maven:mantle-74924:7563777"))
    add("worldGameTestRuntimeOnly", fg.deobf("curse.maven:geckolib-388172:7553267"))
    add("worldGameTestRuntimeOnly", fg.deobf("curse.maven:curios-api-309927:6418456"))
    add("worldGameTestRuntimeOnly", fg.deobf(
            "com.teamresourceful.resourcefullib:resourcefullib-forge-1.20.1:2.1.29"))
    add("worldGameTestRuntimeOnly", fg.deobf(
            "com.teamresourceful.resourcefulconfig:resourcefulconfig-forge-1.20.1:2.1.3"))
    add("worldGameTestRuntimeOnly", fg.deobf(
            "worldtest.enderman:endermanoverhaul-forge-1.20.1-1.0.4:yjxych8u@jar"))
    add("worldGameTestRuntimeOnly", fg.deobf("bettercontent.local:better-deaths-door:1.0.0"))
    // Only declared current-pack geological archives; unused legacy Deeper and Darker is absent.
    geologicalArchives.forEach { archive ->
        val coordinate = archive["coordinate"].toString()
        add(pinnedGeologicalArchives.name, coordinate)
        add("geologyGameTestRuntimeOnly", fg.deobf(coordinate))
    }
}

mixin {
    add(sourceSets.main.get(), "better_cave_encounters.refmap.json")
    config("better_cave_encounters.mixins.json")
}

tasks.compileJava {
    val actualRefmap = layout.buildDirectory.file("tmp/compileJava/compileJava-refmap.json")
    outputs.file(actualRefmap)
    // MixinExtras registers WrapOperation before Mixin's injector processor scans it.
    options.compilerArgs.addAll(listOf("-processor", "com.llamalad7.mixinextras.ap.MixinExtrasAP,org.spongepowered.tools.obfuscation.MixinObfuscationProcessorInjection,org.spongepowered.tools.obfuscation.MixinObfuscationProcessorTargets"))
    doLast {
        check(actualRefmap.get().asFile.isFile) { "Initial-root Mixin processor did not produce its actual refmap" }
        val text = actualRefmap.get().asFile.readText()
        check(text.contains("DamnedInitialRootMixin") && text.contains("EntityType;m_262496_")) {
            "Initial-root refmap must bind the actual production native EntityType.spawn invocation"
        }
    }
}

tasks.processResources {
    dependsOn(tasks.compileJava)
    from(layout.buildDirectory.file("tmp/compileJava/compileJava-refmap.json")) {
        rename { "better_cave_encounters.refmap.json" }
    }
    val props = mapOf(
        "minecraft_version" to project.property("minecraft_version"),
        "forge_version" to project.property("forge_version"),
        "mod_id" to project.property("mod_id"),
        "mod_name" to project.property("mod_name"),
        "mod_version" to project.property("mod_version")
    )
    inputs.properties(props)
    filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) { expand(props) }
}

tasks.named<Jar>("jar") {
    manifest.attributes("MixinConfigs" to "better_cave_encounters.mixins.json")
    exclude("**/GeologicalDefinitionGameTests*", "native-case-inventory.json")
    finalizedBy("reobfJar")
}

val nativeParserNamespace = property("mod_id").toString()
val parserInventoryFile = file("src/nativeGameTest/resources/native-case-inventory.json")
val parserInventory = JsonSlurper().parse(parserInventoryFile) as Map<*, *>
val baselineParserCases = (parserInventory["baseline"] as List<*>).map { it.toString() }
val migratedParserCases = (parserInventory["migrated"] as List<*>).map { it.toString() }
val requiredParserCases = baselineParserCases + migratedParserCases
fun requireNativeParserInventory(names: List<String>, namespace: String) {
    require(namespace == nativeParserNamespace && namespace == parserInventory["namespace"]) { "Wrong native parser namespace" }
    require(baselineParserCases.size == 10 && migratedParserCases.size == 2 && parserInventory["required_count"] == 12) { "Native parser inventory must be original 10 plus two new cases" }
    require(names.size == 12 && names.toSet().size == 12 && names.toSet() == requiredParserCases.toSet()) { "Missing/extra/aliased native parser assertion case" }
}
fun requireParserArchiveEntries(entries: Set<String>) {
    require("com/bettercontent/bettercaveencounters/GeologicalDefinition.class" in entries) { "Production parser missing from archive" }
    require(entries.none { it.contains("GeologicalDefinitionGameTests") || it == "native-case-inventory.json" }) { "Nonshipping parser fixture leaked into runtime archive" }
}
val verifyNativeParserProfile = tasks.register("verifyNativeParserProfile") {
    dependsOn(nativeGameTest.classesTaskName)
    inputs.file(parserInventoryFile)
    doLast {
        val sources = listOf(file("src/main/java/com/bettercontent/bettercaveencounters/DepthDirectorGameTests.java"),
            file("src/nativeGameTest/java/com/bettercontent/bettercaveencounters/GeologicalDefinitionGameTests.java"))
        val names = sources.flatMap { source ->
            val text = source.readText()
            require(text.contains("@GameTestHolder(DepthDirectorMod.MOD_ID)")) { "Native fixture namespace changed" }
            Regex("""@GameTest\([^)]*\)\s*public static void (\w+)\(""").findAll(text).map { it.groupValues[1] }.toList()
        }
        requireNativeParserInventory(names, nativeParserNamespace)
        fun rejects(body: () -> Unit) {
            var rejected = false
            try { body() } catch (expected: IllegalArgumentException) { rejected = true }
            check(rejected) { "Native fixture guard accepted a synthetic defect" }
        }
        rejects { requireNativeParserInventory(names.dropLast(1), nativeParserNamespace) }
        rejects { requireNativeParserInventory(names + "unexpectedAlias", nativeParserNamespace) }
        rejects { requireNativeParserInventory(names, "wrong_namespace") }
        val production = setOf("com/bettercontent/bettercaveencounters/GeologicalDefinition.class")
        requireParserArchiveEntries(production)
        rejects { requireParserArchiveEntries(production + "com/bettercontent/bettercaveencounters/GeologicalDefinitionGameTests.class") }
        rejects { requireParserArchiveEntries(emptySet()) }
    }
}
val verifyNativeParserArchive = tasks.register("verifyNativeParserArchive") {
    dependsOn(tasks.named("reobfJar"))
    doLast {
        ZipFile(layout.buildDirectory.file("reobfJar/output.jar").get().asFile).use { zip ->
            requireParserArchiveEntries(zip.entries().asSequence().map { it.name }.toSet())
        }
    }
}

// Nonshipping world classes/resources must stay out of both published archive kinds.
val nonshippingWorldEntries = listOf("**/DepthDirectorWorldGameTests*", "**/GeologicalWorldGameTests*",
    "**/DepthDirectorWorldTestMod*", "**/CatalogueRoot*", "**/CaveFixtureGlm*", "**/FixtureGlmLoadMixin*",
    "bc_cave_fixture_glm*", "glm-profiles.json", "world-case-inventory.json", "world-initial-root-adaptations.json", "geological-qualification.json",
    "data/better_cave_encounters_world_tests/**")
tasks.named<Jar>("jar") { exclude(nonshippingWorldEntries) }
tasks.named<Jar>("sourcesJar") {
    exclude(nonshippingWorldEntries)
    exclude("**/GeologicalDefinitionGameTests*", "native-case-inventory.json")
}
val verifyWorldProfilePins = tasks.register("verifyWorldProfilePins") {
    inputs.files(worldProfileInputFile, geologicalPackRoot.resolve(worldDeepVoid["pin"].toString()))
    doLast {
        project.exec {
            commandLine("python3", "-B", "gradle/verify-world-fixture.py",
                "--root-pin", geologicalPackRoot.resolve(worldDeepVoid["pin"].toString()).absolutePath,
                "--deep-void-archive", pinnedWorldDeepVoid.singleFile.absolutePath,
                "--pin-proof", layout.buildDirectory.file("verification/world-profile-pin-proof.json").get().asFile.absolutePath)
        }
    }
}
val verifyWorldFixtureProfile = tasks.register("verifyWorldFixtureProfile") {
    dependsOn(worldGameTest.classesTaskName, geologyGameTest.classesTaskName, verifyWorldProfilePins)
    inputs.files(fileTree("src/worldGameTest"), file("gradle/verify-world-fixture.py"))
    doLast {
        project.exec {
            commandLine("python3", "-B", "gradle/verify-world-fixture.py",
                "--world-resources", worldGameTest.output.resourcesDir!!.absolutePath,
                "--geology-resources", geologyGameTest.output.resourcesDir!!.absolutePath)
        }
    }
}
val verifyWorldFixtureArchives = tasks.register("verifyWorldFixtureArchives") {
    dependsOn(tasks.named("reobfJar"), tasks.named("sourcesJar"))
    doLast {
        project.exec {
            commandLine("python3", "-B", "gradle/verify-world-fixture.py",
                "--runtime-archive", layout.buildDirectory.file("reobfJar/output.jar").get().asFile.absolutePath,
                "--sources-archive", tasks.named<Jar>("sourcesJar").get().archiveFile.get().asFile.absolutePath)
        }
    }
}

val stageRuntimeJar by tasks.registering(Copy::class) {
    group = "build"
    dependsOn(tasks.named("reobfJar"))
    from(layout.buildDirectory.file("reobfJar/output.jar"))
    into(layout.buildDirectory.dir("libs"))
    rename { "${base.archivesName.get()}-$version.jar" }
}

tasks.named("assemble") { dependsOn(stageRuntimeJar) }
tasks.withType<JavaCompile>().configureEach { options.release.set(17) }
tasks.test { useJUnitPlatform() }

fun retainedFixture(name: String, directory: File) = tasks.register(name) {
    doLast {
        require(!directory.exists()) {
            "Native fixture already exists at $directory; choose a fresh -Pbc.nativeFixtureId (nothing was deleted)"
        }
        Files.createDirectories(directory.parentFile.toPath())
        Files.createDirectory(directory.toPath())
        // Exact absent-only defaults: never overwrite/adopt operator or previously prepared settings.
        Files.writeString(directory.resolve("server.properties").toPath(),
            "# Retained native GameTest fixture; vanilla defaults.\n",
            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
    }
}
val prepareGameTestRun = retainedFixture("prepareRetainedGameTestRun", gameTestRun)
val prepareWorldTestRun = retainedFixture("prepareRetainedWorldTestRun", worldTestRun)
prepareWorldTestRun.configure { dependsOn(verifyWorldProfilePins) }
val prepareGeologyTestRun = retainedFixture("prepareRetainedGeologyTestRun", geologyTestRun)
val syncGameTestStructures = tasks.register<Copy>("syncGameTestStructures") {
    dependsOn(prepareGameTestRun)
    from(layout.projectDirectory.dir("src/main/resources/gameteststructures"))
    into(gameTestRun.resolve("gameteststructures"))
}
tasks.matching { it.name.startsWith("prepareRunGameTestServer") }.configureEach {
    dependsOn(syncGameTestStructures)
}
val syncWorldGameTestStructures = tasks.register<Copy>("syncWorldGameTestStructures") {
    dependsOn(prepareWorldTestRun)
    from(layout.projectDirectory.dir("src/main/resources/gameteststructures"))
    into(worldTestRun.resolve("gameteststructures"))
}
tasks.matching { it.name.startsWith("prepareRunWorldGameTestServer") }.configureEach {
    dependsOn(syncWorldGameTestStructures)
}

val verifyGeologicalPins = tasks.register("verifyGeologicalPins") {
    dependsOn(tasks.named("reobfJar"), prepareGeologyTestRun)
    inputs.files(geologicalArchives.map { geologicalPackRoot.resolve(it["pin"].toString()) })
    doLast {
        val hashes = linkedMapOf<String, String>()
        fun digest(file: File, algorithm: String) = MessageDigest.getInstance(algorithm)
            .digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        val resolved = pinnedGeologicalArchives.resolvedConfiguration.resolvedArtifacts
        geologicalArchives.forEach { archive ->
            val coordinate = archive["coordinate"].toString().substringBefore('@')
            val pin = geologicalPackRoot.resolve(archive["pin"].toString())
            require(pin.isFile) { "Missing committed pack pin: $pin" }
            val text = pin.readText()
            fun value(key: String) = Regex("(?m)^" + Regex.escape(key) + "\\s*=\\s*\"([^\"]+)\"")
                .find(text)?.groupValues?.get(1) ?: error("Missing $key in $pin")
            val original = resolved.single { it.moduleVersion.id.toString() == coordinate }.file
            val expected = value("hash")
            require(digest(original, value("hash-format").replace("sha", "SHA-")).equals(expected, true)) {
                "Pinned native archive mismatch for $coordinate / $pin"
            }
            hashes[coordinate] = digest(original, "SHA-256")
        }
        listOf("better-compat-fixes" to "better-compat-fixes-0.1.9.jar",
            "better-deaths-door" to "better-deaths-door-1.0.0.jar",
            "better-gameplay-notices" to "better-gameplay-notices-1.0.0.jar").forEach { (repo, artifact) ->
            hashes[artifact] = digest(betterContentJar(repo, artifact), "SHA-256")
        }
        hashes["director_reobf_artifact"] = digest(layout.buildDirectory.file("reobfJar/output.jar").get().asFile, "SHA-256")
        val packHashes = linkedMapOf<String, String>()
        geologicalPackInputs.forEach { pattern ->
            val files = fileTree(geologicalPackRoot) { include(pattern) }.files
            require(files.isNotEmpty()) { "Missing declared geological qualification input: $pattern" }
            files.sorted().forEach { packHashes[it.relativeTo(geologicalPackRoot).invariantSeparatorsPath] = digest(it, "SHA-256") }
        }
        geologyTestRun.resolve("qualification-inputs.json").writeText(JsonOutput.prettyPrint(JsonOutput.toJson(mapOf(
            "fixture_id" to nativeFixtureId, "native_sha256" to hashes, "pack_sha256" to packHashes,
            "verdict_scope" to "native source fixture; not full-pack same-artifact acceptance"))))
    }
}
val prepareGeologicalInputs = tasks.register<Copy>("prepareGeologicalInputs") {
    dependsOn(verifyGeologicalPins)
    from(geologicalPackRoot) { include(geologicalPackInputs) }
    into(geologyTestRun)
}
val syncGeologicalStructures = tasks.register<Copy>("syncGeologicalStructures") {
    dependsOn(verifyGeologicalPins)
    from(layout.projectDirectory.dir("src/main/resources/gameteststructures"))
    into(geologyTestRun.resolve("gameteststructures"))
}
tasks.matching { it.name.startsWith("prepareRunGeologyGameTestServer") }.configureEach {
    dependsOn(prepareGeologicalInputs, syncGeologicalStructures)
}

tasks.matching { it.name == "runGameTestServer" }.configureEach {
    doLast {
        val latest = gameTestRun.resolve("logs/latest.log")
        require(latest.isFile) { "Missing actual native parser profile log" }
        val counts = Regex("""All (\d+) required tests passed""").findAll(latest.readText()).map { it.groupValues[1].toInt() }.toList()
        require(counts == listOf(12)) { "Native parser profile must actually complete all original 10 plus two cases: $counts" }
        listOf("latest.log", "debug.log").forEach { name ->
            val log = gameTestRun.resolve("logs/$name")
            require(log.isFile) { "Missing actual native parser diagnostics: $log" }
            require(!Regex("""\[[^\]\r\n]*/(?:ERROR|FATAL)\]|(?i:could not decode|couldn't decode|Tried to load a block entity for block .* but failed at location |Tried to load a DUMMY block entity @ .* but found not block entity block )""").containsMatchIn(log.readText())) { "Native parser diagnostics require diagnosis: $log" }
        }
    }
}

tasks.register("headlessGameTest") {
    group = "verification"
    dependsOn(tasks.named("runGameTestServer"))
}
tasks.register("verifyFast") {
    group = "verification"
    dependsOn(tasks.named("check"), rootContractTest, verifyNativeParserProfile, verifyNativeParserArchive,
        verifyWorldFixtureProfile, verifyWorldFixtureArchives)
}
tasks.register("verifyFull") {
    group = "verification"
    dependsOn(tasks.named("verifyFast"), tasks.named("headlessGameTest"))
}
tasks.register("verifyWorld") {
    group = "verification"
    description = "Runs retained full-cadence and pinned geological/native-population qualification fixtures."
    dependsOn(tasks.named("verifyFull"), tasks.named("runWorldGameTestServer"), tasks.named("runGeologyGameTestServer"))
}
fun requireWorldProfileCompletion(directory: File) {
    val latest = directory.resolve("logs/latest.log")
    require(latest.isFile) { "Missing actual world fixture log" }
    val counts = Regex("""All (\d+) required tests passed""").findAll(latest.readText()).map { it.groupValues[1].toInt() }.toList()
    require(counts == listOf(1)) { "Selected original world profile must complete exactly its one original case: $counts" }
    listOf("latest.log", "debug.log").forEach { name ->
        val log = directory.resolve("logs/$name")
        require(log.isFile) { "Missing actual world diagnostics: $log" }
        require(!Regex("""\[[^\]\r\n]*/(?:ERROR|FATAL)\]|(?i:could not decode|couldn't decode|Tried to load a block entity for block .* but failed at location |Tried to load a DUMMY block entity @ .* but found not block entity block )""").containsMatchIn(log.readText())) { "World diagnostics require diagnosis: $log" }
    }
}
tasks.matching { it.name == "runWorldGameTestServer" }.configureEach {
    doLast { requireWorldProfileCompletion(worldTestRun) }
    mustRunAfter(tasks.named("verifyFull"))
}
tasks.matching { it.name == "runGeologyGameTestServer" }.configureEach {
    doLast { requireWorldProfileCompletion(geologyTestRun) }
    mustRunAfter(tasks.named("verifyFull"), tasks.named("runWorldGameTestServer"))
}

// Nonshipping policy preparation/proof has no production RunConfig or archive surface.
extra["caveFixtureGlmToken"] = nativeFixtureId
extra["caveFixtureGlmPackRoot"] = geologicalPackRoot
extra["caveFixtureGlmWorldRun"] = worldTestRun
extra["caveFixtureGlmGeologyRun"] = geologyTestRun
extra["caveFixtureGlmProviders"] = mapOf(
    "better-compat-fixes" to betterContentJar("better-compat-fixes", "better-compat-fixes-0.1.9.jar"),
    "better-deaths-door" to betterContentJar("better-deaths-door", "better-deaths-door-1.0.0.jar"),
    "better-gameplay-notices" to betterContentJar("better-gameplay-notices", "better-gameplay-notices-1.0.0.jar"))
apply(from = "gradle/fixture-glm.gradle")
