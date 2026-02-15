package org.ucombinator.jade.decompile

import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.expr.AssignExpr
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.NameExpr
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt
import com.github.javaparser.ast.stmt.ExpressionStmt
import com.github.javaparser.ast.stmt.Statement
import org.ucombinator.jade.util.Log

/**
 * Reorders statements so that super()/this() becomes the first statement as required by Java.
 * Replaces copyVar arguments with their original parameter sources.
 */
object RewriteConstructorCalls {
  private val log = Log {}

  /**
   * Builds a mapping from copyVar names to their source expressions.
   * Scans assignment statements like `copyVar3_1 = parameter1` and creates a map.
   */
  private fun buildCopyVarMapping(statements: List<Statement>): Map<String, Expression> =
    statements
      .filterIsInstance<ExpressionStmt>()
      .map { it.expression }
      .filterIsInstance<AssignExpr>()
      .filter { it.target is NameExpr && it.operator == AssignExpr.Operator.ASSIGN }
      .associate { (it.target as NameExpr).nameAsString to it.value }

  /**
   * Resolves an expression by replacing copyVars with their source expressions.
   */
  private fun resolveExpression(expr: Expression, copyVarMap: Map<String, Expression>): Expression =
    when (expr) {
      is NameExpr -> copyVarMap[expr.nameAsString]?.clone() ?: expr.clone()
      else -> expr.clone()
    }

  fun make(statements: NodeList<Statement>): NodeList<Statement> {
    val stmtList = statements.toList()
    val copyVarMap = buildCopyVarMapping(stmtList)
    val constructorCallIndex = stmtList.indexOfFirst { stmt ->
      stmt is ExplicitConstructorInvocationStmt
    }
    
    if (constructorCallIndex == -1) return statements
    val originalConstructorCall = stmtList[constructorCallIndex] as ExplicitConstructorInvocationStmt
    
    // Create new constructor call with resolved arguments
    val newArgs = originalConstructorCall.arguments
      .map { resolveExpression(it, copyVarMap) }
      .let { NodeList(it) }
    
    val newConstructorCall = ExplicitConstructorInvocationStmt(
      originalConstructorCall.isThis,
      originalConstructorCall.expression.orElse(null),
      newArgs
    )
    
    // Build result: super()/this() first, then all other statements
    val otherStatements = stmtList
      .filterIndexed { index, _ -> index != constructorCallIndex }
    
    return NodeList(listOf(newConstructorCall) + otherStatements)
  }
}
