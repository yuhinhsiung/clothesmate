package com.yuhinhsiung.clothesmate.core.update

import com.yuhinhsiung.clothesmate.BuildConfig
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

interface UpdateRepository {
    suspend fun check(currentVersionName: String = BuildConfig.VERSION_NAME): UpdateCheckResult
}

class DefaultUpdateRepository @Inject constructor(
    private val api: GitHubReleaseApi,
) : UpdateRepository {

    override suspend fun check(currentVersionName: String): UpdateCheckResult {
        val current = SemanticVersion.parse(currentVersionName)
            ?: return UpdateCheckResult.Failure(UpdateFailure.INVALID_RESPONSE)
        val release = try {
            api.latest(BuildConfig.GITHUB_OWNER, BuildConfig.GITHUB_REPO)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            return UpdateCheckResult.Failure(
                if (failure is retrofit2.HttpException) UpdateFailure.HTTP else UpdateFailure.NETWORK,
            )
        }
        if (release.draft || release.prerelease) {
            return UpdateCheckResult.NoApk(NoApkReason.INVALID_TAG)
        }
        val tagVersion = SemanticVersion.parse(release.tag_name)
            ?: return UpdateCheckResult.NoApk(NoApkReason.INVALID_TAG)
        if (release.tag_name.removePrefix("v") != tagVersion.toString()) {
            return UpdateCheckResult.NoApk(NoApkReason.INVALID_TAG)
        }
        if (tagVersion <= current) return UpdateCheckResult.UpToDate(tagVersion)

        val candidates = release.assets.filter { asset ->
            val name = asset.name.lowercase()
            name.startsWith("clothesmate-") && name.endsWith(".apk") &&
                !name.contains("debug") && !name.contains("unsigned")
        }
        if (candidates.isEmpty()) return UpdateCheckResult.NoApk(NoApkReason.NO_APK)
        if (candidates.size > 1) return UpdateCheckResult.NoApk(NoApkReason.AMBIGUOUS_APK)
        val asset = candidates.single()
        // R07：资产名必须按版本 token 的完整边界匹配，不能用 contains。
        // 否则 v1.0.1 + clothesmate-1.0.10.apk 会被误当成同一版本。
        if (!assetNameMatchesVersion(asset.name, tagVersion.toString())) {
            return UpdateCheckResult.NoApk(NoApkReason.TAG_ASSET_MISMATCH)
        }
        if (release.html_url.isBlank() || asset.browser_download_url.isBlank()) {
            return UpdateCheckResult.Failure(UpdateFailure.INVALID_RESPONSE)
        }
        return UpdateCheckResult.Available(
            UpdateInfo(
                version = tagVersion,
                tag = release.tag_name,
                versionName = tagVersion.toString(),
                downloadUrl = asset.browser_download_url,
                assetName = asset.name,
                assetSizeBytes = asset.size,
                assetDigest = asset.digest,
                releasePageUrl = release.html_url,
            ),
        )
    }

    private fun assetNameMatchesVersion(name: String, version: String): Boolean {
        val token = Regex.escape(version)
        return Regex(
            "^clothesmate-$token(?:-(?:arm64|arm64-v8a|armeabi-v7a|x86_64|x86))?\\.apk$",
            RegexOption.IGNORE_CASE,
        ).matches(name)
    }
}
