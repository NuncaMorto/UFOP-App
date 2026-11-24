<#
.SYNOPSIS
    Compila o APK (debug ou release) do Vida Acadêmica (`android-app`), garante frescor do núcleo nativo Rust (`jniLibs`),
    exporta em `dist\` e valida o APK gerado com Zip/aapt2/apksigner.
.PARAMETER Release
    Compila :app:assembleRelease em vez de :app:assembleDebug.
.PARAMETER RebuildFfi
    Força recompilação de libcampus_core.so via build-android-ffi.ps1 antes do Gradle.
.PARAMETER SkipFfiCheck
    Pula a verificação/recompilação automática de jniLibs antes do Gradle.
.PARAMETER SkipVerify
    Pula a verificação pós-build (conteúdo .so, aapt2 e apksigner).
#>
param(
    [switch]$Release,
    [switch]$RebuildFfi,
    [switch]$SkipFfiCheck,
    [switch]$SkipVerify
)

$ErrorActionPreference = "Stop"

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$AndroidDir = Join-Path $RepoRoot "android-app"
$GradlewBat = Join-Path $AndroidDir "gradlew.bat"
$BuildGradleKts = Join-Path $AndroidDir "app\build.gradle.kts"
$DistDir = Join-Path $RepoRoot "dist"
$JniLibsDir = Join-Path $AndroidDir "app\src\main\jniLibs"
$FfiScript = Join-Path $PSScriptRoot "build-android-ffi.ps1"

if (-not (Test-Path -LiteralPath $BuildGradleKts -PathType Leaf)) {
    throw "Arquivo build.gradle.kts não encontrado em '$BuildGradleKts'."
}

$GradleText = [System.IO.File]::ReadAllText($BuildGradleKts, [System.Text.Encoding]::UTF8)
if ($GradleText -notmatch '\bversionName\s*=\s*"([^"]+)"') {
    throw "Não foi possível ler versionName em '$BuildGradleKts'."
}
$VersionName = $Matches[1]

if ($Release) {
    $GradleTask = ":app:assembleRelease"
    $OutputApk = Join-Path $DistDir "Vida-Academica-v$VersionName.apk"
    $BuiltApk = Join-Path $AndroidDir "app\build\outputs\apk\release\app-release.apk"
    $BuiltUnsignedApk = Join-Path $AndroidDir "app\build\outputs\apk\release\app-release-unsigned.apk"
} else {
    $GradleTask = ":app:assembleDebug"
    $OutputApk = Join-Path $DistDir "Vida-Academica-v$VersionName-debug.apk"
    $BuiltApk = Join-Path $AndroidDir "app\build\outputs\apk\debug\app-debug.apk"
    $BuiltUnsignedApk = $null
}

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

# 1. Guarda de frescor dos binários nativos Rust (jniLibs/*/libcampus_core.so)
if (-not $SkipFfiCheck) {
    $ExpectedAbis = @("arm64-v8a", "armeabi-v7a", "x86_64")
    $NeedsFfiReason = $null

    if ($RebuildFfi) {
        $NeedsFfiReason = "Solicitado -RebuildFfi."
    } elseif (-not (Test-Path -LiteralPath $JniLibsDir -PathType Container)) {
        $NeedsFfiReason = "Diretório jniLibs ausente."
    } else {
        $MissingAbis = [System.Collections.Generic.List[string]]::new()
        $OldestSoUtc = [datetime]::MaxValue
        foreach ($abi in $ExpectedAbis) {
            $soPath = Join-Path (Join-Path $JniLibsDir $abi) "libcampus_core.so"
            if (-not (Test-Path -LiteralPath $soPath -PathType Leaf) -or (Get-Item -LiteralPath $soPath).Length -le 0) {
                $MissingAbis.Add($abi)
            } else {
                $t = (Get-Item -LiteralPath $soPath).LastWriteTimeUtc
                if ($t -lt $OldestSoUtc) { $OldestSoUtc = $t }
            }
        }
        if ($MissingAbis.Count -gt 0) {
            $NeedsFfiReason = "ABI(s) ausentes em jniLibs: $($MissingAbis -join ', ')."
        } else {
            $NewestRustUtc = [datetime]::MinValue
            foreach ($dirRel in @("rust-core\src", "rust-core\assets\map_data")) {
                $fullDir = Join-Path $RepoRoot $dirRel
                if (Test-Path -LiteralPath $fullDir) {
                    foreach ($f in (Get-ChildItem -LiteralPath $fullDir -Recurse -File)) {
                        if ($f.LastWriteTimeUtc -gt $NewestRustUtc) { $NewestRustUtc = $f.LastWriteTimeUtc }
                    }
                }
            }
            foreach ($fileRel in @("rust-core\Cargo.toml", "rust-core\uniffi.toml", "Cargo.lock")) {
                $fullFile = Join-Path $RepoRoot $fileRel
                if (Test-Path -LiteralPath $fullFile -PathType Leaf) {
                    $fi = Get-Item -LiteralPath $fullFile
                    if ($fi.LastWriteTimeUtc -gt $NewestRustUtc) { $NewestRustUtc = $fi.LastWriteTimeUtc }
                }
            }
            if ($NewestRustUtc -gt $OldestSoUtc) {
                $NeedsFfiReason = "Alterações em rust-core/src ou map_data são mais recentes que jniLibs/libcampus_core.so."
            }
        }
    }

    if ($NeedsFfiReason) {
        Write-Host "==> FFI nativo precisa ser compilado: $NeedsFfiReason" -ForegroundColor Yellow
        if (-not (Get-Command "cargo-ndk" -ErrorAction SilentlyContinue)) {
            throw "Núcleo nativo Rust desatualizado/ausente e 'cargo-ndk' não está no PATH. Instale com 'cargo install cargo-ndk --locked' ou use -SkipFfiCheck em emergências apenas de UI."
        }
        & $FfiScript -SkipBindgen
        if ($LASTEXITCODE -ne 0) {
            throw "Falha ao compilar jniLibs nativos via build-android-ffi.ps1 (código $LASTEXITCODE)."
        }
    } else {
        Write-Host "==> jniLibs/libcampus_core.so atualizado para todas as ABIs." -ForegroundColor DarkGreen
    }
}

Write-Host "==> JAVA_HOME:    $env:JAVA_HOME" -ForegroundColor Cyan
Write-Host "==> ANDROID_HOME: $env:ANDROID_HOME" -ForegroundColor Cyan

Push-Location $AndroidDir
try {
    Write-Host "==> Executando gradlew.bat $GradleTask --no-daemon --offline..." -ForegroundColor Cyan
    & $GradlewBat $GradleTask --no-daemon --offline
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "Build offline falhou (código $LASTEXITCODE); tentando novamente sem --offline..."
        & $GradlewBat $GradleTask --no-daemon
        if ($LASTEXITCODE -ne 0) {
            throw "Falha ao compilar $GradleTask (código de saída $LASTEXITCODE)."
        }
    }
} finally {
    Pop-Location
}

$IsUnsignedRelease = $false
if (-not (Test-Path -LiteralPath $BuiltApk)) {
    if ($Release -and $BuiltUnsignedApk -and (Test-Path -LiteralPath $BuiltUnsignedApk)) {
        $BuiltApk = $BuiltUnsignedApk
        $IsUnsignedRelease = $true
        Write-Warning "APK release gerado sem assinatura (release.storeFile / ANDROID_KEYSTORE_PATH não configurado)."
    } else {
        throw "APK esperado não foi encontrado em '$BuiltApk'."
    }
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

# 2. Validação pós-build (presença de libcampus_core.so + aapt2 + apksigner)
if (-not $SkipVerify) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($OutputApk)
    try {
        $entryNames = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
        foreach ($entry in $zip.Entries) {
            [void]$entryNames.Add($entry.FullName)
        }
    } finally {
        $zip.Dispose()
    }

    $MissingApkLibs = @()
    foreach ($abi in @("arm64-v8a", "armeabi-v7a", "x86_64")) {
        $soEntry = "lib/$abi/libcampus_core.so"
        if (-not $entryNames.Contains($soEntry)) {
            $MissingApkLibs += $soEntry
        }
    }
    if ($MissingApkLibs.Count -gt 0) {
        $msg = "APK não contém biblioteca(s) nativa(s) do Mapa 2D: $($MissingApkLibs -join ', ')"
        if ($SkipFfiCheck) {
            Write-Warning $msg
        } else {
            throw $msg
        }
    } else {
        Write-Host "==> Verificado no APK: libcampus_core.so presente para arm64-v8a, armeabi-v7a e x86_64." -ForegroundColor Green
    }

    $BuildToolsRoot = Join-Path $env:ANDROID_HOME "build-tools"
    if (Test-Path -LiteralPath $BuildToolsRoot -PathType Container) {
        $LatestTools = Get-ChildItem -LiteralPath $BuildToolsRoot -Directory | Sort-Object -Property {
            $v = $null
            if ([version]::TryParse($_.Name, [ref]$v)) { $v } else { [version]"0.0" }
        } -Descending | Select-Object -First 1

        if ($null -ne $LatestTools) {
            $Aapt2 = Join-Path $LatestTools.FullName "aapt2.exe"
            if (Test-Path -LiteralPath $Aapt2 -PathType Leaf) {
                $Badging = & $Aapt2 dump badging $OutputApk 2>&1
                if ($LASTEXITCODE -ne 0) {
                    throw "Falha no aapt2 dump badging (código $LASTEXITCODE)."
                }
                $PkgLine = ($Badging | Select-String "^package:" | Select-Object -First 1).Line
                if ($PkgLine) {
                    Write-Host "==> aapt2 ($($LatestTools.Name)): $PkgLine" -ForegroundColor DarkCyan
                    if ($PkgLine -notmatch "versionName='$([regex]::Escape($VersionName))'") {
                        throw "aapt2 reportou versionName diferente de '$VersionName': $PkgLine"
                    }
                }
            }

            $Apksigner = Join-Path $LatestTools.FullName "apksigner.bat"
            if (Test-Path -LiteralPath $Apksigner -PathType Leaf) {
                if ($IsUnsignedRelease) {
                    Write-Host "==> apksigner: pulado (APK release não assinado)." -ForegroundColor Yellow
                } else {
                    & $Apksigner verify --print-certs $OutputApk | Out-Null
                    if ($LASTEXITCODE -ne 0) {
                        throw "Falha na verificação de assinatura do APK via apksigner (código $LASTEXITCODE)."
                    }
                    Write-Host "==> apksigner ($($LatestTools.Name)): assinatura verificada." -ForegroundColor Green
                }
            }
        }
    }
}

Write-Host "==> APK gerado com sucesso (v$VersionName)!" -ForegroundColor Green
Write-Host "    Caminho principal: $OutputApk"
Write-Host "    Tamanho:           $SizeBytes bytes ($SizeMb MB)"
Write-Host "    SHA256:            $Sha256"
