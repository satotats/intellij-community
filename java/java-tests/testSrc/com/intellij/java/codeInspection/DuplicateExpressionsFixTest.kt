// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.duplicateExpressions.DuplicateExpressionsInspection
import com.intellij.java.JavaBundle
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

/**
 * @author Pavel.Dolgov
 */
class DuplicateExpressionsFixTest : LightJavaCodeInsightFixtureTestCase() {
  val inspection = DuplicateExpressionsInspection()

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(inspection)
  }

  override fun getBasePath() = JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/duplicateExpressionsFix"

  fun testIntroduceVariable() = doTest(introduce("s.substring(s.length() - 9)"))
  fun testReuseVariable() = doTest(reuse("substr", "s.substring(s.length() - 9)"))
  fun testReplaceOthers() = doTest(replace("substr", "s.substring(s.length() - 9)"))
  fun testIntroduceVariableOtherVariableNotInScope() = doTest(introduce("s.substring(s.length() - 9)"))
  fun testVariableNotInScopeCantReplaceOthers() = doNegativeTest(replace("substr", "s.substring(s.length() - 9)"))
  fun testIntroducePathVariableTwoPathOf() = doTest(introduce("Path.of(fileName)"))
  fun testIntroducePathVariableTwoPathsGet() = doTest(introduce("Paths.get(fileName)"))
  fun testIntroducePathVariablePathOfPathsGet() = doTest(introduce("Paths.get(fileName)"))

  private fun doTest(message: String, threshold: Int = 50) =
    withThreshold(threshold) {
      myFixture.configureByFile("${getTestName(false)}.java")
      myFixture.launchAction(myFixture.findSingleIntention(message))
      myFixture.checkResultByFile("${getTestName(false)}_after.java")
    }

  private fun doNegativeTest(message: String, threshold: Int = 50) =
    withThreshold(threshold) {
      myFixture.configureByFile("${getTestName(false)}.java")
      val intentions = myFixture.filterAvailableIntentions(message)
      assertEquals(emptyList<IntentionAction>(), intentions)
    }

  private fun withThreshold(threshold: Int, block: () -> Unit) {
    val oldThreshold = inspection.complexityThreshold
    try {
      inspection.complexityThreshold = threshold
      block()
    }
    finally {
      inspection.complexityThreshold = oldThreshold
    }
  }

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return JAVA_11
  }

  private fun introduce(expr: String) =
    JavaBundle.message("inspection.duplicate.expressions.introduce.variable.fix.name", expr)

  private fun reuse(name: String, expr: String) =
    JavaBundle.message("inspection.duplicate.expressions.reuse.variable.fix.name", name, expr)

  private fun replace(name: String, expr: String) =
    JavaBundle.message("inspection.duplicate.expressions.replace.other.occurrences.fix.name", name, expr)
}
