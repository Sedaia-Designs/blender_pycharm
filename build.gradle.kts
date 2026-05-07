plugins {
	id("java")
	id("org.jetbrains.kotlin.jvm") version "2.1.20"
	id("org.jetbrains.intellij.platform") version "2.13.1"
}

group = "com.sakura-sedaia"
version = "0.7.0-SNAPSHOT"

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
				<li><b>uv-Powered Python Integration</b>: Implemented mandatory <code>uv</code> integration for ultra-fast virtual environment management and linter installation.</li>
				<li><b>Project Traits Management</b>: New capabilities to generate essential project files (Junie guidelines, Run Configs, .gitignore, LICENSE).</li>
				<li><b>Background Task Management</b>: Centralized all long-running operations into <code>BlenderTaskManager</code> for a non-blocking IDE experience.</li>
				<li><b>Execution Validation</b>: Proactive detection of filesystem execution restrictions (e.g., <code>noexec</code>) with actionable troubleshooting guides.</li>
				<li><b>User Permission Prompt</b>: Added explicit confirmation dialogs for invasive operations like SDK management.</li>
				<li><b>Enhanced Blender Discovery</b>: Improved <code>BlenderScanner</code> and <code>BlenderPathUtil</code> for more reliable detection across all platforms.</li>
				<li><b>Robust Downloader</b>: Overhauled <code>BlenderDownloader</code> and <code>ArchiveUtil</code> to improve extraction reliability and Unix permissions.</li>
				<li><b>Internationalization (i18n)</b>: Audited <code>LangManager</code> message bundles, standardized terminology, and synchronized 11 language bundles.</li>
			</ul>
			<b>Changed</b>
			<ul>
				<li><b>UI Modernization</b>: Refactored Tool Window, Settings, and Run Configuration editors using modern Kotlin DSL components.</li>
				<li><b>Repository & CI Migration</b>: Migrated workflows to <code>.forgejo</code> for native Codeberg compatibility.</li>
				<li><b>Junie Agent Guidelines</b>: Renamed configuration directory to <code>.junie</code> and expanded documentation requirements.</li>
				<li><b>Core Refactoring</b>: Enhanced service layer (<code>BlenderService</code>, <code>BlenderLinker</code>) and centralized version parsing logic.</li>
				<li><b>Process Management</b>: Refactored Blender process launching to use <code>KillableProcessHandler</code> for better responsiveness.</li>
				<li><b>Enhanced Build & Validate</b>: Added dedicated inputs for source and output directories in run configurations.</li>
			</ul>
			<b>Fixed</b>
			<ul>
				<li><b>Thread Safety & EDT Compliance</b>: Fixed "Access is allowed from Event Dispatch Thread (EDT) only" errors during SDK initialization.</li>
				<li><b>Codebase Modernization</b>: Replaced several deprecated IntelliJ APIs with modern recommended implementations.</li>
				<li><b>Archive & Download Reliability</b>: Implemented robust extraction using temporary directories, atomic moves, and file size verification.</li>
				<li><b>Communication Server Leak</b>: Implemented robust cleanup in <code>BlenderCommunicationService</code> to prevent resource leaks.</li>
				<li><b>Linux Execution (Error 13)</b>: Resolved issues where Blender failed to launch on partitions with restrictive mount flags.</li>
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
