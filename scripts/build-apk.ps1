<#
.SYNOPSIS
    Compila o APK de debug do Vida Acadêmica (`android-app`) e exporta com o número de versão em `dist\`.
#>
$ErrorActionPreference = "Stop"

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$AndroidDir = Join-Path $RepoRoot "android-app"
$GradlewBat = Join-Path $AndroidDir "gradlew.bat"
$BuildGradleKts = Join-Path $AndroidDir "app\build.gradle.kts"
$DistDir = Join-Path $RepoRoot "dist"
$VersionName = "1.5"
if (Test-Path $BuildGradleKts) {
    $GradleText = Get-Content -Path $BuildGradleKts -Raw
    if ($GradleText -match 'versionName\s*=\s*"([^"]+)"') {
        $VersionName = $Matches[1]
    }
}
$OutputApk = Join-Path $DistDir "Vida-Academica-v$VersionName-debug.apk"
$BuiltApk = Join-Path $AndroidDir "app\build\outputs\apk\debug\app-debug.apk"

$DefaultJavaHome = "C:\Program Files\Android\Android Studio\jbr"
if (Test-Path $DefaultJavaHome) {
    $env:JAVA_HOME = $DefaultJavaHome
} elseif (-not $env:JAVA_HOME -or -not (Test-Path $env:JAVA_HOME)) {
    throw "JAVA_HOME não encontrado em '$DefaultJavaHome' e nenhum JAVA_HOME válido está definido."
}
$env:PATH = "$(Join-Path $env:JAVA_HOME 'bin');$env:PATH"

$DefaultAndroidHome = Join-Path $env:LOCALAPPDATA "Android\Sdk"
if (Test-Path $DefaultAndroidHome) {
    $env:ANDROID_HOME = $DefaultAndroidHome
    $env:ANDROID_SDK_ROOT = $DefaultAndroidHome
} elseif (-not $env:ANDROID_HOME -or -not (Test-Path $env:ANDROID_HOME)) {
    throw "ANDROID_HOME não encontrado em '$DefaultAndroidHome' e nenhum ANDROID_HOME válido está definido."
}

if (-not (Test-Path $GradlewBat)) {
    throw "Gradle Wrapper não encontrado em '$GradlewBat'."
}

Write-Host "==> JAVA_HOME:    $env:JAVA_HOME" -ForegroundColor Cyan
Write-Host "==> ANDROID_HOME: $env:ANDROID_HOME" -ForegroundColor Cyan

Push-Location $AndroidDir
try {
    Write-Host "==> Executando gradlew.bat :app:assembleDebug --no-daemon --offline..." -ForegroundColor Cyan
    & $GradlewBat :app:assembleDebug --no-daemon --offline
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "Build offline falhou (código $LASTEXITCODE); tentando novamente sem --offline..."
        & $GradlewBat :app:assembleDebug --no-daemon
        if ($LASTEXITCODE -ne 0) {
            throw "Falha ao compilar :app:assembleDebug (código de saída $LASTEXITCODE)."
        }
    }
} finally {
    Pop-Location
}

if (-not (Test-Path $BuiltApk)) {
    throw "APK esperado não foi encontrado em '$BuiltApk'."
}

New-Item -ItemType Directory -Force -Path $DistDir | Out-Null
Copy-Item -Path $BuiltApk -Destination $OutputApk -Force

$FileInfo = Get-Item $OutputApk
$SizeBytes = $FileInfo.Length
if ($SizeBytes -le 0) {
    throw "APK gerado está vazio: '$OutputApk'."
}
$SizeMb = [Math]::Round($SizeBytes / 1MB, 2)
$Sha256 = (Get-FileHash -Path $OutputApk -Algorithm SHA256).Hash

Write-Host "==> APK gerado com sucesso (v$VersionName)!" -ForegroundColor Green
Write-Host "    Caminho principal: $OutputApk"
Write-Host "    Tamanho:           $SizeBytes bytes ($SizeMb MB)"
Write-Host "    SHA256:            $Sha256"
