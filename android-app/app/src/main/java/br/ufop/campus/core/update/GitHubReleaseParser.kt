package br.ufop.campus.core.update

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest
import java.util.Locale

/**
 * Metadados de uma versão Android publicada em `NuncaMorto/UFOP-App` (GitHub Releases).
 */
data class AppUpdateReleaseInfo(
    val tagName: String,
    val versionName: String,
    val versionCode: Int?,
    val title: String,
    val releaseNotes: String,
    val isPrerelease: Boolean,
    val publishedAt: String,
    val htmlUrl: String,
    val apkName: String,
    val apkDownloadUrl: String,
    val apkSizeBytes: Long,
    val expectedSha256: String?,
    val checksumAssetUrl: String?
)

object GitHubReleaseParser {

    const val REPO_OWNER = "NuncaMorto"
    const val REPO_NAME = "UFOP-App"
    const val RELEASES_API_URL =
        "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases?per_page=10"

    private val VERSION_CODE_COMMENT_REGEX = Regex(
        """<!--\s*versionCode\s*:\s*(\d+)\s*-->""",
        RegexOption.IGNORE_CASE
    )
    private val VERSION_CODE_INLINE_REGEX = Regex(
        """\bversionCode\s*[:=]\s*(\d+)\b""",
        RegexOption.IGNORE_CASE
    )
    private val HTML_COMMENT_REGEX = Regex("""<!--[\s\S]*?-->""")
    private val SHA256_HEX_REGEX = Regex("""^[0-9a-f]{64}$""")
    private val CHECKSUM_LINE_REGEX = Regex("""([0-9a-fA-F]{64})\s+\*?([^\s]+)""")
    private val CHECKSUM_ONLY_REGEX = Regex("""\b([0-9a-fA-F]{64})\b""")

    /**
     * Analisa o JSON de `/repos/.../releases` e retorna a atualização aplicável mais recente para Android.
     */
    fun parseLatestApplicableRelease(
        releasesJson: String,
        currentVersionName: String,
        currentVersionCode: Int,
        includePrereleases: Boolean = true
    ): AppUpdateReleaseInfo? {
        val trimmed = releasesJson.trim()
        if (trimmed.isEmpty()) return null

        val array = JSONArray(trimmed)
        var bestCandidate: AppUpdateReleaseInfo? = null

        for (i in 0 until array.length()) {
            val releaseObj = array.optJSONObject(i) ?: continue
            val parsed = parseSingleRelease(releaseObj) ?: continue

            if (!includePrereleases && parsed.isPrerelease) continue

            val isNewer = isNewerRelease(
                candidateVersionName = parsed.versionName,
                candidateVersionCode = parsed.versionCode,
                currentVersionName = currentVersionName,
                currentVersionCode = currentVersionCode
            )
            if (!isNewer) continue

            if (bestCandidate == null || isCandidateHigherThan(parsed, bestCandidate)) {
                bestCandidate = parsed
            }
        }
        return bestCandidate
    }

    internal fun parseSingleRelease(releaseObj: JSONObject): AppUpdateReleaseInfo? {
        if (releaseObj.optBoolean("draft", false)) return null

        val tagName = releaseObj.optString("tag_name", "").trim()
        if (tagName.isEmpty()) return null

        val assets = releaseObj.optJSONArray("assets") ?: return null
        val selectedApk = selectBestApkAsset(assets) ?: return null

        val rawDownloadUrl = selectedApk.optString("browser_download_url", "").trim()
        if (!isTrustedGitHubUrl(rawDownloadUrl)) return null

        val apkName = selectedApk.optString("name", "").trim()
        if (apkName.isEmpty()) return null

        val rawHtmlUrl = releaseObj.optString("html_url", "").trim()
        val safeHtmlUrl = if (isTrustedGitHubUrl(rawHtmlUrl)) {
            rawHtmlUrl
        } else {
            "https://github.com/$REPO_OWNER/$REPO_NAME/releases/tag/$tagName"
        }

        val rawBody = releaseObj.optString("body", "")
        val parsedVersionCode = extractVersionCode(rawBody)
        val cleanNotes = stripHtmlComments(rawBody)

        val digestHex = parseDigestField(selectedApk.optString("digest", ""))
        val checksumUrl = findMatchingChecksumUrl(assets, apkName)

        return AppUpdateReleaseInfo(
            tagName = tagName,
            versionName = normalizeVersionName(tagName),
            versionCode = parsedVersionCode,
            title = releaseObj.optString("name", "").trim().ifEmpty { "Vida Acadêmica UFOP $tagName" },
            releaseNotes = cleanNotes,
            isPrerelease = releaseObj.optBoolean("prerelease", false) || tagName.contains("-"),
            publishedAt = releaseObj.optString("published_at", "").trim(),
            htmlUrl = safeHtmlUrl,
            apkName = apkName,
            apkDownloadUrl = rawDownloadUrl,
            apkSizeBytes = selectedApk.optLong("size", 0L).coerceAtLeast(0L),
            expectedSha256 = digestHex,
            checksumAssetUrl = checksumUrl
        )
    }

