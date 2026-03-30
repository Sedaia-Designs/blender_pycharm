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
				<li><b>Virtual Environment Guardrail</b>: Introduced a project-wide guardrail that automatically ensures all Python-related operations run within a dedicated <code>.venv</code>.</li>
				<li><b>Linter Setup Improvements</b>: Explicit <code>pip</code> and <code>ensurepip</code> usage for more reliable installations.</li>
			</ul>
			<b>Changed</b>
			<ul>
				<li><b>Internationalization</b>: Complete localization of logs and synchronization of 11 language bundles.</li>
				<li><b>Refined UI</b>: Simplified Tool Window and settings UI by consolidating interpreter setup into the linter flow.</li>
				<li><b>SDK Metadata Management</b>: Improved SDK creation to reliably identify virtual environments and correctly set the home path and version metadata.</li>
			</ul>
			<b>Fixed</b>
			<ul>
				<li><b>EDT Conflict</b>: Resolved thread conflict when launching Telemetry and Debug instances simultaneously.</li>
			</ul>
			<b>Removed</b>
			<ul>
				<li><b>Automated Python Installation</b>: Removed complex system-level interpreter management.</li>
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
