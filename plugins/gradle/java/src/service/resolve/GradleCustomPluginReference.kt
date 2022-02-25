package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.model.SingleTargetReference
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral

class GradleCustomPluginReference(
  private val myElement: GrLiteral,
  private val fileFound: VirtualFile,
) : SingleTargetReference(), PsiSymbolReference {

  override fun resolveSingleTarget(): Symbol? {
    val psiPluginFile = PsiManager.getInstance(myElement.project).findFile(fileFound)
                        ?: return null
    return GradleCustomPluginSymbol(psiPluginFile)
  }

  override fun getElement() = myElement

  override fun getRangeInElement() = TextRange(1, myElement.text.length - 1)
}