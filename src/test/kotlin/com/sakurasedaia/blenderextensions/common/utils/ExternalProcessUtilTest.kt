package com.sakurasedaia.blenderextensions.common.utils

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ExternalProcessUtilTest : BasePlatformTestCase() {

    fun testExecuteCommandCancellation() {
        val indicator = EmptyProgressIndicator()
        val command = if (System.getProperty("os.name").lowercase().contains("win")) {
            GeneralCommandLine("ping", "127.0.0.1", "-n", "10")
        } else {
            GeneralCommandLine("sleep", "10")
        }

        val cancelled = AtomicBoolean(false)
        val threadFinished = CountDownLatch(1)

        val thread = Thread {
            try {
                ProgressManager.getInstance().runProcess({
                    ExternalProcessUtil.executeCommand(command)
                }, indicator)
            } catch (e: ProcessCanceledException) {
                cancelled.set(true)
            } finally {
                threadFinished.countDown()
            }
        }
        thread.start()

        // Wait a bit for the process to start
        Thread.sleep(500)
        indicator.cancel()

        threadFinished.await(5, TimeUnit.SECONDS)
        assertTrue("Task should be cancelled", cancelled.get())
        // Also check if process is really destroyed? Hard to do without keeping handle.
    }

    fun testExecAndGetOutput() {
        val command = if (System.getProperty("os.name").lowercase().contains("win")) {
            GeneralCommandLine("cmd.exe", "/c", "echo", "hello")
        } else {
            GeneralCommandLine("echo", "hello")
        }
        val output = ExternalProcessUtil.execAndGetOutput(command)
        assertEquals(0, output.exitCode)
        assertTrue(output.stdout.contains("hello"))
    }
}
