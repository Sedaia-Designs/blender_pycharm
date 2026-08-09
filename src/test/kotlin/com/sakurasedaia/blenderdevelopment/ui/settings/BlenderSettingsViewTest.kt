package com.sakurasedaia.blenderdevelopment.ui.settings

import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import java.awt.Container
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

internal class BlenderSettingsViewTest : BasePlatformTestCase() {
  fun testRenderAndReadFormRoundTripEveryField() {
    val view = createView()
    val form = form()

    view.renderForm(form)

    assertEquals(form, view.readForm())
  }

  fun testUsesVerticalOnlyScrollingAndEmbedsVersionComponent() {
    val versionComponent = JPanel()
    val view = createView(versionComponent)
    val scrollPane = view.component() as JBScrollPane

    assertEquals(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, scrollPane.verticalScrollBarPolicy)
    assertEquals(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER, scrollPane.horizontalScrollBarPolicy)
    assertTrue(descendantsOf(scrollPane).contains(versionComponent))
  }

  fun testMinimumVersionDraftAndAccessibleLabelArePreserved() {
    val view = createView()
    view.renderForm(form().copy(minimumBlenderVersion = "invalid"))
    val minimumVersionField = minimumVersionField(view)

    assertEquals("invalid", minimumVersionField.text)
    assertEquals(
      MessageBundle.message("ui.settings.group.versions.minimum-version.label"),
      minimumVersionField.accessibleContext.accessibleName,
    )
  }

  private fun form(): BlenderSettingsForm = BlenderSettingsForm(
    blenderInstallPath = "/blender",
    codeCompletionPath = "/completion",
    logPath = "/logs",
    downloadPath = "/downloads",
    clearDownloadsAfterInstall = false,
    minimumBlenderVersion = "4.5",
    globalEnvironmentVariables = mapOf("BLENDER_USER_SCRIPTS" to "/scripts"),
  )

  private fun createView(versionComponent: JPanel = JPanel()): BlenderSettingsView =
    BlenderSettingsView(versionComponent, isValidMinorVersion = { true })

  private fun minimumVersionField(view: BlenderSettingsView): JBTextField {
    val pathTextFields = descendantsOf(view.component())
      .filterIsInstance<TextFieldWithBrowseButton>()
      .map { it.textField }
      .toSet()
    return descendantsOf(view.component())
      .filterIsInstance<JBTextField>()
      .single { it !in pathTextFields }
  }

  private fun descendantsOf(container: Container): List<java.awt.Component> =
    container.components.flatMap { component ->
      listOf(component) + if (component is Container) descendantsOf(component) else emptyList()
    }
}
