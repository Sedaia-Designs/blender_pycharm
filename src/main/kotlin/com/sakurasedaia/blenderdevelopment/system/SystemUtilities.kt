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

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.sakurasedaia.blenderdevelopment.logging.ErrorTypes
import com.sakurasedaia.blenderdevelopment.logging.PluginLogger
import com.intellij.util.io.Decompressor

/** Utility for platform-aware Blender bundle installation and downloads. */
class SystemUtilities(private val project: Project) {
	private val logger = PluginLogger.getInstance(project)

	/**
	 * Handles the installation of applications based on the detected operating system.
	 *
	 * @param bundlePath Blender bundle file name or path.
	 * @return `Unit`.
	 */
	fun installBlender(bundlePath: String) {
		logger.log("Installing application from bundle path: $bundlePath")
		
		// Blender will use the names "windows", "macos", and "linux" in each platform's bundle.
		// Blender uses x64 and arm64 to denote architecture.
		
		// Basic checks to ensure the Blender bundle is valid
		val bundleSplit: List<String> = bundlePath.split("-")
		when {
			bundleSplit.first() != "blender" -> {
				logger.error(ErrorTypes.NOT_BLENDER_BUNDLE)
				throw IllegalArgumentException(
					"Bundle provided is not a Portable Blender bundle"
				)
			}
			bundleSplit[2] !in listOf("windows", "macos", "linux") -> {
				logger.error(ErrorTypes.UNSUPPORTED_OS)
				throw IllegalArgumentException(
					"Bundle provided is not for a supported operating system"
				)
			}
			bundleSplit[3] !in listOf("x64", "arm64") -> {
				logger.error(ErrorTypes.UNSUPPORTED_ARCH)
				throw IllegalArgumentException(
					"Bundle provided is not for a supported architecture"
				)
			}
			bundleSplit[4] !in listOf("zip", "dmg", "tar.xz") -> {
				logger.error(ErrorTypes.ARCHIVE_FORMAT_UNSUPPORTED)
				throw IllegalArgumentException(
					"Bundle provided is not in a supported format"
				)
			}
		}
		
		
		when {
			SystemInfo.isWindows -> installBlenderWindows(bundlePath)
			SystemInfo.isMac -> installBlenderMac(bundlePath)
			SystemInfo.isLinux -> installBlenderLinux(bundlePath)
			else -> logger.error(ErrorTypes.UNSUPPORTED_OS)
		}
	}
	
	
	/**
	 * Makes use of Intellij's built-in Decompressor.Zip utility to extract the associated application bundle, since Blender ships as a ZIP Archive file for Windows
	 *
	 * @param bundlePath Blender bundle file name or path.
	 * @return `Unit`.
	 */
	private fun installBlenderWindows(bundlePath: String) {
		logger.log("Extracting Blender bundle for Windows from: $bundlePath")

	}
	
	
	/**
	 * Makes use of macOS's built-in hdiutil utility to extract the associated application bundle, since Blender ships as a DMG Installer file for macOS
	 *
	 * @param bundlePath Blender bundle file name or path.
	 * @return `Unit`.
	 */
	private fun installBlenderMac(bundlePath: String) {
		logger.log("Extracting Blender bundle for macOS from: $bundlePath")
	}
	
	
	/**
	 * Makes use of Intellij's built-in Decompressor.Tar utility to extract the associated application bundle, since Blender ships as a tar.xz bundle file for Linux
	 *
	 * @param bundlePath Blender bundle file name or path.
	 * @return `Unit`.
	 */
	private fun installBlenderLinux(bundlePath: String) {
		logger.log("Extracting Blender bundle for Linux from: $bundlePath")
	}
	
	/**
	 * Downloads a file from a given URL and saves it to the specified destination path.
	 *
	 * @param url source URL to download.
	 * @param destination destination path on disk.
	 * @return `Unit`.
	 */
	fun downloadFile(url: String, destination: String) {
	
	}
	
	companion object {}
}
