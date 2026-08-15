package com.sakurasedaia.blenderdevelopment.ui.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.sakurasedaia.blenderdevelopment.state.PluginConfig
import com.sakurasedaia.blenderdevelopment.ui.MessageBundle
import java.awt.Container
import javax.swing.JComponent

internal class BlenderSettingsFactoryTest : BasePlatformTestCase() {
  fun testPreservesSearchableConfigurableContract() {
    val factory = BlenderSettingsFactory()

    assertEquals("com.sakurasedaia.blenderdevelopment.settings.plugin", factory.id)
    assertEquals(MessageBundle.message("ui.settings.title"), factory.displayName)
    assertTrue(Configurable.NoScroll::class.java.isAssignableFrom(factory.javaClass))
  }

  fun testRepeatedCreateReusesComponentAndResetsDraft() {
    PluginConfig.getInstance().setBlenderInstallPath("/configured/blender")
    val factory = BlenderSettingsFactory()
    val firstComponent = factory.createComponent()
    pathFields(firstComponent).first().text = "/draft/blender"
    assertTrue(factory.isModified)

    val secondComponent = factory.createComponent()

    assertSame(firstComponent, secondComponent)
    assertFalse(factory.isModified)
    assertEquals("/configured/blender", pathFields(secondComponent).first().text)
  }

  fun testApplyAndResetDelegateToFeatureComponent() {
    val config =
        PluginConfig.getInstance().apply {
          setBlenderInstallPath("/original/blender")
        }
    val factory = BlenderSettingsFactory()
    val component = factory.createComponent()
    val blenderPath = pathFields(component).first()

    blenderPath.text = "/applied/blender"
    factory.apply()
    assertEquals("/applied/blender", config.getBlenderInstallPath())
    assertFalse(factory.isModified)

    blenderPath.text = "/discarded/blender"
    factory.reset()
    assertEquals("/applied/blender", blenderPath.text)
    assertFalse(factory.isModified)
  }

  fun testDisposeIsRepeatableAndNextCreateUsesFreshComponent() {
    val factory = BlenderSettingsFactory()
    val firstComponent = factory.createComponent()

    factory.disposeUIResources()
    factory.disposeUIResources()

    assertFalse(factory.isModified)
    val secondComponent = factory.createComponent()
    assertNotSame(firstComponent, secondComponent)
  }

  private fun pathFields(component: JComponent): List<TextFieldWithBrowseButton> =
      descendantsOf(component).filterIsInstance<TextFieldWithBrowseButton>()

  private fun descendantsOf(container: Container): List<java.awt.Component> =
      container.components.flatMap { child ->
        listOf(child) + if (child is Container) descendantsOf(child) else emptyList()
      }
}
