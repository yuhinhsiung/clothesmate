package com.yuhinhsiung.clothesmate.core.update

sealed interface UpdateCheckResult {
    data class UpToDate(val latest: SemanticVersion) : UpdateCheckResult

    data class Available(val info: UpdateInfo) : UpdateCheckResult

    data class NoApk(val reason: NoApkReason) : UpdateCheckResult

    data class Failure(val reason: UpdateFailure) : UpdateCheckResult
}

enum class NoApkReason {
    NO_APK,
    AMBIGUOUS_APK,
    TAG_ASSET_MISMATCH,
    INVALID_TAG,
}

enum class UpdateFailure {
    NETWORK,
    HTTP,
    INVALID_RESPONSE,
}

data class UpdateInfo(
    val version: SemanticVersion,
    val tag: String,
    val versionName: String,
    val downloadUrl: String,
    val assetName: String,
    val assetSizeBytes: Long,
    val assetDigest: String?,
    val releasePageUrl: String,
) {
    /** 无可信 SHA-256 时只允许浏览器下载，不进入应用内安装。 */
    val hasTrustedDigest: Boolean
        get() = assetDigest?.startsWith("sha256:", ignoreCase = true) == true &&
            assetDigest.length == "sha256:".length + 64 &&
            assetDigest.drop(7).all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
}
