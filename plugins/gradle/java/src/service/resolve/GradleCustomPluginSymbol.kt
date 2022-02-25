package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.model.Pointer
import com.intellij.navigation.NavigatableSymbol
import com.intellij.navigation.NavigationTarget
import com.intellij.navigation.SymbolNavigationService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class GradleCustomPluginSymbol(
  private val psiFile: PsiFile,
) : NavigatableSymbol {
  override fun createPointer() = Pointer.hardPointer(this)

  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> {
    return listOf(SymbolNavigationService.getInstance().psiFileNavigationTarget(psiFile))
  }
}