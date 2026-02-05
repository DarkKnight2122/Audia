package com.oakiha.audia.utils

/**
 * Resolves directory allow/deny rules using the nearest ancestor match strategy.
 * A more specific rule (longer path) always overrides a parent rule. Allowed rules
 * take precedence over blocked rules at the same depth to enable explicit overrides
 * inside excluded trees.
 */
class DirectoryRuleResolver(
    allowed: Set<String>,
    blocked: Set<String>
) {
    // Normalize paths: remove trailing slashes to ensure consistent matching
    // We assume paths are absolute and start with /
    private val allowedRoots = allowed.mapNotNull { normalize(it) }.toSet()
    private val blockedRoots = blocked.mapNotNull { normalize(it) }.toSet()

    private val hasAllowedRules = allowedRoots.isNotEmpty()

    fun isBlocked(path: String): Boolean {
        // If no allowed directories are set, we block everything (Deny by Default)
        if (!hasAllowedRules) return true
        
        // Logic:
        // 1. Must be inside an "Allowed" tree.
        // 2. Must NOT be inside a more specific "Blocked" tree.

        var deepestBlockLen = -1
        var deepestAllowLen = -1

        // Find the most specific "Allowed" rule
        for (root in allowedRoots) {
            if (isParentOrSame(root, path)) {
                if (root.length > deepestAllowLen) {
                    deepestAllowLen = root.length
                }
            }
        }

        // If not matched by any allow rule, it's blocked by default
        if (deepestAllowLen == -1) return true

        // Find the most specific "Blocked" rule
        for (root in blockedRoots) {
            if (isParentOrSame(root, path)) {
                if (root.length > deepestBlockLen) {
                    deepestBlockLen = root.length
                }
            }
        }

        // It's blocked if the most specific rule is a "blocked" rule
        // (Nested Block > Nested Allow)
        return deepestBlockLen > deepestAllowLen
    }

    private fun normalize(path: String?): String? {
        if (path.isNullOrBlank()) return null
        return if (path.endsWith("/")) path.dropLast(1) else path
    }

    private fun isParentOrSame(root: String, path: String): Boolean {
        if (!path.startsWith(root, ignoreCase = true)) return false
        // It starts with root. Check if it's exactly root or a subdirectory (slash after root)
        // Check needs to safeguard bounds
        if (path.length == root.length) return true
        return path[root.length] == '/'
    }
}
