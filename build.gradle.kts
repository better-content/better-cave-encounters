import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

plugins {
    idea
    jacoco
    `maven-publish`
    id("net.minecraftforge.gradle") version "6.0.54"
}

group = property("mod_group_id") as String
version = property("mod_version") as String
base { archivesName.set(property("artifact_name") as String) }

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    withSourcesJar()
}

val worldGameTest = sourceSets.create("worldGameTest") {
    compileClasspath += sourceSets.main.get().output + configurations.compileClasspath.get()
    runtimeClasspath += output + compileClasspath
}

val geologyGameTest = sourceSets.create("geologyGameTest") {
    java.srcDir("src/worldGameTest/java")
    resources.srcDir("src/worldGameTest/resources")
    compileClasspath += sourceSets.main.get().output + configurations.compileClasspath.get()
    runtimeClasspath += output + compileClasspath
}
val qualification = JsonSlurper().parse(file("src/worldGameTest/resources/geological-qualification.json")) as Map<*, *>
val geologicalArchives = (qualification["archives"] as List<*>).map { it as Map<*, *> }
val geologicalPackInputs = (qualification["pack_inputs"] as List<*>).map { it.toString() }
val geologicalPackRoot = providers.environmentVariable("BC_PACK_ROOT").orNull?.let(::file)
    ?: file("../../better-content-modpack")
val pinnedGeologicalArchives = configurations.create("pinnedGeologicalArchives") { isTransitive = false }
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
                create("better_cave_encounters_world_tests") { source(worldGameTest) }
            }
        }
        create("geologyGameTestServer") {
            parent(gameTestServer)
            workingDirectory(geologyTestRun)
            property("forge.enabledGameTestNamespaces", "better_cave_encounters_geology_tests")
            property("bc.geologyQualification", "true")
            property("mixin.env.remapRefMap", "true")
            property("mixin.env.refMapRemappingFile", file("build/createSrgToMcp/output.srg").absolutePath)
            arg("--nogui")
            mods { create("better_cave_encounters_world_tests") { source(geologyGameTest) } }
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
    testCompileOnly(fg.deobf("bettercontent.local:better-compat-fixes:0.1.9"))
    testRuntimeOnly(fg.deobf("bettercontent.local:better-compat-fixes:0.1.9"))

    add("worldGameTestRuntimeOnly", fg.deobf("curse.maven:born-in-chaos-686437:7917933"))
    add("worldGameTestRuntimeOnly", fg.deobf("curse.maven:goety-586095:8087429"))
    add("worldGameTestRuntimeOnly", fg.deobf("curse.maven:deeper-and-darker-659011:5906086"))
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
    // Only declared current-pack archives, not the legacy world's extra Deeper and Darker fixture.
    geologicalArchives.forEach { archive ->
        val coordinate = archive["coordinate"].toString()
        add(pinnedGeologicalArchives.name, coordinate)
        add("geologyGameTestRuntimeOnly", fg.deobf(coordinate))
    }
}

tasks.processResources {
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

tasks.named<Jar>("jar") { finalizedBy("reobfJar") }

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
        require(!directory.exists() || directory.listFiles().isNullOrEmpty()) {
            "Native fixture already exists at $directory; choose a fresh -Pbc.nativeFixtureId (nothing was deleted)"
        }
        directory.mkdirs()
        // Vanilla logs ERROR for a missing properties file even in a fresh GameTest run.
        // An empty file uses native defaults without accepting that startup failure.
        directory.resolve("server.properties").writeText("# Retained native GameTest fixture; vanilla defaults.\n")
    }
}
val prepareGameTestRun = retainedFixture("prepareRetainedGameTestRun", gameTestRun)
val prepareWorldTestRun = retainedFixture("prepareRetainedWorldTestRun", worldTestRun)
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

tasks.register("headlessGameTest") {
    group = "verification"
    dependsOn(tasks.named("runGameTestServer"))
}
tasks.register("verifyFast") {
    group = "verification"
    dependsOn(tasks.named("check"))
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
tasks.matching { it.name == "runWorldGameTestServer" }.configureEach {
    mustRunAfter(tasks.named("verifyFull"))
}
tasks.matching { it.name == "runGeologyGameTestServer" }.configureEach {
    mustRunAfter(tasks.named("verifyFull"), tasks.named("runWorldGameTestServer"))
}
