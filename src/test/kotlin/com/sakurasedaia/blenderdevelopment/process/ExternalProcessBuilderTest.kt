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

package com.sakurasedaia.blenderdevelopment.process

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.time.Duration
import java.util.concurrent.TimeUnit

class ExternalProcessBuilderTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  fun testLaunchAndCaptureOutputReturnsFailureForMissingExecutable() {
    val result = ExternalProcessBuilder(project).launchAndCaptureOutput(
      command = "/path/that/does/not/exist/blender-runtime",
    )

    assertNull(result.exitCode)
    assertFalse(result.cancelled)
    assertNotNull(result.failure)
    assertTrue(result.output.isEmpty())
    assertTrue(result.firstLine.isEmpty())
  }

  fun testFirstLineReturnsFirstLineFromOutput() {
    val result = ExternalProcessBuilder.ProcessExecutionResult(
      command = "blender",
      args = listOf("--version"),
      output = "Blender 4.5.8\nbuild hash: abc123",
      exitCode = 0,
      cancelled = false,
    )

    assertEquals("Blender 4.5.8", result.firstLine)
  }

  fun testLaunchAndCaptureOutputCompletesWithoutTerminationFlags() {
    val command = successfulCommand()

    val result = ExternalProcessBuilder(project).launchAndCaptureOutput(
      command = command.executable,
      args = command.arguments,
      timeout = Duration.ofSeconds(5),
    )

    assertEquals(0, result.exitCode)
    assertFalse(result.cancelled)
    assertFalse(result.timedOut)
    assertNull(result.failure)
  }

  fun testLaunchAndCaptureOutputTerminatesProcessAfterTimeout() {
    val command = sleepingCommand()
    val startedAtNanos = System.nanoTime()

    val result = ExternalProcessBuilder(project).launchAndCaptureOutput(
      command = command.executable,
      args = command.arguments,
      timeout = Duration.ofMillis(250),
    )

    val elapsed = Duration.ofNanos(System.nanoTime() - startedAtNanos)
    assertTrue("Timed-out process should return promptly", elapsed < Duration.ofSeconds(4))
    assertTrue(result.timedOut)
    assertFalse(result.cancelled)
    assertNull(result.failure)
  }

  fun testLaunchAndCaptureOutputTerminatesProcessAfterCancellation() {
    val command = sleepingCommand()
    val startedAtNanos = System.nanoTime()

    val result = ExternalProcessBuilder(project).launchAndCaptureOutput(
      command = command.executable,
      args = command.arguments,
      shouldCancel = { true },
    )

    val elapsed = Duration.ofNanos(System.nanoTime() - startedAtNanos)
    assertTrue("Cancelled process should return promptly", elapsed < Duration.ofSeconds(4))
    assertTrue(result.cancelled)
    assertFalse(result.timedOut)
    assertNull(result.failure)
  }

  fun testLaunchAndCaptureOutputRejectsNegativeTimeout() {
    assertThrows(IllegalArgumentException::class.java) {
      ExternalProcessBuilder(project).launchAndCaptureOutput(
        command = successfulCommand().executable,
        timeout = Duration.ofMillis(-1),
      )
    }
  }

  fun testZeroTimeoutDisablesTimeout() {
    val command = successfulCommand()

    val result = ExternalProcessBuilder(project).launchAndCaptureOutput(
      command = command.executable,
      args = command.arguments,
      timeout = Duration.ZERO,
    )

    assertEquals(0, result.exitCode)
    assertFalse(result.cancelled)
    assertFalse(result.timedOut)
  }

  fun testAsyncListOverloadForwardsTimeout() {
    val command = sleepingCommand()

    val result = ExternalProcessBuilder(project).launchAndCaptureOutputAsync(
      command = command.executable,
      args = command.arguments,
      timeout = Duration.ofMillis(250),
    ).get(5, TimeUnit.SECONDS)

    assertTrue(result.timedOut)
    assertFalse(result.cancelled)
  }

  private fun successfulCommand(): TestCommand {
    return if (isWindows()) {
      TestCommand("cmd.exe", listOf("/c", "exit", "0"))
    }
    else {
      TestCommand("/bin/sh", listOf("-c", "exit 0"))
    }
  }

  private fun sleepingCommand(): TestCommand {
    return if (isWindows()) {
      TestCommand("cmd.exe", listOf("/c", "ping", "-n", "6", "127.0.0.1"))
    }
    else {
      TestCommand("/bin/sh", listOf("-c", "sleep 5"))
    }
  }

  private fun isWindows(): Boolean = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

  private data class TestCommand(
    val executable: String,
    val arguments: List<String>,
  )
}
