// Builds the mod for obfuscated Minecraft versions (1.20.1 - 1.21.11).
// Shares sources and resources with the main (26.x) build in the parent directory;
// only the mixin differs per API variant (see src/<variant>/java).
//
// Each subproject in versions/ is one jar covering a group of binary compatible versions:
// it compiles against minecraft_version and is published for every entry in game_versions.
//
// Usually run through the main build (./gradlew buildAll / modrinthAll), or directly:
// Build all:    ./gradlew -p legacy build
// Build one:    ./gradlew -p legacy :1.20.1:build
// Output jars:  out/<first game version>[-<last game version>]/<jar>

import com.modrinth.minotaur.ModrinthExtension
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.task.RemapJarTask
import java.util.Properties

plugins {
	id("net.fabricmc.fabric-loom-remap") apply false
	id("com.modrinth.minotaur") apply false
}

val mainProps = Properties().apply {
	file("../gradle.properties").inputStream().use { load(it) }
}
val mainSrc = file("../src/main")

subprojects {
	apply(plugin = "net.fabricmc.fabric-loom-remap")
	apply(plugin = "com.modrinth.minotaur")

	val mc = property("minecraft_version") as String
	val supportedVersions = (property("game_versions") as String).split(",").map { it.trim() }
	val versionLabel = if (supportedVersions.size == 1) supportedVersions[0] else "${supportedVersions.first()}-${supportedVersions.last()}"
	val minecraftDependency =
		if (supportedVersions.size == 1) supportedVersions[0] else ">=${supportedVersions.first()} <=${supportedVersions.last()}"
	val javaVersion = (property("java_version") as String).toInt()
	val mixinVariant = property("mixin_variant") as String
	val archivesBaseName = mainProps.getProperty("archives_base_name")

	version = "${mainProps.getProperty("mod_version")}+$versionLabel"
	group = mainProps.getProperty("maven_group")

	extensions.configure<BasePluginExtension> {
		archivesName = archivesBaseName
	}

	val loom = the<LoomGradleExtensionAPI>()

	dependencies {
		"minecraft"("com.mojang:minecraft:$mc")
		"mappings"(loom.officialMojangMappings())
		"modImplementation"("net.fabricmc:fabric-loader:${mainProps.getProperty("loader_version")}")
	}

	the<SourceSetContainer>().named("main") {
		java.srcDir(File(mainSrc, "java"))
		// mixin_variant=main reuses the main build's mixin as-is (same API as 26.x)
		if (mixinVariant != "main") {
			java.srcDir(rootProject.file("src/$mixinVariant/java"))
			val mainMixinDir = File(mainSrc, "java/de/joker/mixin").path
			java.exclude { it.file.path.startsWith(mainMixinDir) }
		}
		resources.setSrcDirs(listOf(File(mainSrc, "resources")))
	}

	tasks.named<ProcessResources>("processResources") {
		val props = mapOf(
			"version" to project.version,
			"minecraft_dependency" to minecraftDependency,
			"loader_dependency" to project.property("loader_dependency"),
			"java_version" to javaVersion,
			"mixin_java_version" to javaVersion,
		)
		inputs.properties(props)

		filesMatching(listOf("fabric.mod.json", "serverpingfixer.mixins.json")) {
			expand(props)
		}
	}

	tasks.withType<JavaCompile>().configureEach {
		options.release = javaVersion
	}

	extensions.configure<JavaPluginExtension> {
		sourceCompatibility = JavaVersion.toVersion(javaVersion)
		targetCompatibility = JavaVersion.toVersion(javaVersion)
	}

	tasks.named<Jar>("jar") {
		from(rootProject.file("../LICENSE")) {
			rename { "${it}_$archivesBaseName" }
		}
	}

	val remapJar = tasks.named<RemapJarTask>("remapJar")

	val copyToOut = tasks.register<Sync>("copyToOut") {
		from(remapJar)
		into(rootProject.file("../out/$versionLabel"))
	}

	tasks.named("build") {
		finalizedBy(copyToOut)
	}

	extensions.configure<ModrinthExtension> {
		token = providers.environmentVariable("MODRINTH_TOKEN")
		// -Pmodrinth_debug=true prints the upload instead of sending it
		debugMode = providers.gradleProperty("modrinth_debug").map { it.toBoolean() }.orElse(false)
		projectId = mainProps.getProperty("modrinth_id")
		versionNumber = project.version.toString()
		versionType = mainProps.getProperty("modrinth_version_type")
		uploadFile.set(remapJar)
		gameVersions.addAll(supportedVersions)
		loaders.addAll("fabric", "quilt")
		dependencies {
			required.project("fabric-api")
		}

		mainProps.getProperty("modrinth_changelog")?.let { changelog.set(it) }
	}
}

// Aggregates so the main build can run every group via the included build
tasks.register("build") {
	dependsOn(subprojects.map { "${it.path}:build" })
}

tasks.register("modrinth") {
	dependsOn(subprojects.map { "${it.path}:modrinth" })
}
