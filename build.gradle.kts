plugins {
	id("net.fabricmc.fabric-loom")
	id("com.modrinth.minotaur")
}

val minecraftVersion = property("minecraft_version") as String
val supportedVersions = (property("game_versions") as String).split(",").map { it.trim() }
val versionLabel = if (supportedVersions.size == 1) supportedVersions[0] else "${supportedVersions.first()}-${supportedVersions.last()}"

version = "${property("mod_version")}+$versionLabel"
group = property("maven_group") as String

base {
	archivesName = property("archives_base_name") as String
}

dependencies {
	minecraft("com.mojang:minecraft:$minecraftVersion")

	implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
	implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
}

tasks.processResources {
	val props = mapOf(
		"version" to project.version,
		"minecraft_dependency" to ">=${supportedVersions.first()} <=${supportedVersions.last()}",
		"loader_dependency" to project.property("loader_version"),
		"java_version" to 25,
		"mixin_java_version" to 21,
	)
	inputs.properties(props)

	filesMatching(listOf("fabric.mod.json", "serverpingfixer.mixins.json")) {
		expand(props)
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.release = 25
}

java {
	withSourcesJar()

	sourceCompatibility = JavaVersion.VERSION_25
	targetCompatibility = JavaVersion.VERSION_25
}

tasks.jar {
	val archivesName = base.archivesName.get()
	inputs.property("archivesName", archivesName)

	from("LICENSE") {
		rename { "${it}_$archivesName" }
	}
}

val copyToOut = tasks.register<Sync>("copyToOut") {
	from(tasks.jar)
	into(file("out/$versionLabel"))
}

tasks.build {
	finalizedBy(copyToOut)
}

// Upload all versions: MODRINTH_TOKEN=<token> ./gradlew modrinthAll
// Dry run:             ./gradlew modrinthAll -Pmodrinth_debug=true
// Only 26.x:           ./gradlew modrinth
modrinth {
	token = providers.environmentVariable("MODRINTH_TOKEN")
	debugMode = providers.gradleProperty("modrinth_debug").map { it.toBoolean() }.orElse(false)
	projectId = property("modrinth_id") as String
	versionNumber = project.version.toString()
	versionType = property("modrinth_version_type") as String
	uploadFile.set(tasks.jar)
	gameVersions.addAll(supportedVersions)
	loaders.addAll(buildList {
		add("fabric")
		add("quilt")
	})
	dependencies {
		required.project("fabric-api")
	}

	if (providers.gradleProperty("modrinth_changelog").isPresent) {
		changelog.set(providers.gradleProperty("modrinth_changelog"))
	}
}

val legacy = gradle.includedBuild("legacy")

tasks.register("buildAll") {
	group = "build"
	dependsOn(tasks.build, legacy.task(":build"))
}

tasks.register("modrinthAll") {
	group = "publishing"
	dependsOn(tasks.modrinth, legacy.task(":modrinth"))
}
