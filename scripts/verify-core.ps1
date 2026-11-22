<#
.SYNOPSIS
    Verifica formatação, lint (clippy) e suíte de testes do núcleo Rust (`campus_core`).
    Configura automaticamente o ambiente MSVC (vcvars64.bat) no Windows se link.exe não estiver no PATH.
#>
$ErrorActionPreference = "Stop"

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

# 1. Configurar ambiente MSVC se link.exe não estiver disponível no PATH (Windows)
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
            if (Test-Path $Candidate) {
                $VcvarsBat = $Candidate
                break
            }
        }

        if ($VcvarsBat) {
            Write-Host "==> link.exe não encontrado no PATH. Configurando ambiente MSVC via: $VcvarsBat" -ForegroundColor Yellow
            $EnvOutput = & cmd.exe /c "call `"$VcvarsBat`" >nul 2>&1 && set"
            foreach ($Line in $EnvOutput) {
                if ($Line -match '^(.*?)=(.*)$') {
                    Set-Item -Path "env:\$($matches[1])" -Value $matches[2]
                }
            }
        } else {
            Write-Warning "link.exe não está no PATH e vcvars64.bat não foi localizado em caminhos conhecidos."
        }
    }
}

if (-not (Get-Command "cargo" -ErrorAction SilentlyContinue)) {
    Write-Host "ERRO: Ferramenta 'cargo' não encontrada no PATH." -ForegroundColor Red
    exit 1
}

Push-Location $RepoRoot
try {
    Write-Host "==> 1/3 cargo fmt --all -- --check" -ForegroundColor Cyan
    & cargo fmt --all -- --check
    if ($LASTEXITCODE -ne 0) {
        $Code = if ($LASTEXITCODE -ne 0) { $LASTEXITCODE } else { 1 }
        Write-Host "ERRO: Falha na verificação de formatação (cargo fmt). Código de saída: $Code" -ForegroundColor Red
        exit $Code
    }

    Write-Host "==> 2/3 cargo clippy --workspace --all-targets -- -D warnings" -ForegroundColor Cyan
    & cargo clippy --workspace --all-targets -- -D warnings
    if ($LASTEXITCODE -ne 0) {
        $Code = if ($LASTEXITCODE -ne 0) { $LASTEXITCODE } else { 1 }
        Write-Host "ERRO: Falha no cargo clippy. Código de saída: $Code" -ForegroundColor Red
        exit $Code
    }

    Write-Host "==> 3/3 cargo test --workspace" -ForegroundColor Cyan
    & cargo test --workspace
    if ($LASTEXITCODE -ne 0) {
        $Code = if ($LASTEXITCODE -ne 0) { $LASTEXITCODE } else { 1 }
        Write-Host "ERRO: Falha nos testes do workspace Rust. Código de saída: $Code" -ForegroundColor Red
        exit $Code
    }
} finally {
    Pop-Location
}

Write-Host "==> Tudo verificado com sucesso!" -ForegroundColor Green
exit 0
