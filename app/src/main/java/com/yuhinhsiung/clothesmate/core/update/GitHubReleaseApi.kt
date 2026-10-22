package com.yuhinhsiung.clothesmate.core.update

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

@Serializable
data class GitHubReleaseDto(
    val tag_name: String = "",
    val name: String? = null,
    val html_url: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAssetDto> = emptyList(),
)

@Serializable
data class GitHubAssetDto(
    val name: String = "",
    val size: Long = 0,
    val browser_download_url: String = "",
    /** GitHub 对公开资产返回 `sha256:<hex>`；缺失时只能走浏览器下载。 */
    val digest: String? = null,
)

interface GitHubReleaseApi {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun latest(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Accept") accept: String = "application/vnd.github+json",
        @Header("X-GitHub-Api-Version") apiVersion: String = "2022-11-28",
    ): GitHubReleaseDto
}
