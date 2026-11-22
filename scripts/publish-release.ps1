<#
.SYNOPSIS
    Cria ou atualiza uma Release no GitHub (NuncaMorto/UFOP-App) com APK, IPA, SHA256SUMS.txt e patch notes do CHANGELOG.md.
.PARAMETER Tag
    Tag da release (ex.: v1.9.6). Quando omitida, usa v<versionName> de android-app/app/build.gradle.kts.
.PARAMETER NotesFile
    Arquivo opcional com as notas da release. Quando omitido, extrai a seção correspondente de CHANGELOG.md.
.PARAMETER Draft
    Publica a release como rascunho.
.PARAMETER Prerelease
    Marca a release como pre-release.
.PARAMETER AndroidOnly
    Ignora pacotes .ipa e publica apenas o APK Android.
#>
param(
    [string]$Tag,
    [string]$NotesFile,
    [switch]$Draft,
    [switch]$Prerelease,
    [switch]$AndroidOnly
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command "gh" -ErrorAction SilentlyContinue)) {
    throw "GitHub CLI ('gh') não encontrada no PATH."
}

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$BuildGradleKts = Join-Path $RepoRoot "android-app\app\build.gradle.kts"
$ChangelogPath = Join-Path $RepoRoot "CHANGELOG.md"
$DistDir = Join-Path $RepoRoot "dist"

if (-not $Tag) {
    if (-not (Test-Path $BuildGradleKts)) {
        throw "Arquivo '$BuildGradleKts' não encontrado para inferir a versão."
    }
    $GradleText = Get-Content -Path $BuildGradleKts -Raw -Encoding UTF8
    if ($GradleText -match 'versionName\s*=\s*"([^"]+)"') {
        $Tag = "v$($Matches[1])"
    } else {
        throw "Não foi possível ler versionName em '$BuildGradleKts'. Informe -Tag explicitamente."
    }
} elseif (-not $Tag.StartsWith("v")) {
    $Tag = "v$Tag"
}

$VersionBare = $Tag.Substring(1)

$VersionCode = $null
if (Test-Path $BuildGradleKts) {
    $GradleMeta = Get-Content -Path $BuildGradleKts -Raw -Encoding UTF8
    if ($GradleMeta -match 'versionCode\s*=\s*(\d+)') {
        $VersionCode = $Matches[1]
    }
}

$ReleaseApkPath = Join-Path $DistDir "Vida-Academica-UFOP-$Tag.apk"
$DebugApkPath = Join-Path $DistDir "Vida-Academica-UFOP-$Tag-debug.apk"
$ApkPath = if (Test-Path $ReleaseApkPath) { $ReleaseApkPath } else { $DebugApkPath }
$IpaPath = Join-Path $DistDir "Vida-Academica-UFOP-$Tag-unsigned.ipa"

$Assets = New-Object System.Collections.Generic.List[string]
if (Test-Path $ApkPath) { $Assets.Add($ApkPath) }
if (-not $AndroidOnly -and (Test-Path $IpaPath)) { $Assets.Add($IpaPath) }

if ($Assets.Count -eq 0) {
    throw "Nenhum pacote encontrado em '$DistDir' para a versão '$Tag' (esperado '$ApkPath' ou '$IpaPath')."
}

