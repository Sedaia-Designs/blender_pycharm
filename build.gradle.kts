import org.gradle.api.tasks.WriteProperties
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.testing.Test
import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("org.jetbrains.changelog")
  id("org.jetbrains.intellij.platform")
  id("com.diffplug.spotless") version "8.9.0"

  kotlin("plugin.serialization") version "2.2.20"
}

spotless {
  kotlin {
    target("src/**/*.kt")
    ktfmt("0.64").googleStyle().configure {
      it.setMaxWidth(140)
      it.setBlockIndent(2)
      it.setContinuationIndent(4)
      it.setRemoveUnusedImports(false)
    }
    trimTrailingWhitespace()
    endWithNewline()
  }

  kotlinGradle {
    target("*.gradle.kts", "gradle/**/*.gradle.kts")
    ktfmt("0.64").googleStyle().configure {
      it.setMaxWidth(140)
      it.setBlockIndent(2)
      it.setContinuationIndent(4)
      it.setRemoveUnusedImports(false)
    }
    trimTrailingWhitespace()
    endWithNewline()
  }
}

group = "com.sakurasedaia"

version = "1.0.0-beta.3"

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
  testImplementation(libs.junit)

  // IntelliJ Platform Gradle Plugin Dependencies Extension - read more:
  // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
  intellijPlatform {
    pycharm("2026.1")
    testFramework(TestFrameworkType.Platform)

    // Add plugin dependencies for compilation here:
    bundledPlugin("PythonCore")
  }

  // Toml KT Dependencies
  implementation("dev.eav.tomlkt:tomlkt:0.6.0") {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
  }
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.3") { // Required by tomlkt
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
  }

  // For HMAC Codecs
  implementation("commons-codec:commons-codec:1.19.0")
}

intellijPlatform {
  pluginConfiguration {
    vendor {
      url = "https://sakura-sedaia.com"
    }

    ideaVersion {
      sinceBuild = "261"
    }

    changeNotes = changelog.renderItem(changelog.get(project.version.toString()), Changelog.OutputType.HTML)
  }

  signing {
    val privateKeyEnvironment = providers.environmentVariable("PRIVATE_KEY")
    val certificateChainEnvironment = providers.environmentVariable("CERTIFICATE_CHAIN")

    privateKey.set(privateKeyEnvironment)
    certificateChain.set(certificateChainEnvironment)
    if (!privateKeyEnvironment.isPresent) {
      privateKeyFile.set(layout.projectDirectory.file(".env/private.pem"))
    }
    if (!certificateChainEnvironment.isPresent) {
      certificateChainFile.set(layout.projectDirectory.file(".env/chain.crt"))
    }
  }

  publishing {
    token.set(providers.environmentVariable("PUBLISH_TOKEN"))
    channels.set(listOf("dev"))
  }

  autoReload = true

  pluginVerification {
    ides {
      create(IntelliJPlatformType.PyCharm, "261.27258.30")
      create(IntelliJPlatformType.PyCharm, "262.9437.71")
    }
  }
}

tasks {
  withType<Test> {
    // Keep platform tests isolated from unrelated bundled IDE plugins such as Vue.js.
    // The platform automatically loads this plugin's non-optional dependencies.
    systemProperty("idea.load.plugins.id", "com.sakurasedaia.BlenderDevelopment")
  }

  val generatePluginMetadata by
      registering(WriteProperties::class) {
        description = ""
        destinationFile = layout.buildDirectory.file("generated/plugin-metadata/blender-development.properties").get().asFile
        property("plugin.version", version.toString())
      }

  val packageBlenderRuntime by
      registering(Zip::class) {
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
    dependsOn(packageBlenderRuntime, generatePluginMetadata)
    from(generatePluginMetadata)
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
    jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
  }
}
