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
				<li><b>VENV-based Python Setup</b>: Configures a Virtual Environment (VENV) based on Blender's bundled Python for a cleaner development environment.</li>
				<li><b>Python Module Extraction</b>: Dedicated <code>python</code> package for improved modularity and reliability.</li>
			</ul>
			<b>Fixed</b>
			<ul>
				<li><b>Interpreter Paths</b>: Resolved issues where <code>site-packages</code> were lost during linter auto-setup.</li>
			</ul>
			<br>
			<b>Previous version (0.4.0)</b>
			<ul>
				<li><b>Blender Status Bar Widget</b>: New indicator in the IDE status bar showing connection status to Blender.</li>
				<li><b>Support for Multiple Source Folders</b>: Projects can now designate and manage multiple folders as Blender source directories.</li>
				<li><b>Automatic Python Interpreter Setup</b>: Streamlined environment configuration for new projects.</li>
				<li><b>Offline Telemetry</b>: Added local-only telemetry for debugging and error reporting.</li>
				<li><b>Internationalization</b>: Full i18n support for 11 languages (Spanish, German, French, Italian, Japanese, Korean, Dutch, Polish, Portuguese, Russian, and Chinese).</li>
				<li><b>Unit & Integration Testing</b>: Added a comprehensive test suite, including headless integration tests for TCP heartbeat and reload logic.</li>
				<li><b>Sandbox Management</b>: New tool window for clearing and managing Blender sandboxed environments.</li>
				<li><b>Bidirectional Heartbeat</b>: Implemented a more robust TCP client with bidirectional heartbeat and automatic retry logic.</li>
			</ul>
			<b>Changed</b>
			<ul>
				<li><b>Localization Refactor</b>: Standardized all resource bundle keys and migrated from <code>BlenderBundle</code> to <code>LangManager</code> (extending <code>DynamicBundle</code>).</li>
				<li><b>Improved Blender Downloader</b>: Refined extraction logic and updated the selectable version list to focus on LTS releases.</li>
				<li><b>Path Resolution</b>: Centralized and improved cross-platform path handling using Kotlin NIO.2 (<code>java.nio.file.Path</code>) utilities.</li>
				<li><b>Documentation Migration</b>: Moved comprehensive guides to a <a href="https://wiki.sakura-sedaia.com/docs/blender-development-pycharm/index.html">new Sphinx-based documentation site</a>.</li>
				<li><b>License Change</b>: Updated project license to GNU GPL v3.</li>
				<li><b>Configuration Discovery</b>: Switched to dynamic detection of Blender configuration subdirectories for improved OS compatibility.</li>
			</ul>
			<b>Fixed</b>
			<ul>
				<li><b>macOS Compatibility</b>: Prevented installation of Blender 5.0+ on Intel-based Macs and integrated <code>tryWhich</code> for better detection.</li>
				<li><b>Manifest Validation</b>: Switched extension Manifest IDs to <code>snake_case</code> to comply with Blender requirements.</li>
				<li><b>Run Configuration Stability</b>: Fixed absolute path handling for sandboxed installations and corrected CLI argument syntax.</li>
				<li><b>UI Stability</b>: Resolved crashes in the version management tool window and improved New Project Wizard validation.</li>
				<li><b>Logging</b>: Added log rotation for better disk usage management and expanded debug output.</li>
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
