package br.ufop.campus

import android.content.pm.PackageInstaller
import br.ufop.campus.core.update.AppUpdateManager
import br.ufop.campus.core.update.GitHubReleaseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusAppUpdaterTest {

    @Test
    fun `parses latest applicable beta release with versionCode and digest`() {
        val sampleJson = """
            [
              {
                "tag_name": "v1.9.7",
                "name": "Vida Acadêmica v1.9.7",
                "draft": false,
                "prerelease": true,
                "published_at": "2026-11-22T15:00:00Z",
                "html_url": "https://github.com/NuncaMorto/Vida-Academica/releases/tag/v1.9.7",
                "body": "## Novidades\n- Atualizador automático\n<!-- versionCode: 24 -->",
                "assets": [
                  {
                    "name": "Vida-Academica-v1.9.7-debug.apk",
                    "size": 29100000,
                    "digest": "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                    "browser_download_url": "https://github.com/NuncaMorto/Vida-Academica/releases/download/v1.9.7/Vida-Academica-v1.9.7-debug.apk"
                  },
                  {
                    "name": "SHA256SUMS.txt",
                    "size": 108,
                    "browser_download_url": "https://github.com/NuncaMorto/Vida-Academica/releases/download/v1.9.7/SHA256SUMS.txt"
                  }
                ]
              }
            ]
        """.trimIndent()

        assertEquals("Vida-Academica", GitHubReleaseParser.REPO_NAME)

        val found = GitHubReleaseParser.parseLatestApplicableRelease(
            releasesJson = sampleJson,
            currentVersionName = "1.9.6",
            currentVersionCode = 23,
            includePrereleases = true
        )
        assertNotNull(found)
        assertEquals("v1.9.7", found?.tagName)
        assertEquals(24, found?.versionCode)
        assertEquals(
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            found?.expectedSha256
        )
        assertFalse(found?.releaseNotes?.contains("versionCode") == true)

        val blockedBeta = GitHubReleaseParser.parseLatestApplicableRelease(
            releasesJson = sampleJson,
            currentVersionName = "1.9.6",
            currentVersionCode = 23,
            includePrereleases = false
        )
        assertNull(blockedBeta)
    }

    @Test
    fun `versionCode takes precedence over versionName for 1_9_7 to 1_0_0 transition`() {
        assertTrue(
            GitHubReleaseParser.isNewerRelease(
                candidateVersionName = "1.0.0",
                candidateVersionCode = 25,
                currentVersionName = "1.9.7",
                currentVersionCode = 24
            )
        )
        assertFalse(
            GitHubReleaseParser.isNewerRelease(
                candidateVersionName = "1.0.0",
                candidateVersionCode = 24,
                currentVersionName = "1.9.7",
                currentVersionCode = 24
            )
        )
    }

    @Test
    fun `prefers release apk over debug apk when both exist`() {
        val sampleJson = """
            [
              {
                "tag_name": "v1.0.0",
                "name": "v1.0.0 Oficial",
                "draft": false,
                "prerelease": false,
                "body": "<!-- versionCode: 25 -->",
                "assets": [
                  {
                    "name": "Vida-Academica-v1.0.0-debug.apk",
                    "size": 28000000,
                    "browser_download_url": "https://github.com/NuncaMorto/Vida-Academica/releases/download/v1.0.0/Vida-Academica-v1.0.0-debug.apk"
                  },
                  {
                    "name": "Vida-Academica-v1.0.0.apk",
                    "size": 14000000,
                    "browser_download_url": "https://github.com/NuncaMorto/Vida-Academica/releases/download/v1.0.0/Vida-Academica-v1.0.0.apk"
                  }
                ]
              }
            ]
        """.trimIndent()

        val found = GitHubReleaseParser.parseLatestApplicableRelease(
            releasesJson = sampleJson,
            currentVersionName = "1.9.7",
            currentVersionCode = 24,
            includePrereleases = false
        )
        assertEquals("Vida-Academica-v1.0.0.apk", found?.apkName)
    }

    @Test
    fun `extracts sha256 from SHA256SUMS and verifies trusted urls`() {
        val hashA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val hashB = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        val sums = """
            $hashA  Vida-Academica-v1.9.7-debug.apk
            $hashB  Vida-Academica-v1.9.7-unsigned.ipa
        """.trimIndent()

        assertEquals(
            hashA,
            GitHubReleaseParser.extractSha256FromChecksumText(sums, "Vida-Academica-v1.9.7-debug.apk")
        )
        assertTrue(GitHubReleaseParser.verifySha256Hex(hashA.uppercase(), hashA))
        assertFalse(GitHubReleaseParser.verifySha256Hex(hashB, hashA))

        assertTrue(
            GitHubReleaseParser.isTrustedGitHubUrl(
                "https://objects.githubusercontent.com/github-production-release-asset/foo.apk"
            )
        )
        assertFalse(GitHubReleaseParser.isTrustedGitHubUrl("http://github.com/insecure.apk"))
        assertFalse(GitHubReleaseParser.isTrustedGitHubUrl("https://evil-github.com/fake.apk"))
    }

    @Test
    fun `shouldPurgeUpdateApk retains fresh pending apk until target version is installed`() {
        val nowMs = 1_795_400_000_000L
        val freshModifiedMs = nowMs - (30L * 60L * 1000L) // 30 minutos atrás
        val staleModifiedMs = nowMs - (25L * 60L * 60L * 1000L) // 25 horas atrás

        // Mantém o APK pendente enquanto o app ainda está na versão antiga (25 < 26)
        assertFalse(
            AppUpdateManager.shouldPurgeUpdateApk(
                fileLastModifiedMs = freshModifiedMs,
                nowMs = nowMs,
                isActiveState = false,
                isPendingApk = true,
                installedVersionCode = 25,
                pendingTargetVersionCode = 26
            )
        )

        // Apaga assim que a versão alvo foi instalada (26 >= 26)
        assertTrue(
            AppUpdateManager.shouldPurgeUpdateApk(
                fileLastModifiedMs = freshModifiedMs,
                nowMs = nowMs,
                isActiveState = false,
                isPendingApk = true,
                installedVersionCode = 26,
                pendingTargetVersionCode = 26
            )
        )

        // Apaga APK pendente expirado (> 24h)
        assertTrue(
            AppUpdateManager.shouldPurgeUpdateApk(
                fileLastModifiedMs = staleModifiedMs,
                nowMs = nowMs,
                isActiveState = false,
                isPendingApk = true,
                installedVersionCode = 25,
                pendingTargetVersionCode = 26
            )
        )

        // Nunca apaga durante estado ativo de download/instalação
        assertFalse(
            AppUpdateManager.shouldPurgeUpdateApk(
                fileLastModifiedMs = staleModifiedMs,
                nowMs = nowMs,
                isActiveState = true,
                isPendingApk = false,
                installedVersionCode = 25,
                pendingTargetVersionCode = 26
            )
        )

        // Apaga arquivos residuais que não sejam o APK pendente
        assertTrue(
            AppUpdateManager.shouldPurgeUpdateApk(
                fileLastModifiedMs = freshModifiedMs,
                nowMs = nowMs,
                isActiveState = false,
                isPendingApk = false,
                installedVersionCode = 25,
                pendingTargetVersionCode = 26
            )
        )
    }

    @Test
    fun `mapInstallFailureMessage handles user abort and returns actionable diagnostics`() {
        assertNull(AppUpdateManager.mapInstallFailureMessage(PackageInstaller.STATUS_FAILURE_ABORTED, "User cancelled"))

        val conflict = AppUpdateManager.mapInstallFailureMessage(PackageInstaller.STATUS_FAILURE_CONFLICT, null)
        assertNotNull(conflict)
        assertTrue(conflict!!.contains("assinatura", ignoreCase = true))

        val storage = AppUpdateManager.mapInstallFailureMessage(PackageInstaller.STATUS_FAILURE_STORAGE, null)
        assertNotNull(storage)
        assertTrue(storage!!.contains("Espaço insuficiente", ignoreCase = true))

        val blocked = AppUpdateManager.mapInstallFailureMessage(PackageInstaller.STATUS_FAILURE_BLOCKED, null)
        assertNotNull(blocked)
        assertTrue(blocked!!.contains("bloqueada", ignoreCase = true))

        val incompatible = AppUpdateManager.mapInstallFailureMessage(PackageInstaller.STATUS_FAILURE_INCOMPATIBLE, null)
        assertNotNull(incompatible)
        assertTrue(incompatible!!.contains("compatível", ignoreCase = true))

        val invalid = AppUpdateManager.mapInstallFailureMessage(PackageInstaller.STATUS_FAILURE_INVALID, null)
        assertNotNull(invalid)
        assertTrue(invalid!!.contains("inválido", ignoreCase = true))

        val generic = AppUpdateManager.mapInstallFailureMessage(PackageInstaller.STATUS_FAILURE, "INSTALL_FAILED_INTERNAL_ERROR")
        assertNotNull(generic)
        assertTrue(generic!!.contains("INSTALL_FAILED_INTERNAL_ERROR"))
    }

    @Test
    fun `isNewerRelease handles multi-digit patch transition 1_9_9 to 1_9_10`() {
        assertTrue(
            GitHubReleaseParser.isNewerRelease(
                candidateVersionName = "1.9.10",
                candidateVersionCode = 27,
                currentVersionName = "1.9.9",
                currentVersionCode = 26
            )
        )
        assertTrue(
            GitHubReleaseParser.isNewerRelease(
                candidateVersionName = "1.9.10",
                candidateVersionCode = null,
                currentVersionName = "1.9.9",
                currentVersionCode = 26
            )
        )
        assertTrue(GitHubReleaseParser.compareSemver("1.9.10", "1.9.9") > 0)
    }
}
