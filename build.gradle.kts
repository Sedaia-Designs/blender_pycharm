plugins {
	id("java")
	id("org.jetbrains.kotlin.jvm") version "2.1.20"
	id("org.jetbrains.intellij.platform") version "2.13.1"
}

group = "com.sakura-sedaia"
version = "0.5.0-SNAPSHOT"

repositories {
	mavenCentral()
	intellijPlatform {
		defaultRepositories()
	}
}

dependencies {
	intellijPlatform {
		pycharm("2025.2.4")
		testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
		
		bundledPlugin("PythonCore")
		bundledPlugin("Pythonid")
	}
	testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
		pluginConfiguration {
			vendor {
				name = "Sakura Sedaia"
				url = "https://www.sakura-sedaia.com"
			}

			ideaVersion {
			sinceBuild = "252.25557"
		}
		
		changeNotes = """
			<b>Added</b>
			<ul>
				<li><b>Integrated Linter Management</b>: Direct linter installation for the current project SDK.</li>
				<li><b>Blender-to-Python Version Mapping</b>: Enhanced Blender version metadata for easier Python environment identification.</li>
			</ul>
			<b>Removed</b>
			<ul>
				<li><b>Automated Python Setup</b>: Removed complex Python download and virtual environment management.</li>
			</ul>
			<b>Changed</b>
			<ul>
				<li><b>Refined UI</b>: Simplified the tool window and settings UI by removing obsolete Python tables.</li>
			</ul>
		""".trimIndent()
	}
}

tasks {
	withType<JavaCompile> {
		sourceCompatibility = "21"
		targetCompatibility = "21"
	}

	buildSearchableOptions {
		enabled = false
	}

	prepareJarSearchableOptions {
		enabled = false
	}

	jarSearchableOptions {
		enabled = false
	}
}

kotlin {
	compilerOptions {
		jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
	}
}
