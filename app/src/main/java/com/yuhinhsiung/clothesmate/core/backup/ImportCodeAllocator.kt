package com.yuhinhsiung.clothesmate.core.backup

/**
 * 导入时的编号分配（R02 根因修复）。
 *
 * 编号的唯一性口径不能只看本机表：备份清单里已经退役、持有人已物理删除的号
 * 同样永久占用。若冲突顺延交给只认本机库的 CodeAllocator，备份历史里的号会被
 * 重新发出，导致“同一个号两个持有人”。这里把四类占用统一成纯集合判定：
 * - 本机现存/软删除衣物 + 本机全部历史；
 * - 备份内其他衣物当前编号；
 * - 备份内历史编号（持有人不是当前旧衣物时不可复用）；
 * - 本次导入已分配的编号。
 */
internal object ImportCodeAllocator {
    private const val MAX_CODE = 9_999_999L

    fun allocate(
        preferred: String,
        oldItemId: Long,
        backupCodesByItem: Map<Long, String>,
        backupHistoryHolder: Map<String, Long>,
        occupiedCodes: Set<String>,
        assignedCodes: Set<String>,
    ): String {
        fun available(candidate: String): Boolean {
            if (candidate.isBlank() || candidate in assignedCodes || candidate in occupiedCodes) return false
            if (backupCodesByItem.any { (id, code) -> id != oldItemId && code == candidate }) return false
            val holder = backupHistoryHolder[candidate]
            if (holder != null && holder != oldItemId) return false
            return true
        }

        val normalized = preferred.trim()
        if (normalized.isNotEmpty() && available(normalized)) return normalized

        var candidate = 1L
        while (candidate <= MAX_CODE && !available(candidate.toString())) candidate++
        if (candidate > MAX_CODE) throw BackupException("导入失败：没有可用的衣物编号")
        return candidate.toString()
    }
}
