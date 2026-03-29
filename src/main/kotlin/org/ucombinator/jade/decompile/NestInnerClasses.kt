package org.ucombinator.jade.decompile

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.BodyDeclaration
import com.github.javaparser.ast.body.ConstructorDeclaration

object NestInnerClasses {
  fun nestChild(
    parentName: String,
    childName: String,
    compilationUnits: Map<String, CompilationUnit>,
  ) {
    val parentType = compilationUnits[parentName]?.types?.firstOrNull() ?: return
    val childType = compilationUnits[childName]?.types?.firstOrNull() as? BodyDeclaration<*> ?: return
    val insertIndex = parentType.members
      .indexOfFirst { it is ConstructorDeclaration }
      .takeIf { it >= 0 }
      ?: parentType.members.size //TODO: Check the style guide for the order of the members of the class
    parentType.members.add(insertIndex, childType)
  }
}
