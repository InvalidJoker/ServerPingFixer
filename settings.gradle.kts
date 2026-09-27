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
		id("net.fabricmc.fabric-loom") version providers.gradleProperty("loom_version").get()
		id("com.modrinth.minotaur") version providers.gradleProperty("minotaur_version").get()
	}
}

includeBuild("legacy")