    private fun selectBestApkAsset(assets: JSONArray): JSONObject? {
        var debugApk: JSONObject? = null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name", "").trim()
            val lower = name.lowercase(Locale.ROOT)
            if (!lower.endsWith(".apk")) continue
            val url = asset.optString("browser_download_url", "").trim()
            if (!isTrustedGitHubUrl(url)) continue

            if (!lower.contains("-debug")) {
                return asset
            }
            if (debugApk == null) {
                debugApk = asset
            }
        }
        return debugApk
    }

    private fun findMatchingChecksumUrl(assets: JSONArray, apkName: String): String? {
        val exactShaName = "$apkName.sha256"
        var sumsUrl: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name", "").trim()
            val url = asset.optString("browser_download_url", "").trim()
            if (!isTrustedGitHubUrl(url)) continue
            if (name.equals(exactShaName, ignoreCase = true)) {
                return url
            }
            if (name.equals("SHA256SUMS.txt", ignoreCase = true)) {
                sumsUrl = url
            }
        }
        return sumsUrl
    }

    private fun parseDigestField(rawDigest: String): String? {
        val clean = rawDigest.trim().lowercase(Locale.ROOT)
        val hex = if (clean.startsWith("sha256:")) clean.removePrefix("sha256:") else clean
        return hex.takeIf { SHA256_HEX_REGEX.matches(it) }
    }

    fun extractVersionCode(releaseBody: String): Int? {
        VERSION_CODE_COMMENT_REGEX.find(releaseBody)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
            return it
        }
        return VERSION_CODE_INLINE_REGEX.find(releaseBody)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    fun stripHtmlComments(markdown: String): String =
        markdown.replace(HTML_COMMENT_REGEX, "").trim()

    fun normalizeVersionName(tagOrVersion: String): String =
        tagOrVersion.trim().removePrefix("v").removePrefix("V")

    /**
     * Compara prioridade dando precedência absoluta ao `versionCode` quando informado na Release
     * (garantindo transição limpa `1.9.7` [code 24] -> `1.0.0` [code 25+]).
     */
    fun isNewerRelease(
        candidateVersionName: String,
        candidateVersionCode: Int?,
        currentVersionName: String,
        currentVersionCode: Int
    ): Boolean {
        if (candidateVersionCode != null) {
            return candidateVersionCode > currentVersionCode
        }
        return compareSemver(candidateVersionName, currentVersionName) > 0
    }

    private fun isCandidateHigherThan(
        candidate: AppUpdateReleaseInfo,
        currentBest: AppUpdateReleaseInfo
    ): Boolean {
        val cCode = candidate.versionCode
        val bCode = currentBest.versionCode
        if (cCode != null && bCode != null && cCode != bCode) {
            return cCode > bCode
        }
        if (cCode != null && bCode == null) return true
        if (cCode == null && bCode != null) return false
        return compareSemver(candidate.versionName, currentBest.versionName) > 0
    }

    fun compareSemver(leftRaw: String, rightRaw: String): Int {
        val (leftCore, leftPre) = splitSemver(leftRaw)
        val (rightCore, rightPre) = splitSemver(rightRaw)
        val maxLen = maxOf(leftCore.size, rightCore.size)
        for (idx in 0 until maxLen) {
            val l = leftCore.getOrElse(idx) { 0 }
            val r = rightCore.getOrElse(idx) { 0 }
            if (l != r) return l.compareTo(r)
        }
        return when {
            leftPre == null && rightPre != null -> 1
            leftPre != null && rightPre == null -> -1
            leftPre == null && rightPre == null -> 0
            else -> leftPre!!.compareTo(rightPre!!)
        }
    }

    private fun splitSemver(raw: String): Pair<List<Int>, String?> {
        val normalized = normalizeVersionName(raw)
        val parts = normalized.split("-", limit = 2)
        val numbers = parts[0]
            .split(".")
            .map { token -> token.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
        val prerelease = parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
        return numbers to prerelease
    }

    /**
     * Extrai o hash SHA-256 esperado de `SHA256SUMS.txt` ou `<arquivo>.apk.sha256`.
     */
    fun extractSha256FromChecksumText(checksumText: String, targetFileName: String): String? {
        val lines = checksumText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null

        val targetBase = targetFileName.substringAfterLast('/').trim()
        for (line in lines) {
            val match = CHECKSUM_LINE_REGEX.find(line) ?: continue
            val hash = match.groupValues[1].lowercase(Locale.ROOT)
            val file = match.groupValues[2].substringAfterLast('/').trim()
            if (file.equals(targetBase, ignoreCase = true) && SHA256_HEX_REGEX.matches(hash)) {
                return hash
            }
        }

        if (lines.size == 1) {
            val hex = CHECKSUM_ONLY_REGEX.find(lines[0])?.groupValues?.getOrNull(1)?.lowercase(Locale.ROOT)
            if (hex != null && SHA256_HEX_REGEX.matches(hex)) {
                return hex
            }
        }
        return null
    }

    fun verifySha256Hex(actualHex: String, expectedHex: String): Boolean {
        val actual = actualHex.trim().lowercase(Locale.ROOT)
        val expected = expectedHex.trim().lowercase(Locale.ROOT)
        if (!SHA256_HEX_REGEX.matches(actual) || !SHA256_HEX_REGEX.matches(expected)) {
            return false
        }
        return MessageDigest.isEqual(actual.toByteArray(Charsets.US_ASCII), expected.toByteArray(Charsets.US_ASCII))
    }

    fun isTrustedGitHubUrl(rawUrl: String): Boolean {
        val clean = rawUrl.trim()
        if (clean.isEmpty()) return false
        val uri = try {
            URI(clean)
        } catch (_: Exception) {
            return false
        }
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        return host == "github.com" ||
            host == "api.github.com" ||
            host.endsWith(".github.com") ||
            host == "githubusercontent.com" ||
            host.endsWith(".githubusercontent.com")
    }
}
