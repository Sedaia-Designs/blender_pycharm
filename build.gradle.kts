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
				<li><b>Virtual Environment Guardrail</b>: Automatic project-wide <code>.venv</code> management.</li>
				<li><b>Linter Setup Improvements</b>: Explicit <code>pip</code> and <code>ensurepip</code> usage for more reliable installations.</li>
				<li><b>Shared Index Fix</b>: Corrected SDK version string formatting for better IDE integration.</li>
			</ul>
			<b>Changed</b>
			<ul>
				<li><b>Internationalization</b>: Complete localization of logs and synchronization of all language bundles.</li>
				<li><b>Refined UI</b>: Simplified Tool Window and settings UI.</li>
			</ul>
			<b>Removed</b>
			<ul>
				<li><b>Automated Python Download</b>: Removed complex external interpreter management.</li>
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
