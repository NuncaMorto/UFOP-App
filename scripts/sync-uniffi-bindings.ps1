<#
.SYNOPSIS
    Sincroniza ou valida (com -CheckOnly) os bindings UniFFI gerados para Kotlin e Swift
    a partir do código Rust em `rust-core`, evitando drift entre Android, iOS e Rust.
    Compatível com Windows PowerShell 5.1 e PowerShell 7+ (pwsh).
#>
param(
    [switch]$CheckOnly,
    [switch]$Release,
    [switch]$SkipHostBuild,
    [string[]]$Languages = @("kotlin", "swift")
)

$ErrorActionPreference = "Stop"

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$RustCoreDir = Join-Path $RepoRoot "rust-core"
$UniffiConfig = Join-Path $RustCoreDir "uniffi.toml"

if (-not (Test-Path -LiteralPath $UniffiConfig -PathType Leaf)) {
    throw "Arquivo de configuração UniFFI não encontrado: $UniffiConfig"
}

# Configurar ambiente MSVC no Windows se link.exe não estiver disponível no PATH
if ($IsWindows -or ($env:OS -like "*Windows*")) {
    if (-not (Get-Command "link.exe" -ErrorAction SilentlyContinue)) {
        $KnownVcvars = @(
            "C:\Program Files (x86)\Microsoft Visual Studio\2019\BuildTools\VC\Auxiliary\Build\vcvars64.bat",
            "C:\Program Files\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat",
            "C:\Program Files (x86)\Microsoft Visual Studio\2019\Community\VC\Auxiliary\Build\vcvars64.bat",
            "C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\vcvars64.bat"
        )
        $VcvarsBat = $null
        foreach ($Candidate in $KnownVcvars) {
            if (Test-Path -LiteralPath $Candidate) {
                $VcvarsBat = $Candidate
                break
            }
        }
        if ($VcvarsBat) {
            Write-Host "==> Configurando ambiente MSVC via: $VcvarsBat" -ForegroundColor Yellow
            $EnvOutput = & cmd.exe /c "call `"$VcvarsBat`" >nul 2>&1 && set"
            foreach ($Line in $EnvOutput) {
                if ($Line -match '^(.*?)=(.*)$') {
                    Set-Item -Path "env:\$($matches[1])" -Value $matches[2]
                }
            }
        }
    }
}

if (-not (Get-Command "cargo" -ErrorAction SilentlyContinue)) {
    throw "Ferramenta 'cargo' não encontrada no PATH."
}

# Ler package_name, cdylib_name e module_name de rust-core/uniffi.toml
$UniffiText = [System.IO.File]::ReadAllText($UniffiConfig, [System.Text.Encoding]::UTF8)
$Section = ""
$KotlinPkg = ""
$KotlinCdylib = ""
$SwiftModule = ""

foreach ($rawLine in ($UniffiText -split "`r?`n")) {
    $line = $rawLine.Trim()
    if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) { continue }
    if ($line -match '^\[([^\]]+)\]\s*$') {
        $Section = $Matches[1].Trim()
        continue
    }
    if ($line -match '^([A-Za-z0-9_]+)\s*=\s*"([^"]*)"\s*(#.*)?$') {
        $k = $Matches[1].Trim()
        $v = $Matches[2].Trim()
        if ($Section -eq "bindings.kotlin") {
            if ($k -eq "package_name") { $KotlinPkg = $v }
            elseif ($k -eq "cdylib_name") { $KotlinCdylib = $v }
        } elseif ($Section -eq "bindings.swift") {
            if ($k -eq "module_name") { $SwiftModule = $v }
        }
    }
}

if ([string]::IsNullOrWhiteSpace($KotlinPkg) -or [string]::IsNullOrWhiteSpace($KotlinCdylib) -or [string]::IsNullOrWhiteSpace($SwiftModule)) {
    throw "Falha ao extrair bindings.kotlin / bindings.swift de $UniffiConfig"
}

$NormalizedLangs = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
foreach ($lang in $Languages) {
    foreach ($part in ($lang -split ',')) {
        $trimmed = $part.Trim().ToLowerInvariant()
        if ($trimmed -eq "kotlin" -or $trimmed -eq "swift") {
            [void]$NormalizedLangs.Add($trimmed)
        } elseif (-not [string]::IsNullOrWhiteSpace($trimmed)) {
            throw "Linguagem não suportada '$trimmed'. Use 'kotlin' e/ou 'swift'."
        }
    }
}
if ($NormalizedLangs.Count -eq 0) {
    throw "Nenhuma linguagem válida informada em -Languages."
}

$ProfileDir = if ($Release) { "release" } else { "debug" }
$TargetRoot = if ($env:CARGO_TARGET_DIR) { $env:CARGO_TARGET_DIR } else { Join-Path $RepoRoot "target" }
$ProfilePath = Join-Path $TargetRoot $ProfileDir

function Find-HostLibrary([string]$Dir) {
    foreach ($name in @("campus_core.dll", "libcampus_core.so", "libcampus_core.dylib")) {
        $cand = Join-Path $Dir $name
        if (Test-Path -LiteralPath $cand -PathType Leaf) {
            return $cand
        }
    }
    return $null
}

function Normalize-Lf([string]$Text) {
    return ($Text -replace "`r`n", "`n" -replace "`r", "`n")
}

function Find-FirstDiffLine([string]$Expected, [string]$Actual) {
    $expLines = $Expected -split "`n"
    $actLines = $Actual -split "`n"
    $max = [Math]::Max($expLines.Length, $actLines.Length)
    for ($i = 0; $i -lt $max; $i++) {
        $e = if ($i -lt $expLines.Length) { $expLines[$i] } else { "<EOF>" }
        $a = if ($i -lt $actLines.Length) { $actLines[$i] } else { "<EOF>" }
        if ($e -ne $a) {
            return ($i + 1)
        }
    }
    return -1
}

function Invoke-UniffiGenerate {
    param(
        [string]$LibraryPath,
        [string]$Language,
        [string]$OutDir
    )
    New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
    $uniffiCmd = Get-Command "uniffi-bindgen" -ErrorAction SilentlyContinue
    if ($null -ne $uniffiCmd) {
        & $uniffiCmd.Source generate `
            --library $LibraryPath `
            --language $Language `
            --config $UniffiConfig `
            --out-dir $OutDir
    } else {
        & cargo run -p campus_core --bin uniffi-bindgen -- generate `
            --library $LibraryPath `
            --language $Language `
            --config $UniffiConfig `
            --out-dir $OutDir
    }
    if ($LASTEXITCODE -ne 0) {
        throw "Falha ao executar uniffi-bindgen para '$Language' (exit code $LASTEXITCODE)."
    }
}

$TempDir = Join-Path ([System.IO.Path]::GetTempPath()) ("ufop_uniffi_" + [Guid]::NewGuid().ToString("N"))
$Utf8NoBom = [System.Text.UTF8Encoding]::new($false)

Push-Location $RepoRoot
try {
    $LibPath = Find-HostLibrary -Dir $ProfilePath
    if (-not ($SkipHostBuild -and $null -ne $LibPath)) {
        Write-Host "==> Compilando campus_core para o host ($ProfileDir) para extrair metadados UniFFI..." -ForegroundColor Cyan
        $BuildArgs = @("build", "-p", "campus_core")
        if ($Release) {
            $BuildArgs += "--release"
        }
        & cargo @BuildArgs
        if ($LASTEXITCODE -ne 0) {
            throw "Falha ao compilar campus_core para o host (exit code $LASTEXITCODE)."
        }
        $LibPath = Find-HostLibrary -Dir $ProfilePath
    } else {
        Write-Host "==> Usando biblioteca host existente: $LibPath" -ForegroundColor Cyan
    }

    if ($null -eq $LibPath) {
        throw "Biblioteca compilada do host não encontrada em '$ProfilePath'."
    }

    $Pairs = [System.Collections.Generic.List[hashtable]]::new()

    if ($NormalizedLangs.Contains("kotlin")) {
        $KtTempOut = Join-Path $TempDir "kotlin"
        Invoke-UniffiGenerate -LibraryPath $LibPath -Language "kotlin" -OutDir $KtTempOut
        $PkgSubParts = $KotlinPkg.Split('.')
        $GenKt = $KtTempOut
        foreach ($p in $PkgSubParts) { $GenKt = Join-Path $GenKt $p }
        $GenKt = Join-Path $GenKt "$KotlinCdylib.kt"
        if (-not (Test-Path -LiteralPath $GenKt -PathType Leaf)) {
            throw "Binding Kotlin esperado não gerado em: $GenKt"
        }
        $DestKt = $RepoRoot
        foreach ($p in (@("android-app", "app", "src", "main", "java") + $PkgSubParts)) {
            $DestKt = Join-Path $DestKt $p
        }
        $DestKt = Join-Path $DestKt "$KotlinCdylib.kt"
        $Pairs.Add(@{
            Label  = "Kotlin ($KotlinPkg/$KotlinCdylib.kt)"
            Source = $GenKt
            Target = $DestKt
        })
    }

    if ($NormalizedLangs.Contains("swift")) {
        $SwTempOut = Join-Path $TempDir "swift"
        Invoke-UniffiGenerate -LibraryPath $LibPath -Language "swift" -OutDir $SwTempOut
        $SwiftGenDir = $RepoRoot
        foreach ($p in @("ios-app", "CampusUFOP", "CoreFFI", "Generated")) {
            $SwiftGenDir = Join-Path $SwiftGenDir $p
        }
        $SwiftFileNames = @(
            "$SwiftModule.swift",
            "${SwiftModule}FFI.h",
            "${SwiftModule}FFI.modulemap"
        )
        foreach ($sfName in $SwiftFileNames) {
            $genSw = Join-Path $SwTempOut $sfName
            if (-not (Test-Path -LiteralPath $genSw -PathType Leaf)) {
                throw "Arquivo Swift UniFFI esperado não gerado em: $genSw"
            }
            $Pairs.Add(@{
                Label  = "Swift ($sfName)"
                Source = $genSw
                Target = (Join-Path $SwiftGenDir $sfName)
            })
        }
    }

    $Drifted = [System.Collections.Generic.List[string]]::new()
    foreach ($pair in $Pairs) {
        $genContent = Normalize-Lf ([System.IO.File]::ReadAllText($pair.Source, [System.Text.Encoding]::UTF8))
        $targetPath = $pair.Target

        if ($CheckOnly) {
            if (-not (Test-Path -LiteralPath $targetPath -PathType Leaf)) {
                $Drifted.Add("$($pair.Label): arquivo ausente em '$targetPath'")
                Write-Host "[FAIL] $($pair.Label) ausente: $targetPath" -ForegroundColor Red
                continue
            }
            $curContent = Normalize-Lf ([System.IO.File]::ReadAllText($targetPath, [System.Text.Encoding]::UTF8))
            if ($curContent -cne $genContent) {
                $diffLine = Find-FirstDiffLine -Expected $genContent -Actual $curContent
                $Drifted.Add("$($pair.Label): divergente na linha $diffLine")
                Write-Host "[FAIL] Drift detectado em $($pair.Label) (primeira divergência na linha $diffLine)." -ForegroundColor Red
            } else {
                Write-Host "[PASS] $($pair.Label) sincronizado byte a byte com rust-core." -ForegroundColor Green
            }
        } else {
            $targetDir = Split-Path $targetPath -Parent
            New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
            $needsWrite = $true
            if (Test-Path -LiteralPath $targetPath -PathType Leaf) {
                $curContent = Normalize-Lf ([System.IO.File]::ReadAllText($targetPath, [System.Text.Encoding]::UTF8))
                if ($curContent -ceq $genContent) {
                    $needsWrite = $false
                }
            }
            if ($needsWrite) {
                [System.IO.File]::WriteAllText($targetPath, $genContent, $Utf8NoBom)
                Write-Host "[UPDATED] $($pair.Label) -> $targetPath" -ForegroundColor Green
            } else {
                Write-Host "[OK]      $($pair.Label) já estava atualizado." -ForegroundColor Cyan
            }
        }
    }

    if ($CheckOnly -and $Drifted.Count -gt 0) {
        Write-Host ""
        Write-Host "ERRO: $($Drifted.Count) arquivo(s) de binding UniFFI estão desatualizados em relação ao Rust." -ForegroundColor Red
        Write-Host "Execute '.\scripts\sync-uniffi-bindings.ps1' para regenerar Kotlin e Swift sincronizados." -ForegroundColor Yellow
        exit 1
    }

    if ($CheckOnly) {
        Write-Host "[PASS] Todos os bindings UniFFI verificados estão 100% sincronizados." -ForegroundColor Green
    } else {
        Write-Host "==> Bindings UniFFI sincronizados com sucesso!" -ForegroundColor Green
    }
} finally {
    Pop-Location
    if (Test-Path -LiteralPath $TempDir) {
        Remove-Item -LiteralPath $TempDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}
exit 0
