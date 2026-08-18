package org.ucombinator.jade.decompile

import com.github.javaparser.ast.Node
import com.github.javaparser.ast.expr.*
import com.github.javaparser.ast.stmt.*
//import org.ucombinator.jade.decompile.applyPropagation

object Elimination {
  val liveInStates = mutableMapOf<Statement, Set<String>>()
  val liveOutStates = mutableMapOf<Statement, Set<String>>()

  fun make(statement: BlockStmt): BlockStmt {
    predecessors.clear()
    successors.clear()
    buildGraph(statement)
    // println(predecessors)
    backwardAnalyse(statement)
    val res = removeUneeded(statement)
    return removeEmptyStmt(res)
  }
  data class DataflowGraph(
    val entry: Statement,
    val predecessors: Map<Statement, Set<Statement>>,
    val successors: Map<Statement, Set<Statement>>,
    val nodes: Set<Statement>
  )
  val successors = mutableMapOf<Statement, MutableSet<Statement>>()
  val predecessors = mutableMapOf<Statement, MutableSet<Statement>>()
  val nodes = mutableSetOf<Statement>()

  fun buildGraph(statement: BlockStmt): DataflowGraph {
    build(statement, null)
    return DataflowGraph(statement.statements[0], predecessors, successors, nodes)
  }
//
fun build(statement: BlockStmt, exit: Statement?) {
//    statement.walk { stmt -> println("walking.. stmt is $stmt") }
  val statements = statement.statements
  for (i in statements.indices) {
    val curr = statements[i]
//      println("processing $curr children is:")
//      println(curr.walk { node -> println("within curr, node is $node") })
    val next =
      if (i + 1 < statements.size) {
        statements[i + 1]
      } else {
        exit
      }
    when (curr) {
      is LabeledStmt -> { // these are the targets
        val inner = curr.statement
        edge(curr, inner)
//          println("type of labelled statement is ${curr.statement.javaClass}")
        if (inner is WhileStmt) {
          val whileStmt = inner as WhileStmt
          val body = whileStmt.body
          if (body is BlockStmt && body.statements.isNonEmpty) {
            edge(inner, body.statements[0])
            build(body, curr) // loops back
          }
          if (next != null) {
            edge(curr, next)
          }
        } else if (inner is BlockStmt) {
          if (inner.statements.isNonEmpty) {
            edge(inner, inner.statements[0])
            build(inner, next)
          } else if (next != null) {
            edge(curr, next)
          }

        }

      }
      is WhileStmt -> {
        val body = curr.body
        if (body is BlockStmt && body.statements.isNonEmpty) {
          edge(curr, body.statements[0])
          build(body, curr) // loops back
        }
        if (next != null) {
          edge(curr, next)
        }
      }
      is BlockStmt -> {
        if (curr.statements.isNotEmpty()) {
          edge(curr, curr.statements[0])
          build(curr, next)
        } else if (next != null) {
          edge(curr, next)
        }

      }

      else -> if (next != null) {
        edge(curr, next)
      }
    }
  }
}

  fun backwardAnalyse(statement: BlockStmt) {
    val statements = statement.statements
    if (statement.isEmpty) {
      return
    }
    val workList: ArrayDeque<Statement> = ArrayDeque()
    statements.forEach {
      liveInStates[it] = mutableSetOf()
      liveOutStates[it] = mutableSetOf()
      workList.add(it)
    } // initialize
    while (!workList.isEmpty()) {
      // backwards traversal, from last stmt
      val stmt = workList.removeLast()
      // println("processing $stmt")

      when (stmt) {
        is LabeledStmt -> {
          when (val inner = stmt.statement) {
            is BlockStmt -> backwardAnalyse(inner)
            is WhileStmt -> {
              if (inner.body is BlockStmt) {
                val innerBlk = inner.body as BlockStmt
                backwardAnalyse(innerBlk)
              }
            }
          }
        }

      }
      val successor = successors[stmt] ?: emptySet()
//      println("successor of $stmt is $successor")
      val outgoing =
        if (successor.isEmpty()) {
          mutableSetOf()
        } else {
          for (s in successor) {
            if (s is BlockStmt) {
              // recursively process block (backwards)
              backwardAnalyse(s)
            }
          }
          // gather live vars from successors, indicates we still need them
          successor.map { liveInStates[it] ?: emptySet() }.reduce{ acc, set -> (acc + set).toMutableSet()}
        }
//      println("setting liveout as $outgoing")
      liveOutStates[stmt] = outgoing // liveIn of successors is liveOut of curr stmt
//      println("+++ now with $stmt +++")
      val newIn = backTransfer(stmt, outgoing)
//      println("newin has $newIn")

      if (newIn != liveInStates[stmt]) {
        // if updated, reprocess predecessor since values required has changed
        liveInStates[stmt] = newIn.toMutableSet()
        predecessors[stmt]?.forEach {
            v ->
          // println("predecessor is $v !!!!!")
          if (!workList.contains(v)) {
            // println("adding $v to the list!!!!!")
            workList.add(v)
          }
        }
      }
    }
  }
  private fun edge(source: Statement, target: Statement) {
    successors.getOrPut(source) { mutableSetOf()}.add(target)
    predecessors.getOrPut(target) { mutableSetOf()}.add(source)
    nodes.add(source)
    nodes.add(target)
  }

