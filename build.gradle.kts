import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.gradle.api.tasks.bundling.Zip

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")

    kotlin("plugin.serialization") version "2.2.20"
}

group = "com.sakurasedaia"
version = "0.10.0-SNAPSHOT"

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    testImplementation(libs.junit)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        pycharm("2026.1")
        testFramework(TestFrameworkType.Platform)

        // Add plugin dependencies for compilation here:
        bundledPlugin("PythonCore")
        bundledPlugin("Pythonid")
    }

    implementation("dev.eav.tomlkt:tomlkt:0.6.0") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.3") { // Required by tomlkt
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }

}

intellijPlatform {
    pluginConfiguration {
        vendor {
            url="https://sakura-sedaia.com"
        }
        
        ideaVersion {
            sinceBuild = "261"
        }
        
        changeNotes = """
            <h3>0.10.0-Snapshot</h3>
            <ul>
              <li>Refines Project Blender Manager around synchronized application and project state.</li>
              <li>Restores cached Blender installations when a project opens.</li>
              <li>Simplifies executable, source, and launch controls.</li>
              <li>Fixes recursive table edits and applies managed installation path changes without restarting the IDE.</li>
            </ul>
            <p>This is a pre-release snapshot and is not production-hardened.</p>
        """.trimIndent()
    }
    autoReload = true
}

tasks {
    val packageBlenderRuntime by registering(Zip::class) {
        group = "build"
        description = "Packages Blender runtime API files into an archive for plugin distribution."
        archiveFileName.set("blender-runtime.zip")
        destinationDirectory.set(layout.buildDirectory.dir("generated/blender-runtime"))
        from(layout.projectDirectory.dir("src/main/python")) {
            into("include/blender_pycharm")
        }
        exclude("**/__pycache__/**", "**/*.pyc", "**/*.pyo")
    }

    processResources {
        dependsOn(packageBlenderRuntime)
        from(packageBlenderRuntime) {
            into("blender-runtime")
        }
    }

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
    
    runIde {
        autoReload = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
