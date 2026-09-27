plugins {
	id("net.fabricmc.fabric-loom")
	id("com.modrinth.minotaur")
}

val minecraftVersion = property("minecraft_version") as String

version = "${property("mod_version")}+$minecraftVersion"
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
		"minecraft_dependency" to "~$minecraftVersion",
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

val copyToOut = tasks.register<Copy>("copyToOut") {
	from(tasks.jar)
	into(file("out/$minecraftVersion"))
}

tasks.build {
	finalizedBy(copyToOut)
}

// Upload with: MODRINTH_TOKEN=<token> ./gradlew modrinth
// Dry run:     ./gradlew modrinth -Pmodrinth_debug=true
modrinth {
	token = providers.environmentVariable("MODRINTH_TOKEN")
	debugMode = providers.gradleProperty("modrinth_debug").map { it.toBoolean() }.orElse(false)
	projectId = property("modrinth_id") as String
	versionNumber = project.version.toString()
	versionType = property("modrinth_version_type") as String
	uploadFile.set(tasks.jar)
	gameVersions.add(minecraftVersion)
	loaders.add("fabric")
	dependencies {
		required.project("fabric-api")
	}

	if (providers.gradleProperty("modrinth_changelog").isPresent) {
		changelog.set(providers.gradleProperty("modrinth_changelog"))
	}
}
