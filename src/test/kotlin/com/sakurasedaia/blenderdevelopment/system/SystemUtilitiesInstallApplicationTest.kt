/*
 * Copyright (C) 2026 Sakura Sedaia
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.sakurasedaia.blenderdevelopment.system

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

/** Tests logging and validation behavior in [SystemUtilities.installBlender]. */
class SystemUtilitiesInstallApplicationTest : BasePlatformTestCase() {

	/**
	 * Returns today's plugin log file path under the IntelliJ log directory.
	 *
	 * @return absolute path to today's plugin log file.
	 */
	private fun todayLogFile(): Path {
		val date = LocalDate.now().toString()
		return Path.of(PathManager.getLogPath())
			.resolve("blender-plugin")
			.resolve("blender_plugin_$date.log")
	}

	/**
	 * Polls the plugin log file until [expected] appears or timeout is reached.
	 *
	 * @param expected text to search for in the log file.
	 * @param timeoutMs timeout in milliseconds.
	 * @return `true` if expected text is found before timeout.
	 */
	private fun waitForLogContains(expected: String, timeoutMs: Long = 5_000L): Boolean {
		val start = System.currentTimeMillis()
		val logPath = todayLogFile()
		while (System.currentTimeMillis() - start < timeoutMs) {
			if (Files.exists(logPath)) {
				val content = Files.readString(logPath, StandardCharsets.UTF_8)
				if (content.contains(expected)) return true
			}
			Thread.sleep(100)
		}
		return false
	}

	/**
	 * Verifies host-specific extraction logging is emitted for a valid bundle name.
	 *
	 * @return `Unit`.
	 */
	fun testInstallBlenderLogsPlatformExtractionForCurrentHost() {
		val utilities = SystemUtilities(project)
		val token = System.currentTimeMillis().toString()
		val (osToken, expectedExtractLine) = when {
			SystemInfo.isWindows -> "windows" to "Extracting Blender bundle for Windows from:"
			SystemInfo.isMac -> "macos" to "Extracting Blender bundle for macOS from:"
			SystemInfo.isLinux -> "linux" to "Extracting Blender bundle for Linux from:"
			else -> return
		}

		val bundleName = "blender-4.5.1$token-$osToken-x64.zip"
		utilities.installBlender(bundleName)

		assertTrue(
			"Expected install start log for bundle '$bundleName'",
			waitForLogContains("Installing application from bundle path: $bundleName")
		)
		assertTrue(
			"Expected extraction log for current host",
			waitForLogContains("$expectedExtractLine $bundleName")
		)
	}

	/**
	 * Verifies malformed bundle names are handled without throwing exceptions.
	 *
	 * @return `Unit`.
	 */
	fun testInstallBlenderWithMalformedBundleNameDoesNotThrow() {
		val utilities = SystemUtilities(project)
		try {
			utilities.installBlender("blender.zip")
		} catch (t: Throwable) {
			fail("installApplication should reject malformed bundle names without throwing, but threw: $t")
		}
	}
}
