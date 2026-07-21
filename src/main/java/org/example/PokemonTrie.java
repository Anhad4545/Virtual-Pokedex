package org.example;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
public class PokemonTrie {
    private static final int MAX_SUGGESTIONS = 8;
    private static class TrieNode {
        final Map<Character, TrieNode> children = new TreeMap<>();
        boolean isEndOfWord = false;
    }
    private final TrieNode root = new TrieNode();
    public void insert(String word) {
        TrieNode current = root;
        for (char ch : word.toLowerCase().toCharArray()) {
            current.children.putIfAbsent(ch, new TrieNode());
            current = current.children.get(ch);
        }
        current.isEndOfWord = true;
    }
    public List<String> getSuggestions(String prefix) {
        List<String> results = new ArrayList<>();
        TrieNode current = root;
        String lowerPrefix = prefix.toLowerCase();
        for (char ch : lowerPrefix.toCharArray()) {
            if (!current.children.containsKey(ch)) return results; 
            current = current.children.get(ch);
        }
        dfs(current, lowerPrefix, results);
        return results;
    }
    private void dfs(TrieNode node, String currentWord, List<String> results) {
        if (results.size() >= MAX_SUGGESTIONS) return;
        if (node.isEndOfWord) results.add(capitalize(currentWord));
        for (Map.Entry<Character, TrieNode> entry : node.children.entrySet()) {
            dfs(entry.getValue(), currentWord + entry.getKey(), results);
        }
    }
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}
