// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.uast.generate.UastElementFactory
import org.jetbrains.uast.kotlin.generate.KotlinUastBaseCodeGenerationPlugin
import org.jetbrains.uast.kotlin.generate.KotlinUastElementFactory

class FirKotlinUastCodeGenerationPlugin : KotlinUastBaseCodeGenerationPlugin() {
  @OptIn(KtAllowAnalysisOnEdt::class)
  override fun shortenReference(sourcePsi: KtElement): PsiElement {
    val ktFile = sourcePsi.containingKtFile
    allowAnalysisOnEdt {
      analyze(ktFile) {
        collectPossibleReferenceShortenings(ktFile, sourcePsi.textRange)
      }
    }
    //todo apply shortening
    return sourcePsi
  }

  override fun getElementFactory(project: Project): UastElementFactory {
    return object : KotlinUastElementFactory(project) {
      override fun PsiType?.suggestName(context: PsiElement?): String {
        TODO("Not yet implemented")
      }
    }
  }
}