<#
.SYNOPSIS
    Cria ou atualiza uma Release no GitHub (NuncaMorto/Vida-Academica) com APK, IPA, SHA256SUMS.txt e patch notes do CHANGELOG.md.
.PARAMETER Tag
    Tag da release (ex.: v1.9.6). Quando omitida, usa v<versionName> de android-app/app/build.gradle.kts.
.PARAMETER NotesFile
    Arquivo opcional com as notas da release. Quando omitido, extrai a seção correspondente de CHANGELOG.md.
.PARAMETER Draft
    Publica a release como rascunho.
.PARAMETER Prerelease
    Marca a release como pre-release.
.PARAMETER AndroidOnly
    Ignora pacotes .ipa locais e preserva checksums de outros assets ao atualizar SHA256SUMS.txt.
.PARAMETER SkipChecks
    Pula validação estrita de versão e guarda de branch antes da publicação.
.PARAMETER EnsureTag
    Cria a tag anotada local (se ausente) e faz git push origin <Tag> antes de publicar.
.PARAMETER Force
    Permite ignorar a guarda de branch (stable em develop / prerelease em main).
#>
param(
    [string]$Tag,
    [string]$NotesFile,
    [switch]$Draft,
    [switch]$Prerelease,
    [switch]$AndroidOnly,
    [switch]$SkipChecks,
    [switch]$EnsureTag,
    [switch]$Force
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command "gh" -ErrorAction SilentlyContinue)) {
    throw "GitHub CLI ('gh') não encontrada no PATH."
}

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$BuildGradleKts = Join-Path $RepoRoot "android-app\app\build.gradle.kts"
$ChangelogPath = Join-Path $RepoRoot "CHANGELOG.md"
$DistDir = Join-Path $RepoRoot "dist"
$BumpScript = Join-Path $PSScriptRoot "bump-version.ps1"
$ExtractScript = Join-Path $PSScriptRoot "extract-release-notes.py"

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

$ReleaseApkPath = Join-Path $DistDir "Vida-Academica-$Tag.apk"
$DebugApkPath = Join-Path $DistDir "Vida-Academica-$Tag-debug.apk"
$ApkPath = if (Test-Path $ReleaseApkPath) { $ReleaseApkPath } else { $DebugApkPath }
$IpaPath = Join-Path $DistDir "Vida-Academica-$Tag-unsigned.ipa"

$Assets = New-Object System.Collections.Generic.List[string]
if (Test-Path $ApkPath) { $Assets.Add($ApkPath) }
if (-not $AndroidOnly -and (Test-Path $IpaPath)) { $Assets.Add($IpaPath) }

if ($Assets.Count -eq 0) {
    throw "Nenhum pacote encontrado em '$DistDir' para a versão '$Tag' (esperado '$ApkPath' ou '$IpaPath')."
}

if (-not $SkipChecks) {
    if (-not $VersionCode) {
        throw "Não foi possível ler versionCode em '$BuildGradleKts'."
    }
    Write-Host "==> Validando sincronismo estrito da versão $VersionBare (versionCode=$VersionCode)..." -ForegroundColor Cyan
    & $BumpScript -Version $VersionBare -Code ([int]$VersionCode) -CheckOnly -Strict
    if ($LASTEXITCODE -ne 0) {
        throw "Falha na validação estrita de versão (use -SkipChecks para ignorar)."
    }

    if (Get-Command "git" -ErrorAction SilentlyContinue) {
        $PrevPrefGit = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        $CurrentBranch = (& git -C $RepoRoot rev-parse --abbrev-ref HEAD 2>$null | Out-String).Trim()
        $ErrorActionPreference = $PrevPrefGit
        $global:LASTEXITCODE = 0
        if ($CurrentBranch -and -not $Force) {
            if ((-not $Prerelease) -and ($CurrentBranch -ne "main")) {
                throw "Release estável sem -Prerelease deve ser publicada a partir de 'main' (branch atual: '$CurrentBranch'). Use -Prerelease para beta ou -Force para ignorar."
            }
            if ($Prerelease -and ($CurrentBranch -eq "main")) {
                throw "Release na branch 'main' não deve ser marcada como -Prerelease (use -Force para ignorar)."
            }
        }
    }
}

if ($EnsureTag) {
    if (-not (Get-Command "git" -ErrorAction SilentlyContinue)) {
        throw "Git não encontrado no PATH para criar/enviar a tag '$Tag'."
    }
    $ExistingTag = (& git -C $RepoRoot tag -l $Tag | Out-String).Trim()
    if (-not $ExistingTag) {
        Write-Host "==> Criando tag anotada $Tag..." -ForegroundColor Cyan
        & git -C $RepoRoot tag -a $Tag -m "Release $Tag"
        if ($LASTEXITCODE -ne 0) {
            throw "Falha ao criar tag '$Tag'."
        }
    }
    Write-Host "==> Enviando tag $Tag para origin..." -ForegroundColor Cyan
    & git -C $RepoRoot push origin $Tag
    if ($LASTEXITCODE -ne 0) {
        throw "Falha ao enviar tag '$Tag' para origin."
    }
}

