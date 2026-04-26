plugins {
	id("java")
	id("org.jetbrains.kotlin.jvm") version "2.1.20"
	id("org.jetbrains.intellij.platform") version "2.13.1"
}

group = "com.sakura-sedaia"
version = "0.6.0-SNAPSHOT"

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
				<li><b>Background Task Management</b>: Centralized UI-blocking operations into <code>BlenderTaskManager</code> for a smoother experience.</li>
				<li><b>Execution Validation</b>: Proactive detection of filesystem execution restrictions (e.g., <code>noexec</code>) with user-friendly diagnostics.</li>
				<li><b>UI Modernization</b>: Updated Tool Window, Settings, and Run Configurations with modern Kotlin DSL components.</li>
				<li><b>Enhanced Blender Discovery</b>: More reliable system-wide Blender detection across Linux and macOS.</li>
			</ul>
			<b>Changed</b>
			<ul>
				<li><b>Process Management</b>: Improved Blender process launching using <code>KillableProcessHandler</code> for better responsiveness.</li>
				<li><b>Refactored Service Layer</b>: Enhanced <code>BlenderService</code> and related components for better project lifecycle management.</li>
			</ul>
			<b>Fixed</b>
			<ul>
				<li><b>Archive Reliability</b>: Robust extraction using atomic moves and file integrity verification for downloads.</li>
				<li><b>Thread Safety</b>: Resolved race conditions and EDT conflicts during startup and cache access.</li>
				<li><b>Communication Safety</b>: Improved JSON serialization and resource cleanup in <code>BlenderCommunicationService</code>.</li>
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