$TempDir = Join-Path ([IO.Path]::GetTempPath()) ("ufop_release_" + [Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Force -Path $TempDir | Out-Null

try {
    # Gera SHA256SUMS.txt apenas com os hashes e nomes base dos arquivos anexados
    $SumLines = foreach ($Asset in $Assets) {
        $Hash = (Get-FileHash -LiteralPath $Asset -Algorithm SHA256).Hash.ToLowerInvariant()
        "$Hash  $(Split-Path -Leaf $Asset)"
    }
    $ShaFile = Join-Path $TempDir "SHA256SUMS.txt"
    [IO.File]::WriteAllText($ShaFile, (($SumLines -join "`n") + "`n"), [Text.UTF8Encoding]::new($false))
    $Assets.Add($ShaFile)

    # Resolve notas da release a partir de NotesFile ou CHANGELOG.md
    $ResolvedNotesPath = $NotesFile
    if (-not $ResolvedNotesPath) {
        $ExtractedNotes = $null
        if (Test-Path $ChangelogPath) {
            $Lines = Get-Content -Path $ChangelogPath -Encoding UTF8
            $HeaderPattern = "^##\s+(?:\[)?v?$([regex]::Escape($VersionBare))(?:\b|\]|\s|$)"
            $Capture = New-Object System.Collections.Generic.List[string]
            $InSection = $false
            foreach ($Line in $Lines) {
                if (-not $InSection) {
                    if ($Line -match $HeaderPattern) {
                        $InSection = $true
                        $Capture.Add($Line)
                    }
                } else {
                    if ($Line -match '^##\s+') {
                        break
                    }
                    $Capture.Add($Line)
                }
            }
            if ($Capture.Count -gt 0) {
                while ($Capture.Count -gt 0 -and ($Capture[$Capture.Count - 1].Trim() -eq "" -or $Capture[$Capture.Count - 1].Trim() -eq "---")) {
                    $Capture.RemoveAt($Capture.Count - 1)
                }
                $ExtractedNotes = ($Capture -join "`n") + "`n"
            }
        }
        if (-not $ExtractedNotes) {
            $ExtractedNotes = "## Vida Acadêmica UFOP $Tag`n`nRelease publicada a partir de \`dist/\`."
        }
        if ($VersionCode -and $ExtractedNotes -notmatch '<!--\s*versionCode\s*:') {
            $ExtractedNotes = $ExtractedNotes.TrimEnd() + "`n`n<!-- versionCode: $VersionCode -->`n"
        }
        $ResolvedNotesPath = Join-Path $TempDir "release-notes.md"
        [IO.File]::WriteAllText($ResolvedNotesPath, $ExtractedNotes, [Text.UTF8Encoding]::new($false))
    }

    $Title = "Vida Acadêmica UFOP $Tag"

    $PreviousPref = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    & gh release view $Tag 2>$null | Out-Null
    $ReleaseExists = ($LASTEXITCODE -eq 0)
    $ErrorActionPreference = $PreviousPref

    if ($ReleaseExists) {
        Write-Host "==> Atualizando Release existente $Tag..." -ForegroundColor Cyan
        & gh release upload $Tag @Assets --clobber
        if ($LASTEXITCODE -ne 0) {
            throw "Falha ao enviar assets para a Release '$Tag' (exit code $LASTEXITCODE)."
        }
        $EditArgs = @("release", "edit", $Tag, "--title", $Title, "--notes-file", $ResolvedNotesPath)
        if ($Prerelease) { $EditArgs += "--prerelease" } else { $EditArgs += "--prerelease=false" }
        & gh @EditArgs
        if ($LASTEXITCODE -ne 0) {
            throw "Falha ao atualizar título/notas da Release '$Tag' (exit code $LASTEXITCODE)."
        }
    } else {
        Write-Host "==> Criando nova Release $Tag..." -ForegroundColor Cyan
        $CreateArgs = @("release", "create", $Tag) + $Assets + @("--title", $Title, "--notes-file", $ResolvedNotesPath)
        if ($Draft) { $CreateArgs += "--draft" }
        if ($Prerelease) { $CreateArgs += "--prerelease" }
        & gh @CreateArgs
        if ($LASTEXITCODE -ne 0) {
            throw "Falha ao criar Release '$Tag' (exit code $LASTEXITCODE)."
        }
    }

    Write-Host "==> Release '$Tag' publicada com sucesso!" -ForegroundColor Green
    & gh release view $Tag --json tagName,name,url,assets
} finally {
    Remove-Item -LiteralPath $TempDir -Recurse -Force -ErrorAction SilentlyContinue
}
