package org.ucombinator.jade.classfile

/**
 * Tracks the enclosing/enclosed relationships among all classes parsed from input files.
 *
 * @property childrenMap Maps each class name to the internal names of its direct inner classes.
 *   Only classes that have at least one inner class appear as keys.
 */
data class ClassHierarchy(
  val childrenMap: Map<String, List<String>>,
) {
  private val innerClasses: Set<String> = childrenMap.values.flatten().toSet()

  /** Returns `true` if [name] is a top-level (non-inner) class. */
  fun isTopLevel(name: String): Boolean = name !in innerClasses

  /** Returns the direct inner class names of [name], or an empty list if none. */
  fun childrenOf(name: String): List<String> = childrenMap[name] ?: emptyList()
}
