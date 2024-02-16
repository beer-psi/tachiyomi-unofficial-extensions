package io.github.beerpsi.tachiyomi.extension.all.mangaplus

class Trie {
    data class Node(var word: String? = null, val childNodes: MutableMap<Char, Node> = mutableMapOf())

    private val root = Node()

    fun insert(word: String) {
        var currentNode = root
        for (char in word) {
            if (currentNode.childNodes[char] == null) {
                currentNode.childNodes[char] = Node()
            }
            currentNode = currentNode.childNodes[char]!!
        }
        currentNode.word = word
    }

    fun longestPrefix(): String {
        return buildString {
            var currentNode = root

            while (currentNode.childNodes.size == 1) {
                val entry = currentNode.childNodes.entries.first()

                append(entry.key)
                currentNode = entry.value
            }
        }
    }
}
