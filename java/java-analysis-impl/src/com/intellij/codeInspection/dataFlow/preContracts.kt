/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.dataFlow

import com.intellij.codeInsight.NullableNotNullManager
import com.intellij.codeInspection.dataFlow.ContractInferenceInterpreter.negateConstraint
import com.intellij.codeInspection.dataFlow.ContractInferenceInterpreter.withConstraint
import com.intellij.codeInspection.dataFlow.MethodContract.ValueConstraint.*
import com.intellij.codeInspection.dataFlow.instructions.MethodCallInstruction
import com.intellij.psi.*
import com.siyeh.ig.psiutils.SideEffectChecker

/**
 * @author peter
 */
interface PreContract {
  fun toContracts(method: PsiMethod, body: PsiCodeBlock): List<MethodContract>
  fun negate(): PreContract? = NegatingContract(this)
}

internal data class KnownContract(val contract: MethodContract) : PreContract {
  override fun toContracts(method: PsiMethod, body: PsiCodeBlock) = listOf(contract)
  override fun negate() = negateContract(contract)?.let(::KnownContract)
}

internal data class DelegationContract(private val expression: ExpressionRange, private val negated: Boolean) : PreContract {

  override fun toContracts(method: PsiMethod, body: PsiCodeBlock): List<MethodContract> {
    val call = expression.restoreExpression(body) as PsiMethodCallExpression? ?: return emptyList()

    val result = call.resolveMethodGenerics()
    val targetMethod = result.element as PsiMethod? ?: return emptyList()

    val parameters = targetMethod.parameterList.parameters
    val arguments = call.argumentList.expressions
    val varArgCall = MethodCallInstruction.isVarArgCall(targetMethod, result.substitutor, arguments, parameters)

    val fromDelegate = ControlFlowAnalyzer.getMethodContracts(targetMethod).mapNotNull { dc ->
      convertDelegatedMethodContract(method, parameters, arguments, varArgCall, dc)
    }
    if (NullableNotNullManager.isNotNull(targetMethod)) {
      return fromDelegate.map { returnNotNull(it) } + listOf(MethodContract(emptyConstraints(method), NOT_NULL_VALUE))
    }
    return fromDelegate
  }

  private fun convertDelegatedMethodContract(callerMethod: PsiMethod,
                                             targetParameters: Array<PsiParameter>,
                                             callArguments: Array<PsiExpression>,
                                             varArgCall: Boolean,
                                             targetContract: MethodContract): MethodContract? {
    var answer: Array<MethodContract.ValueConstraint>? = emptyConstraints(callerMethod)
    for (i in targetContract.arguments.indices) {
      if (i >= callArguments.size) return null
      val argConstraint = targetContract.arguments[i]
      if (argConstraint != ANY_VALUE) {
        if (varArgCall && i >= targetParameters.size - 1) {
          if (argConstraint == NULL_VALUE) {
            return null
          }
          break
        }

        val argument = callArguments[i]
        val paramIndex = resolveParameter(callerMethod, argument)
        if (paramIndex >= 0) {
          answer = withConstraint(answer, paramIndex, argConstraint) ?: return null
        }
        else if (argConstraint != getLiteralConstraint(argument)) {
          return null
        }
      }
    }
    val returnValue = if (negated) negateConstraint(targetContract.returnValue) else targetContract.returnValue
    return answer?.let { MethodContract(it, returnValue) }
  }

  private fun emptyConstraints(method: PsiMethod) = MethodContract.createConstraintArray(method.parameterList.parametersCount)

  private fun returnNotNull(mc: MethodContract) = if (mc.returnValue == THROW_EXCEPTION) mc else MethodContract(mc.arguments, NOT_NULL_VALUE)

  private fun getLiteralConstraint(argument: PsiExpression) = when (argument) {
    is PsiLiteralExpression -> ContractInferenceInterpreter.getLiteralConstraint(argument.getFirstChild().node.elementType)
    else -> null
  }

  private fun resolveParameter(method: PsiMethod, expr: PsiExpression): Int {
    val target = if (expr is PsiReferenceExpression && !expr.isQualified) expr.resolve() else null
    return if (target is PsiParameter && target.parent === method.parameterList) method.parameterList.getParameterIndex(target) else -1
  }
}

internal data class SideEffectFilter(private val expressionsToCheck: List<ExpressionRange>, private val contracts: List<PreContract>) : PreContract {

  override fun toContracts(method: PsiMethod, body: PsiCodeBlock): List<MethodContract> {
    if (expressionsToCheck.any { d -> mayHaveSideEffects(body, d) }) {
      return emptyList()
    }
    return contracts.flatMap { c -> c.toContracts(method, body) }
  }

  private fun mayHaveSideEffects(body: PsiCodeBlock, range: ExpressionRange) =
      range.restoreExpression(body)?.let { SideEffectChecker.mayHaveSideEffects(it) } ?: false
}

internal data class NegatingContract(private val negated: PreContract) : PreContract {
  override fun toContracts(method: PsiMethod, body: PsiCodeBlock) = negated.toContracts(method, body).mapNotNull(::negateContract)
}

private fun negateContract(c: MethodContract): MethodContract? {
  val ret = c.returnValue
  return if (ret == TRUE_VALUE || ret == FALSE_VALUE) MethodContract(c.arguments, negateConstraint(ret)) else null
}

internal data class MethodCallContract(private val call: ExpressionRange, private val states: List<Array<MethodContract.ValueConstraint>>) : PreContract {

  override fun toContracts(method: PsiMethod, body: PsiCodeBlock): List<MethodContract> {
    val target = (call.restoreExpression(body) as PsiMethodCallExpression?)?.resolveMethod()
    if (target != null && NullableNotNullManager.isNotNull(target)) {
      return ContractInferenceInterpreter.toContracts(states, NOT_NULL_VALUE)
    }
    return emptyList()
  }
}
