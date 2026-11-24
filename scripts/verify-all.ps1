<#
.SYNOPSIS
    Orquestrador único e change-aware de verificação local pós-prompt para o projeto Vida Acadêmica.
    Detecta automaticamente as áreas alteradas via Git e executa apenas os checks aplicáveis (ou tudo com -Full).
    Compatível com Windows PowerShell 5.1 e PowerShell 7+ (pwsh).
#>
param(
    [switch]$ChangedOnly,
    [switch]$Full,
    [string]$BaseRef = "",
    [switch]$SkipRust,
    [switch]$SkipAndroidTests,
    [switch]$StrictVersion,
    [switch]$VerifyUniffiBindings,
    [switch]$StrictNative
)

$ErrorActionPreference = "Stop"

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Results = [System.Collections.Generic.List[hashtable]]::new()

function Add-StepResult {
    param(
        [string]$Name,
        [string]$Status,
        [long]$ElapsedMs,
        [string]$Detail = ""
    )
    $Results.Add(@{
        Name      = $Name
        Status    = $Status
        ElapsedMs = $ElapsedMs
        Detail    = $Detail
    })
}

function Invoke-TrackedStep {
    param(
        [string]$Name,
        [bool]$ShouldRun,
        [string]$SkipReason,
        [scriptblock]$Action
    )
    if (-not $ShouldRun) {
        Write-Host "[SKIP] $Name ($SkipReason)" -ForegroundColor DarkGray
        Add-StepResult -Name $Name -Status "SKIP" -ElapsedMs 0 -Detail $SkipReason
        return $true
    }

    Write-Host ""
    Write-Host "==> [$Name]" -ForegroundColor Cyan
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $ok = $true
    $detail = ""
    try {
        $global:LASTEXITCODE = 0
        & $Action
        if ($LASTEXITCODE -ne 0) {
            $ok = $false
            $detail = "exit code $LASTEXITCODE"
        }
    } catch {
        $ok = $false
        $detail = $_.Exception.Message
        Write-Host "[FAIL] $Name -> $detail" -ForegroundColor Red
    }
    $sw.Stop()

    if ($ok) {
        Write-Host "[PASS] $Name ($($sw.ElapsedMilliseconds) ms)" -ForegroundColor Green
        Add-StepResult -Name $Name -Status "PASS" -ElapsedMs $sw.ElapsedMilliseconds -Detail $detail
    } else {
        Write-Host "[FAIL] $Name ($($sw.ElapsedMilliseconds) ms)" -ForegroundColor Red
        Add-StepResult -Name $Name -Status "FAIL" -ElapsedMs $sw.ElapsedMilliseconds -Detail $detail
    }
    return $ok
}

function Invoke-GitQuiet {
    param(
        [string[]]$GitArgs,
        [string]$Root = $RepoRoot
    )
    $prevPref = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $out = & git -C $Root @GitArgs 2>$null
        return @{
            Code   = $LASTEXITCODE
            Output = $out
        }
    } finally {
        $ErrorActionPreference = $prevPref
    }
}

