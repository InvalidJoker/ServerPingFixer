pluginManagement {
	repositories {
		maven {
			name = "Fabric"
			url = uri("https://maven.fabricmc.net/")
		}
		mavenCentral()
		gradlePluginPortal()
	}

	plugins {
		id("net.fabricmc.fabric-loom-remap") version providers.gradleProperty("loom_version").get()
		id("com.modrinth.minotaur") version providers.gradleProperty("minotaur_version").get()
	}
}

rootProject.name = "serverpingfixer-legacy"

// One subproject per Minecraft version, configured by versions/<mc>/gradle.properties
file("versions").listFiles()!!.filter { it.isDirectory }.forEach { dir ->
	include(dir.name)
	project(":${dir.name}").projectDir = dir
}