  fun backTransfer(statement: Statement, state: Set<String>): Set<String> {
    val res = state.toMutableSet() // live vars going out to successors
    when (statement) {
      is ExpressionStmt -> {
        when (val expr = statement.expression) {
          is AssignExpr -> {
            val target = expr.target
            if (target is NameExpr && res.contains(target.nameAsString)) {
//              res.remove(target.nameAsString)
              // vars used on rhs are live
              res.addAll(processVars(expr.value))
            }
          }
          is VariableDeclarationExpr -> {
            for (v in expr.variables) {
              res.remove(v.nameAsString)
              val initialVal = v.initializer.orElse(null)
              if (initialVal != null) {
                // add those vars assigned to target
                res.addAll(processVars(initialVal))
              }
            }
          }
          else -> res.addAll(processVars(expr))
        }
      }
//      is LabeledStmt -> return backTransfer(statement.statement, res)
//      is BlockStmt -> return res
      is ReturnStmt -> statement.expression.ifPresent { res.addAll(processVars(it)) }
      is WhileStmt -> res.addAll(processVars(statement.condition))
      is IfStmt -> {
        // println("in a if condition with $statement")
        res.addAll(processVars(statement.condition))
      }
    }
    return res // liveIn for this, = liveOut of predecessor
  }
  fun processVars(expression: Expression): Set<String> {
    // println("===== processing $expression =====")
    val vars = mutableSetOf<String>()
    expression.walk { node ->
      if (node is NameExpr) {
        val isQualified =
          node.parentNode.map {
            it is FieldAccessExpr && it.name == node
          }.orElse(false)
        if (!isQualified) {
          // println("=== adding $node ===")
          vars.add(node.nameAsString)
        }
      }
    }
    return vars
  }
  fun removeUneeded(statement: BlockStmt): BlockStmt {
    val cloned = statement.clone()
    val toRemove = mutableListOf<Statement>()
//    println("liveout is $liveOutStates")
    removal(statement, cloned, toRemove)
    toRemove.forEach { it.replace(EmptyStmt()) }
    return cloned
  }
  fun removal(original: Node, cloned:Node, set: MutableList<Statement>) {
    if (original is Statement && cloned is Statement) {
      val liveOut = liveOutStates[original]?: emptySet()
//      println("processing $original with $cloned")
//      println("liveout has ${liveOutStates[original]}")
//      cloned.walk(ExpressionStmt::class.java) { node ->
        if (cloned is ExpressionStmt) {
          val expr = cloned.expression
          when (expr) {
            is AssignExpr -> {
              val target = expr.target
              if (target is NameExpr && !liveOut.contains(target.nameAsString)) {
                // target is not needed in future statements, can remove this assignexpr
                set.add(cloned)
              }
            }
            is VariableDeclarationExpr -> {
              val vars = expr.variables
              // if any var on rhs is needed, don't delete this declaration
              val varUsage = vars.any{ v ->
                liveOut.contains(v.nameAsString)}
              if (!varUsage) {
                set.add(cloned)
              }
            }
          }
        }
//      }
      for (i in original.childNodes.indices) {
        if (i < cloned.childNodes.size) {
          removal(original.childNodes[i], cloned.childNodes[i], set)
        }
      }
    }
  }
//  fun removeUneeded(statement: BlockStmt): BlockStmt {
//    val toRemove = mutableSetOf<Statement>()
//    val statements = statement.clone().statements
//    statements.forEachIndexed { idx, stmt ->
//      val liveOut = liveOutStates[statement.statements[idx]] ?: emptySet()
//      stmt.walk {node ->
//        if (node is ExpressionStmt) {
//          val expr = node.expression
//          when (expr) {
//            is AssignExpr -> {
//              val target = expr.target
////              println("processing ${target}")
////              println("liveout has: ${liveOut}")
//              if (target is NameExpr && !liveOut.contains(target.nameAsString)) {
//                toRemove.add(node)
//              }
//            }
//            is VariableDeclarationExpr -> {
//              val vars = expr.variables
//              val varUsage = vars.any{ v -> println(liveOut.contains(v.nameAsString))
//                println("processing $v with $liveOut")
//                liveOut.contains(v.nameAsString)}
//              if (!varUsage) {
//                toRemove.add(node)
//              }
//            }
//          }
//        }
//      }
//
//
//    }
//    return BlockStmt(statements)
//  }
//  fun removeUneeded(statement: BlockStmt): BlockStmt {
//    val statements = statement.clone().statements
//    statements.forEachIndexed { idx, stmt ->
//      val liveOut = liveOutStates[statement.statements[idx]] ?: emptySet()
//      stmt.walk {node ->
//        if (node is ExpressionStmt) {
//          val expr = node.expression
//          when (expr) {
//            is AssignExpr -> {
//              val target = expr.target
////              println("processing ${target}")
////              println("liveout has: ${liveOut}")
//              if (target is NameExpr && !liveOut.contains(target.nameAsString)) {
//                node.replace(EmptyStmt())
//              }
//            }
//            is VariableDeclarationExpr -> {
//              val vars = expr.variables
//              val varUsage = vars.any{ v -> println(liveOut.contains(v.nameAsString))
//                println("processing $v with $liveOut")
//                liveOut.contains(v.nameAsString)}
//              if (!varUsage) {
//                node.replace(EmptyStmt())
//              }
//            }
//          }
//        }
//      }
//
//
//    }
//    return BlockStmt(statements)
//  }

  fun removeEmptyStmt(statement: BlockStmt): BlockStmt {
    val statements = statement.clone().statements
    val emptyStmts = mutableListOf<EmptyStmt>()
    statements.forEach { stmt ->
      stmt.walk { node ->
        if (node is EmptyStmt) {
          emptyStmts.add(node)
        }
      }
    }
    emptyStmts.forEach { it.remove() }
    return BlockStmt(statements)
  }
}