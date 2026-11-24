<#
.SYNOPSIS
    Compila a biblioteca Rust `campus_core` para Android (via cargo-ndk) e gera os bindings UniFFI.
.PARAMETER Abis
    Lista de ABIs Android a compilar. Padrão: arm64-v8a, armeabi-v7a, x86_64.
.PARAMETER Release
    Compila em modo release (padrão: $true).
.PARAMETER SkipBindgen
    Se definido, pula a geração dos bindings UniFFI.
.PARAMETER KotlinOnly
    Se definido, regenera apenas o binding Kotlin (sem atualizar Swift).
#>
param(
    [string[]]$Abis = @("arm64-v8a", "armeabi-v7a", "x86_64"),
    [bool]$Release = $true,
    [switch]$SkipBindgen,
    [switch]$KotlinOnly
)

$ErrorActionPreference = "Stop"

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$JniLibsDir = Join-Path $RepoRoot "android-app\app\src\main\jniLibs"
$SyncScript = Join-Path $PSScriptRoot "sync-uniffi-bindings.ps1"

if (-not (Get-Command "cargo" -ErrorAction SilentlyContinue)) {
    throw "Ferramenta 'cargo' não encontrada no PATH."
}

if (-not (Get-Command "cargo-ndk" -ErrorAction SilentlyContinue)) {
    throw "Ferramenta 'cargo-ndk' não encontrada. Instale com: cargo install cargo-ndk --locked"
}

# Auto-detectar ANDROID_NDK_HOME se ainda não estiver definido
if (-not $env:ANDROID_NDK_HOME -or -not (Test-Path -LiteralPath $env:ANDROID_NDK_HOME)) {
    $SdkCandidate = $env:ANDROID_HOME
    if (-not $SdkCandidate -or -not (Test-Path -LiteralPath $SdkCandidate)) {
        if ($env:LOCALAPPDATA) {
            $SdkCandidate = Join-Path $env:LOCALAPPDATA "Android\Sdk"
        }
    }
    if ($SdkCandidate -and (Test-Path -LiteralPath (Join-Path $SdkCandidate "ndk"))) {
        $NdkVersions = Get-ChildItem -LiteralPath (Join-Path $SdkCandidate "ndk") -Directory | Sort-Object -Property {
            $ver = $null
            if ([version]::TryParse($_.Name, [ref]$ver)) { $ver } else { [version]"0.0" }
        } -Descending
        if ($NdkVersions -and $NdkVersions.Count -gt 0) {
            $env:ANDROID_NDK_HOME = $NdkVersions[0].FullName
            Write-Host "==> ANDROID_NDK_HOME detectado: $env:ANDROID_NDK_HOME" -ForegroundColor Cyan
        }
    }
}

Write-Host "==> Compilando campus_core para Android ABIs: $($Abis -join ', ')" -ForegroundColor Cyan
New-Item -ItemType Directory -Force -Path $JniLibsDir | Out-Null

$NdkArgs = @("ndk")
foreach ($Abi in $Abis) {
    $NdkArgs += @("-t", $Abi)
}
$NdkArgs += @("-o", $JniLibsDir, "build", "-p", "campus_core")
if ($Release) {
    $NdkArgs += "--release"
}

Push-Location $RepoRoot
try {
    & cargo @NdkArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Falha ao compilar campus_core via cargo ndk (exit code $LASTEXITCODE)."
    }

    foreach ($Abi in $Abis) {
        $SoPath = Join-Path (Join-Path $JniLibsDir $Abi) "libcampus_core.so"
        if (-not (Test-Path -LiteralPath $SoPath -PathType Leaf)) {
            throw "Biblioteca nativa esperada não foi gerada para ABI '$Abi': $SoPath"
        }
        $SizeMb = [Math]::Round((Get-Item -LiteralPath $SoPath).Length / 1MB, 2)
        Write-Host "    [$Abi] libcampus_core.so ($SizeMb MB)" -ForegroundColor DarkCyan
    }

    if (-not $SkipBindgen) {
        if (-not (Test-Path -LiteralPath $SyncScript -PathType Leaf)) {
            throw "Script de sincronização UniFFI não encontrado: $SyncScript"
        }
        $Langs = if ($KotlinOnly) { @("kotlin") } else { @("kotlin", "swift") }
        Write-Host "==> Sincronizando bindings UniFFI ($($Langs -join ', '))..." -ForegroundColor Cyan
        & $SyncScript -Release:$Release -Languages $Langs
        if ($LASTEXITCODE -ne 0) {
            throw "Falha ao sincronizar bindings UniFFI (exit code $LASTEXITCODE)."
        }
    }
} finally {
    Pop-Location
}

Write-Host "==> Concluído! Bibliotecas nativas em: $JniLibsDir" -ForegroundColor Green
