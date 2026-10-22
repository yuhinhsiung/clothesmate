package com.yuhinhsiung.clothesmate.core.update

/**
 * 只接受正式版 `vX.Y.Z` / `X.Y.Z`：预发布、缺段、越界都返回 null。
 *
 * M5-16 首发不支持 beta/rc 通道；minor/patch 0..99 与 versionCode 单射公式一致。
 */
data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<SemanticVersion> {

    override fun compareTo(other: SemanticVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        fun parse(raw: String?): SemanticVersion? {
            if (raw.isNullOrBlank()) return null
            val value = raw.trim()
            // 预发布/构建元数据（1.2.3-beta、1.2.3+meta）不在首发通道内。
            if (value.contains('-') || value.contains('+')) return null
            val normalized = if (value.startsWith("v") || value.startsWith("V")) value.drop(1) else value
            val parts = normalized.split('.')
            if (parts.size != 3) return null
            val numbers = parts.map { part ->
                if (part.isEmpty() || !part.all(Char::isDigit)) return null
                part.toIntOrNull() ?: return null
            }
            if (numbers[0] < 0 || numbers[1] !in 0..99 || numbers[2] !in 0..99) return null
            return SemanticVersion(numbers[0], numbers[1], numbers[2])
        }
    }
}
