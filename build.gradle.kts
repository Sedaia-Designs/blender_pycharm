import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.gradle.api.tasks.bundling.Zip

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

group = "com.sakurasedaia"
version = "1.0.0-RC1"

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    testImplementation(libs.junit)
    
    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        pycharm("2025.3")
        testFramework(TestFrameworkType.Platform)
        
        // Add plugin dependencies for compilation here:
        bundledPlugin("PythonCore")
        bundledPlugin("Pythonid")
    }
}

intellijPlatform {
    pluginConfiguration {
        vendor {
            url="https://sakura-sedaia.com"
        }
        
        ideaVersion {
            sinceBuild = "253.28294"
        }
        
        changeNotes = """
            Initial Release
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
        from(layout.projectDirectory.dir("src/main/blender-runtime"))
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