function Get-GitChangedFiles {
    param([string]$Root, [string]$ExplicitBase)

    $changed = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)

    $statusLines = & git -C $Root status --porcelain=v1 -uall
    if ($LASTEXITCODE -ne 0) {
        throw "Falha ao executar 'git status --porcelain=v1'."
    }
    foreach ($line in $statusLines) {
        if ([string]::IsNullOrEmpty($line) -or $line.Length -lt 4) { continue }
        $rawPath = $line.Substring(3)
        if ($rawPath -match '^.+ -> (.+)$') {
            $rawPath = $Matches[1]
        }
        $rawPath = $rawPath.Trim('"').Replace('\', '/')
        if (-not [string]::IsNullOrWhiteSpace($rawPath)) {
            [void]$changed.Add($rawPath)
        }
    }

    $resolvedBase = $ExplicitBase
    if ([string]::IsNullOrWhiteSpace($resolvedBase)) {
        $chk = Invoke-GitQuiet -Root $Root -GitArgs @("rev-parse", "--verify", "--quiet", "origin/develop")
        if ($chk.Code -eq 0) {
            $resolvedBase = "origin/develop"
        }
    } else {
        $chk = Invoke-GitQuiet -Root $Root -GitArgs @("rev-parse", "--verify", "--quiet", $ExplicitBase)
        if ($chk.Code -ne 0) {
            Write-Warning "BaseRef '$ExplicitBase' não encontrada no Git; usando apenas working tree/index."
            $resolvedBase = ""
        }
    }

    if (-not [string]::IsNullOrWhiteSpace($resolvedBase)) {
        $diffRes = Invoke-GitQuiet -Root $Root -GitArgs @("diff", "--name-only", "$resolvedBase...HEAD")
        if ($diffRes.Code -eq 0) {
            foreach ($dl in $diffRes.Output) {
                $norm = ([string]$dl).Trim().Replace('\', '/')
                if (-not [string]::IsNullOrWhiteSpace($norm)) {
                    [void]$changed.Add($norm)
                }
            }
        }
    }

    $global:LASTEXITCODE = 0
    return ,$changed
}

function Resolve-NonWslBash {
    $gitCmd = Get-Command "git" -ErrorAction SilentlyContinue
    if ($null -ne $gitCmd) {
        $execPath = (& $gitCmd.Source --exec-path 2>$null | Out-String).Trim()
        if ($execPath -and (Test-Path -LiteralPath $execPath)) {
            $dir = Get-Item -LiteralPath $execPath
            for ($i = 0; $i -lt 4 -and $null -ne $dir; $i++) {
                foreach ($rel in @("bin\bash.exe", "usr\bin\bash.exe")) {
                    $cand = Join-Path $dir.FullName $rel
                    if (Test-Path -LiteralPath $cand -PathType Leaf) {
                        return $cand
                    }
                }
                $dir = $dir.Parent
            }
        }
    }

    $bashCmd = Get-Command "bash" -ErrorAction SilentlyContinue
    if ($null -ne $bashCmd) {
        $src = $bashCmd.Source
        if ($src -notmatch '(?i)\\System32\\bash\.exe$') {
            return $src
        }
    }
    return $null
}

$ModeLabel = if ($Full) { "FULL" } else { "CHANGE-AWARE" }
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " Vida Acadêmica — Verificação Local ($ModeLabel)" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

$ChangedFiles = Get-GitChangedFiles -Root $RepoRoot -ExplicitBase $BaseRef

$Areas = @{
    Rust           = $false
    FfiOrModels    = $false
    MapAssets      = $false
    Android        = $false
    Ios            = $false
    Docs           = $false
    Version        = $false
    ShellScripts   = $false
    PythonScripts  = $false
    Workflows      = $false
}

foreach ($rel in $ChangedFiles) {
    if ($rel -match '^(rust-core/|Cargo\.(toml|lock)$|scripts/verify-core\.ps1$)') {
        $Areas.Rust = $true
    }
    if ($rel -match '^(rust-core/src/ffi/|rust-core/src/models\.rs$|rust-core/uniffi\.toml$|android-app/app/src/main/java/br/ufop/campus/core/uniffi/campus_core\.kt$|ios-app/CampusUFOP/CoreFFI/Generated/|scripts/sync-uniffi-bindings\.ps1$)') {
        $Areas.FfiOrModels = $true
    }
    if ($rel -match '^(rust-core/assets/map_data/|rust-core/src/domain/map/|scripts/check-map-assets\.ps1$)') {
        $Areas.MapAssets = $true
    }
    if ($rel -match '^(android-app/|scripts/build-apk\.ps1$)') {
        $Areas.Android = $true
    }
    if ($rel -match '^(ios-app/|scripts/check-swift-hygiene\.ps1$|scripts/build-ios-.*\.sh$)') {
        $Areas.Ios = $true
    }
    if ($rel -match '^docs/') {
        $Areas.Docs = $true
    }
    if ($rel -match '^(CHANGELOG\.md$|android-app/app/build\.gradle\.kts$|ios-app/project\.yml$|docs/app\.js$|docs/index\.html$|.*ReleaseNotes(Dialog\.kt|SheetView\.swift)$|scripts/(bump-version\.ps1|extract-release-notes\.py|publish-release\.ps1)$)') {
        $Areas.Version = $true
    }
    if ($rel -match '^scripts/.*\.sh$') {
        $Areas.ShellScripts = $true
    }
    if ($rel -match '^scripts/.*\.py$') {
        $Areas.PythonScripts = $true
    }
    if ($rel -match '^\.github/workflows/.*\.ya?ml$') {
        $Areas.Workflows = $true
    }
}

if (-not $Full) {
    if ($ChangedFiles.Count -eq 0) {
        Write-Host "Arquivos alterados detectados: 0 (rodando apenas verificações globais de higiene e versão)." -ForegroundColor Yellow
    } else {
        $ActiveAreas = @($Areas.Keys | Where-Object { $Areas[$_] } | Sort-Object)
        $AreaDisplay = if ($ActiveAreas.Count -gt 0) { $ActiveAreas -join ", " } else { "Geral" }
        Write-Host "Arquivos alterados ($($ChangedFiles.Count)): áreas [$AreaDisplay]" -ForegroundColor Cyan
    }
}

Push-Location $RepoRoot
try {
    # 1. Auditoria de privacidade e arquivos bloqueados (SEMPRE)
    [void](Invoke-TrackedStep -Name "Privacy & Blocked Files" -ShouldRun $true -SkipReason "" -Action {
        $BlockedRx = '(\.(apk|aab|pdf|jks|keystore|p12|pem|key|so|a|xcframework)$|(^|/)jniLibs/|(^|/)local\.properties$|(^|/)\.env($|\..*))'
        $Tracked = & git ls-files
        $Staged = & git diff --cached --name-only
        $FilesToCheck = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
        foreach ($f in $Tracked) { if ($f) { [void]$FilesToCheck.Add($f) } }
        foreach ($f in $Staged) { if ($f) { [void]$FilesToCheck.Add($f) } }
        foreach ($f in $ChangedFiles) { if ($f) { [void]$FilesToCheck.Add($f) } }

        $BlockedHits = [System.Collections.Generic.List[string]]::new()
        foreach ($f in $FilesToCheck) {
            if ($f -match $BlockedRx -and -not ($f -match '(^|/)\.env\.example$')) {
                $BlockedHits.Add($f)
            }
        }
        if ($BlockedHits.Count -gt 0) {
            throw "Arquivo sensível/binário proibido detectado: $($BlockedHits -join ', ')"
        }

        # Regexes concatenadas para evitar auto-correspondência no git grep
        $TokenRx = '(gh' + '[pous]_[A-Za-z0-9]{20,}|' + 'AIza' + '[0-9A-Za-z_-]{30,})'
        $EmailRx = '[A-Za-z0-9._%+-]+@(gmail|hotmail|outlook|yahoo)\.(com|com\.br)'
        $WinRx = '[A-Za-z]:' + '\\' + 'Users' + '\\' + '[^\\' + '[:space:]]+'
        $MacRx = '/' + 'Users' + '/' + '[^/' + '[:space:]]+'
        $LinRx = '/' + 'home' + '/' + '[^/' + '[:space:]]+'
        $PathRx = "($WinRx|$MacRx|$LinRx)"

        $TokenRes = Invoke-GitQuiet -GitArgs @("grep", "--untracked", "-n", "-E", $TokenRx, "--", ":!.github/workflows/ci-quality.yml")
        if ($TokenRes.Code -eq 0 -and $TokenRes.Output) {
            throw "Possível token/chave encontrado:`n$($TokenRes.Output -join "`n")"
        }
        $EmailRes = Invoke-GitQuiet -GitArgs @("grep", "--untracked", "-n", "-E", $EmailRx, "--", ":!.github/workflows/ci-quality.yml")
        if ($EmailRes.Code -eq 0 -and $EmailRes.Output) {
            throw "E-mail pessoal encontrado em arquivo rastreado/novo:`n$($EmailRes.Output -join "`n")"
        }
        $PathRes = Invoke-GitQuiet -GitArgs @("grep", "--untracked", "-n", "-E", $PathRx, "--", ":!.github/workflows/ci-quality.yml", ":!AGENTS.md", ":!scripts/build-apk.ps1")
        if ($PathRes.Code -eq 0 -and $PathRes.Output) {
            throw "Caminho local com nome de usuário encontrado:`n$($PathRes.Output -join "`n")"
        }

        $GradlePath = Join-Path (Join-Path (Join-Path $RepoRoot "android-app") "app") "build.gradle.kts"
        $GradleContent = [System.IO.File]::ReadAllText($GradlePath, [System.Text.Encoding]::UTF8)
        if ($GradleContent -notmatch 'applicationId\s*=\s*"io\.github\.nuncamorto\.vidaacademica"') {
            throw "applicationId esperado não encontrado em android-app/app/build.gradle.kts"
        }
        $global:LASTEXITCODE = 0
        Write-Host "[PASS] Higiene de privacidade, arquivos bloqueados e applicationId OK." -ForegroundColor Green
    })

    # 2. Sincronismo de versão (SEMPRE)
    $UseStrictVer = $StrictVersion -or $Full -or $Areas.Version
    [void](Invoke-TrackedStep -Name "Version Sync Check" -ShouldRun $true -SkipReason "" -Action {
        $BumpScript = Join-Path $PSScriptRoot "bump-version.ps1"
        if ($UseStrictVer) {
            & $BumpScript -CheckOnly -Strict
        } else {
            & $BumpScript -CheckOnly
        }
        if ($LASTEXITCODE -ne 0) { throw "bump-version.ps1 -CheckOnly falhou." }
    })

    # 3. Núcleo Rust
    $RunRust = (-not $SkipRust) -and ($Full -or $Areas.Rust -or $Areas.FfiOrModels -or $Areas.MapAssets)
    $RustSkipMsg = if ($SkipRust) { "-SkipRust informado" } else { "sem alterações em Rust/FFI/Mapa" }
    [void](Invoke-TrackedStep -Name "Rust Core (fmt, clippy, test)" -ShouldRun $RunRust -SkipReason $RustSkipMsg -Action {
        $VerifyCore = Join-Path $PSScriptRoot "verify-core.ps1"
        & $VerifyCore
        if ($LASTEXITCODE -ne 0) { throw "verify-core.ps1 falhou." }
    })

    # 4. Assets do Mapa 2D & paridade estática UniFFI
    $RunMapAssets = $Full -or $Areas.MapAssets -or $Areas.FfiOrModels
    [void](Invoke-TrackedStep -Name "Map Assets & UniFFI Static Check" -ShouldRun $RunMapAssets -SkipReason "sem alterações em Mapa/FFI" -Action {
        $MapCheck = Join-Path $PSScriptRoot "check-map-assets.ps1"
        if ($StrictNative) {
            & $MapCheck -StrictNative
        } else {
            & $MapCheck
        }
        if ($LASTEXITCODE -ne 0) { throw "check-map-assets.ps1 falhou." }
    })

    # 5. Drift determinístico UniFFI (Kotlin + Swift byte a byte via uniffi-bindgen)
    $RunUniffiSync = (-not $SkipRust) -and ($VerifyUniffiBindings -or $Full -or $Areas.FfiOrModels)
    $UniffiSkipMsg = if ($SkipRust) { "-SkipRust informado" } else { "sem alterações em FFI/models.rs (use -VerifyUniffiBindings para forçar)" }
    [void](Invoke-TrackedStep -Name "UniFFI Bindings Drift Check" -ShouldRun $RunUniffiSync -SkipReason $UniffiSkipMsg -Action {
        $SyncScript = Join-Path $PSScriptRoot "sync-uniffi-bindings.ps1"
        & $SyncScript -CheckOnly
        if ($LASTEXITCODE -ne 0) { throw "sync-uniffi-bindings.ps1 -CheckOnly falhou." }
    })

    # 6. Testes JVM Android
    $RunAndroid = (-not $SkipAndroidTests) -and ($Full -or $Areas.Android -or $Areas.FfiOrModels)
    $AndroidSkipMsg = if ($SkipAndroidTests) { "-SkipAndroidTests informado" } else { "sem alterações em android-app/ ou FFI" }
    [void](Invoke-TrackedStep -Name "Android JVM Unit Tests" -ShouldRun $RunAndroid -SkipReason $AndroidSkipMsg -Action {
        $DefaultJavaHome = "C:\Program Files\Android\Android Studio\jbr"
        if (Test-Path -LiteralPath $DefaultJavaHome) {
            $env:JAVA_HOME = $DefaultJavaHome
        }
        if ($env:LOCALAPPDATA) {
            $DefaultAndroidHome = Join-Path $env:LOCALAPPDATA "Android\Sdk"
            if (Test-Path -LiteralPath $DefaultAndroidHome) {
                $env:ANDROID_HOME = $DefaultAndroidHome
                $env:ANDROID_SDK_ROOT = $DefaultAndroidHome
            }
        }
        $AndroidDir = Join-Path $RepoRoot "android-app"
        Push-Location $AndroidDir
        try {
            if ($IsWindows -or ($env:OS -like "*Windows*")) {
                & .\gradlew.bat :app:testDebugUnitTest --no-daemon --offline
                if ($LASTEXITCODE -ne 0) {
                    Write-Warning "Gradle offline falhou (código $LASTEXITCODE); tentando sem --offline..."
                    & .\gradlew.bat :app:testDebugUnitTest --no-daemon
                }
            } else {
                & ./gradlew :app:testDebugUnitTest --no-daemon
            }
            if ($LASTEXITCODE -ne 0) { throw "Gradle :app:testDebugUnitTest falhou." }
        } finally {
            Pop-Location
        }
    })

    # 7. Higiene Swift iOS
    $RunIos = $Full -or $Areas.Ios -or $Areas.FfiOrModels
    [void](Invoke-TrackedStep -Name "iOS Swift Hygiene" -ShouldRun $RunIos -SkipReason "sem alterações em ios-app/ ou FFI" -Action {
        $SwiftHygiene = Join-Path $PSScriptRoot "check-swift-hygiene.ps1"
        & $SwiftHygiene
        if ($LASTEXITCODE -ne 0) { throw "check-swift-hygiene.ps1 falhou." }
    })

    # 8. Download Page JS
    $RunDocs = $Full -or $Areas.Docs
    [void](Invoke-TrackedStep -Name "Docs JavaScript Check" -ShouldRun $RunDocs -SkipReason "sem alterações em docs/" -Action {
        $NodeCmd = Get-Command "node" -ErrorAction SilentlyContinue
        if ($null -eq $NodeCmd) { throw "'node' não encontrado no PATH." }
        & $NodeCmd.Source --check (Join-Path (Join-Path $RepoRoot "docs") "app.js")
        if ($LASTEXITCODE -ne 0) { throw "node --check docs/app.js falhou." }
        Write-Host "[PASS] docs/app.js válido." -ForegroundColor Green
    })

    # 9. Shell scripts iOS (.sh)
    $RunShell = $Full -or $Areas.ShellScripts
    $GitBash = Resolve-NonWslBash
    if ($RunShell -and ($null -eq $GitBash)) {
        [void](Invoke-TrackedStep -Name "iOS Shell Scripts (bash -n)" -ShouldRun $false -SkipReason "bash (Git Bash) não encontrado" -Action {})
    } else {
        [void](Invoke-TrackedStep -Name "iOS Shell Scripts (bash -n)" -ShouldRun $RunShell -SkipReason "sem alterações em scripts/*.sh" -Action {
            & $GitBash -n "scripts/build-ios-xcframework.sh" "scripts/build-ios-ipa.sh"
            if ($LASTEXITCODE -ne 0) { throw "bash -n falhou." }
            Write-Host "[PASS] bash -n OK para scripts/build-ios-*.sh." -ForegroundColor Green
        })
    }

    # 10. Scripts Python (py_compile)
    $RunPython = $Full -or $Areas.PythonScripts
    [void](Invoke-TrackedStep -Name "Python Scripts Syntax" -ShouldRun $RunPython -SkipReason "sem alterações em scripts/*.py" -Action {
        $PyCmd = Get-Command "python" -ErrorAction SilentlyContinue
        if (-not $PyCmd) { $PyCmd = Get-Command "python3" -ErrorAction SilentlyContinue }
        if ($null -eq $PyCmd) { throw "Python 3 não encontrado no PATH." }
        $PyFiles = @(Get-ChildItem -LiteralPath (Join-Path $RepoRoot "scripts") -Filter "*.py" -File | Select-Object -ExpandProperty FullName)
        if ($PyFiles.Count -gt 0) {
            & $PyCmd.Source -m py_compile @PyFiles
            if ($LASTEXITCODE -ne 0) { throw "py_compile falhou." }
        }
        Write-Host "[PASS] py_compile OK ($($PyFiles.Count) arquivo(s))." -ForegroundColor Green
    })

    # 11. Workflows YAML
    $RunWorkflows = $Full -or $Areas.Workflows
    $PyForYaml = Get-Command "python" -ErrorAction SilentlyContinue
    if (-not $PyForYaml) { $PyForYaml = Get-Command "python3" -ErrorAction SilentlyContinue }
    $HasPyYaml = $false
    if ($RunWorkflows -and $null -ne $PyForYaml) {
        $prevPrefYaml = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        try {
            & $PyForYaml.Source -c "import importlib.util, sys; sys.exit(0 if importlib.util.find_spec('yaml') else 1)"
            $HasPyYaml = ($LASTEXITCODE -eq 0)
        } finally {
            $ErrorActionPreference = $prevPrefYaml
            $global:LASTEXITCODE = 0
        }
    }
    if ($RunWorkflows -and -not $HasPyYaml) {
        [void](Invoke-TrackedStep -Name "Workflows YAML Syntax" -ShouldRun $false -SkipReason "PyYAML não instalado no Python local" -Action {})
    } else {
        [void](Invoke-TrackedStep -Name "Workflows YAML Syntax" -ShouldRun $RunWorkflows -SkipReason "sem alterações em .github/workflows/" -Action {
            & $PyForYaml.Source -c "import glob, yaml; files=sorted(glob.glob('.github/workflows/*.yml')); [yaml.safe_load(open(f, encoding='utf-8')) for f in files]; print(f'YAML OK ({len(files)} files)')"
            if ($LASTEXITCODE -ne 0) { throw "Falha ao validar sintaxe YAML dos workflows." }
        })
    }
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "================ Resumo da Verificação ================" -ForegroundColor Cyan
$FailedCount = 0
foreach ($r in $Results) {
    $color = switch ($r.Status) {
        "PASS" { "Green" }
        "FAIL" { "Red"; $FailedCount++ }
        default { "DarkGray" }
    }
    $timeStr = if ($r.Status -eq "SKIP") { "-" } else { "$($r.ElapsedMs) ms" }
    $line = ("{0,-36} {1,-6} {2,10}" -f $r.Name, $r.Status, $timeStr)
    if ($r.Detail -and $r.Status -ne "PASS") {
        $line += "  ($($r.Detail))"
    }
    Write-Host $line -ForegroundColor $color
}
Write-Host "=======================================================" -ForegroundColor Cyan

if ($FailedCount -gt 0) {
    Write-Host "RESULTADO: FALHA ($FailedCount verificação(ões) reprovada(s))." -ForegroundColor Red
    exit 1
}

Write-Host "RESULTADO: SUCESSO!" -ForegroundColor Green
exit 0
