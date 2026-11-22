<#
.SYNOPSIS
    Compila a biblioteca Rust `campus_core` para Android (via cargo-ndk) e gera os bindings Kotlin via UniFFI.
.PARAMETER Abis
    Lista de ABIs Android a compilar. Padrão: arm64-v8a, armeabi-v7a, x86_64.
.PARAMETER Release
    Compila em modo release (padrão: $true).
.PARAMETER SkipBindgen
    Se definido, pula a geração dos arquivos Kotlin via uniffi-bindgen.
#>
param(
    [string[]]$Abis = @("arm64-v8a", "armeabi-v7a", "x86_64"),
    [bool]$Release = $true,
    [switch]$SkipBindgen
)

$ErrorActionPreference = "Stop"

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$RustCoreDir = Join-Path $RepoRoot "rust-core"
$JniLibsDir = Join-Path $RepoRoot "android-app\app\src\main\jniLibs"
$KotlinOutDir = Join-Path $RepoRoot "android-app\app\src\main\java"
$UniffiConfig = Join-Path $RustCoreDir "uniffi.toml"

if (-not (Get-Command "cargo" -ErrorAction SilentlyContinue)) {
    throw "Ferramenta 'cargo' não encontrada no PATH."
}

if (-not (Get-Command "cargo-ndk" -ErrorAction SilentlyContinue)) {
    throw "Ferramenta 'cargo-ndk' não encontrada. Instale com: cargo install cargo-ndk"
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

    if (-not $SkipBindgen) {
        Write-Host "==> Gerando bindings Kotlin com uniffi-bindgen" -ForegroundColor Cyan
        New-Item -ItemType Directory -Force -Path $KotlinOutDir | Out-Null

        # Compila a biblioteca para o host para extrair os metadados UniFFI
        $HostBuildArgs = @("build", "-p", "campus_core")
        if ($Release) {
            $HostBuildArgs += "--release"
        }
        & cargo @HostBuildArgs
        if ($LASTEXITCODE -ne 0) {
            throw "Falha ao compilar campus_core para o host."
        }

        $ProfileDir = if ($Release) { "release" } else { "debug" }
        $LibPath = Join-Path $RepoRoot "target\$ProfileDir\campus_core.dll"
        if (-not (Test-Path $LibPath)) {
            $LibPath = Join-Path $RepoRoot "target\$ProfileDir\libcampus_core.so"
        }
        if (-not (Test-Path $LibPath)) {
            $LibPath = Join-Path $RepoRoot "target\$ProfileDir\libcampus_core.dylib"
        }
        if (-not (Test-Path $LibPath)) {
            throw "Biblioteca compilada do host não encontrada em target\$ProfileDir."
        }

        if (Get-Command "uniffi-bindgen" -ErrorAction SilentlyContinue) {
            & uniffi-bindgen generate `
                --library $LibPath `
                --language kotlin `
                --config $UniffiConfig `
                --out-dir $KotlinOutDir
        } else {
            & cargo run -p uniffi --features cli --bin uniffi-bindgen -- generate `
                --library $LibPath `
                --language kotlin `
                --config $UniffiConfig `
                --out-dir $KotlinOutDir
        }

        if ($LASTEXITCODE -ne 0) {
            throw "Falha ao gerar bindings Kotlin com uniffi-bindgen."
        }
    }
} finally {
    Pop-Location
}

Write-Host "==> Concluído! Bibliotecas nativas em: $JniLibsDir" -ForegroundColor Green