$TempDir = Join-Path ([IO.Path]::GetTempPath()) ("ufop_release_" + [Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Force -Path $TempDir | Out-Null

try {
    $PreviousPref = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    & gh release view $Tag 2>$null | Out-Null
    $ReleaseExists = ($LASTEXITCODE -eq 0)
    $ErrorActionPreference = $PreviousPref

    # Gera SHA256SUMS.txt mesclando hashes preservados de outros assets quando a release já existe
    $SumByFile = @{}
    $UploadedLeafNames = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($Asset in $Assets) {
        $Leaf = Split-Path -Leaf $Asset
        $Hash = (Get-FileHash -LiteralPath $Asset -Algorithm SHA256).Hash.ToLowerInvariant()
        $SumByFile[$Leaf] = $Hash
        [void]$UploadedLeafNames.Add($Leaf)
    }

    if ($ReleaseExists) {
        $PrevSumPath = Join-Path $TempDir "prev-SHA256SUMS.txt"
        $PrevPref = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        & gh release download $Tag -p "SHA256SUMS.txt" -O $PrevSumPath 2>$null | Out-Null
        $PrevOk = ($LASTEXITCODE -eq 0)
        $ErrorActionPreference = $PrevPref

        if ($PrevOk -and (Test-Path -LiteralPath $PrevSumPath -PathType Leaf)) {
            foreach ($line in ([System.IO.File]::ReadAllLines($PrevSumPath, [System.Text.Encoding]::UTF8))) {
                if ($line -match '^([0-9a-fA-F]{64})\s+\*?(.+?)\s*$') {
                    $prevHash = $Matches[1].ToLowerInvariant()
                    $prevName = $Matches[2].Trim()
                    if (-not $UploadedLeafNames.Contains($prevName)) {
                        $SumByFile[$prevName] = $prevHash
                    }
                }
            }
        }
    }

    $SumLines = foreach ($name in ($SumByFile.Keys | Sort-Object)) {
        "$($SumByFile[$name])  $name"
    }
    $ShaFile = Join-Path $TempDir "SHA256SUMS.txt"
    [IO.File]::WriteAllText($ShaFile, (($SumLines -join "`n") + "`n"), [Text.UTF8Encoding]::new($false))
    $Assets.Add($ShaFile)

    # Resolve notas da release a partir de NotesFile, extract-release-notes.py ou CHANGELOG.md
    $ResolvedNotesPath = $NotesFile
    if (-not $ResolvedNotesPath) {
        $ResolvedNotesPath = Join-Path $TempDir "release-notes.md"
        $ExtractedViaPython = $false
        if (Test-Path -LiteralPath $ExtractScript -PathType Leaf) {
            $PyCmd = Get-Command "python" -ErrorAction SilentlyContinue
            if (-not $PyCmd) { $PyCmd = Get-Command "python3" -ErrorAction SilentlyContinue }
            if ($null -ne $PyCmd) {
                $PyArgs = @($ExtractScript, "--version", $VersionBare, "--out", $ResolvedNotesPath)
                if ($VersionCode) { $PyArgs += @("--version-code", $VersionCode) }
                & $PyCmd.Source @PyArgs
                if ($LASTEXITCODE -eq 0 -and (Test-Path -LiteralPath $ResolvedNotesPath -PathType Leaf)) {
                    $ExtractedViaPython = $true
                }
            }
        }

        if (-not $ExtractedViaPython) {
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
                $ExtractedNotes = "## Vida Acadêmica $Tag`n`nRelease publicada a partir de \`dist/\`."
            }
            $ExtractedNotes = [regex]::Replace($ExtractedNotes, '(?im)^\s*<!--\s*versionCode\s*:\s*\d+\s*-->\s*\r?\n?', '').TrimEnd()
            if ($VersionCode) {
                $ExtractedNotes = "$ExtractedNotes`n`n<!-- versionCode: $VersionCode -->`n"
            } else {
                $ExtractedNotes = "$ExtractedNotes`n"
            }
            [IO.File]::WriteAllText($ResolvedNotesPath, $ExtractedNotes, [Text.UTF8Encoding]::new($false))
        }
    }

    $Title = if ($Prerelease) { "Vida Acadêmica $Tag (Beta / Testes)" } else { "Vida Acadêmica $Tag" }

    if ($ReleaseExists) {
        Write-Host "==> Atualizando Release existente $Tag..." -ForegroundColor Cyan
        & gh release upload $Tag @Assets --clobber
        if ($LASTEXITCODE -ne 0) {
            throw "Falha ao enviar assets para a Release '$Tag' (exit code $LASTEXITCODE)."
        }
        $EditArgs = @("release", "edit", $Tag, "--title", $Title, "--notes-file", $ResolvedNotesPath)
        if ($Prerelease) { $EditArgs += "--prerelease" } else { $EditArgs += @("--prerelease=false", "--latest") }
        & gh @EditArgs
        if ($LASTEXITCODE -ne 0) {
            throw "Falha ao atualizar título/notas da Release '$Tag' (exit code $LASTEXITCODE)."
        }
    } else {
        Write-Host "==> Criando nova Release $Tag..." -ForegroundColor Cyan
        $CreateArgs = @("release", "create", $Tag) + $Assets + @("--title", $Title, "--notes-file", $ResolvedNotesPath)
        if ($Draft) { $CreateArgs += "--draft" }
        if ($Prerelease) { $CreateArgs += "--prerelease" } else { $CreateArgs += "--latest" }
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
