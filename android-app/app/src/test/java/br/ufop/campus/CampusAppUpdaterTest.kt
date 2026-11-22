package br.ufop.campus

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
                "name": "Vida Acadêmica UFOP v1.9.7",
                "draft": false,
                "prerelease": true,
                "published_at": "2026-11-22T15:00:00Z",
                "html_url": "https://github.com/NuncaMorto/UFOP-App/releases/tag/v1.9.7",
                "body": "## Novidades\n- Atualizador automático\n<!-- versionCode: 24 -->",
                "assets": [
                  {
                    "name": "Vida-Academica-UFOP-v1.9.7-debug.apk",
                    "size": 29100000,
                    "digest": "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                    "browser_download_url": "https://github.com/NuncaMorto/UFOP-App/releases/download/v1.9.7/Vida-Academica-UFOP-v1.9.7-debug.apk"
                  },
                  {
                    "name": "SHA256SUMS.txt",
                    "size": 108,
                    "browser_download_url": "https://github.com/NuncaMorto/UFOP-App/releases/download/v1.9.7/SHA256SUMS.txt"
                  }
                ]
              }
            ]
        """.trimIndent()

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
                    "name": "Vida-Academica-UFOP-v1.0.0-debug.apk",
                    "size": 28000000,
                    "browser_download_url": "https://github.com/NuncaMorto/UFOP-App/releases/download/v1.0.0/Vida-Academica-UFOP-v1.0.0-debug.apk"
                  },
                  {
                    "name": "Vida-Academica-UFOP-v1.0.0.apk",
                    "size": 14000000,
                    "browser_download_url": "https://github.com/NuncaMorto/UFOP-App/releases/download/v1.0.0/Vida-Academica-UFOP-v1.0.0.apk"
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
        assertEquals("Vida-Academica-UFOP-v1.0.0.apk", found?.apkName)
    }

    @Test
    fun `extracts sha256 from SHA256SUMS and verifies trusted urls`() {
        val hashA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val hashB = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        val sums = """
            $hashA  Vida-Academica-UFOP-v1.9.7-debug.apk
            $hashB  Vida-Academica-UFOP-v1.9.7-unsigned.ipa
        """.trimIndent()

        assertEquals(
            hashA,
            GitHubReleaseParser.extractSha256FromChecksumText(sums, "Vida-Academica-UFOP-v1.9.7-debug.apk")
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
}
