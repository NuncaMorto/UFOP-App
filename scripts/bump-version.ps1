param(
    [string]$Version,

    [int]$Code = 0,

    [switch]$CheckOnly,

    [switch]$Strict,

    [switch]$BumpPatch,

    [switch]$BumpMinor,

    [switch]$Stable
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)

function Read-Utf8File([string]$Path) {
    return [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
}

function Write-Utf8File([string]$Path, [string]$Content) {
    [System.IO.File]::WriteAllText($Path, $Content, $utf8NoBom)
}

function Apply-RegexReplace {
    param(
        [string]$Content,
        [string]$Pattern,
        [string]$Replacement,
        [string]$Label,
        [string]$FilePath
    )
    $rx = [regex]$Pattern
    if (-not $rx.IsMatch($Content)) {
        throw "Pattern not found for '$Label' in $FilePath"
    }
    return $rx.Replace($Content, $Replacement)
}

function Join-PathParts {
    param(
        [string]$Base,
        [string[]]$Parts
    )
    $cur = $Base
    foreach ($p in $Parts) { $cur = Join-Path $cur $p }
    return $cur
}

$targets = [ordered]@{
    buildGradle  = Join-PathParts $repoRoot @("android-app", "app", "build.gradle.kts")
    projectYml   = Join-PathParts $repoRoot @("ios-app", "project.yml")
    docsJs       = Join-PathParts $repoRoot @("docs", "app.js")
    docsHtml     = Join-PathParts $repoRoot @("docs", "index.html")
    swiftNotes   = Join-PathParts $repoRoot @("ios-app", "CampusUFOP", "Features", "Profile", "ReleaseNotesSheetView.swift")
    androidNotes = Join-PathParts $repoRoot @("android-app", "app", "src", "main", "java", "br", "ufop", "campus", "ui", "profile", "ReleaseNotesDialog.kt")
    changelog    = Join-Path $repoRoot "CHANGELOG.md"
}

foreach ($entry in $targets.GetEnumerator()) {
    if (-not (Test-Path -LiteralPath $entry.Value)) {
        Write-Error "Required file not found: $($entry.Value)"
        exit 1
    }
}

$gradleInitial = Read-Utf8File $targets.buildGradle
$currentVersion = ""
$currentCode = 0
if ($gradleInitial -match '\bversionName\s*=\s*"(\d+\.\d+\.\d+)"') {
    $currentVersion = $Matches[1]
}
if ($gradleInitial -match '\bversionCode\s*=\s*(\d+)') {
    $currentCode = [int]$Matches[1]
}

if ($BumpPatch -and $BumpMinor) {
    Write-Error "Cannot combine -BumpPatch and -BumpMinor."
    exit 1
}

if (($BumpPatch -or $BumpMinor) -and $CheckOnly) {
    Write-Error "Cannot combine -BumpPatch/-BumpMinor with -CheckOnly."
    exit 1
}

if ($BumpPatch -or $BumpMinor) {
    if ($currentVersion -notmatch '^(\d+)\.(\d+)\.(\d+)$' -or $currentCode -le 0) {
        Write-Error "Failed to parse current versionName/versionCode from $($targets.buildGradle)."
        exit 1
    }
    $maj = [int]$Matches[1]
    $min = [int]$Matches[2]
    $pat = [int]$Matches[3]
    if ([string]::IsNullOrWhiteSpace($Version)) {
        if ($BumpMinor) {
            $Version = "$maj.$($min + 1).0"
        } else {
            $Version = "$maj.$min.$($pat + 1)"
        }
    }
    if ($Code -le 0) {
        $Code = $currentCode + 1
    }
} elseif ($CheckOnly) {
    if ([string]::IsNullOrWhiteSpace($Version)) {
        $Version = $currentVersion
    }
    if ($Code -le 0) {
        $Code = $currentCode
    }
}

if ([string]::IsNullOrWhiteSpace($Version) -or $Code -le 0) {
    Write-Error "Specify -Version <X.Y.Z> -Code <N>, use -BumpPatch / -BumpMinor, or use -CheckOnly."
    exit 1
}

if ($Version -notmatch '^\d+\.\d+\.\d+$') {
    Write-Error "Invalid SemVer '$Version'. Expected X.Y.Z (e.g., 1.9.13)."
    exit 1
}

if ($Code -le 0) {
    Write-Error "Invalid versionCode '$Code'. Must be > 0."
    exit 1
}

if (-not $CheckOnly) {
    # 1. android-app/app/build.gradle.kts
    $gradleText = Read-Utf8File $targets.buildGradle
    $gradleUpdated = Apply-RegexReplace -Content $gradleText -Pattern '\bversionCode\s*=\s*\d+' -Replacement "versionCode = $Code" -Label "versionCode" -FilePath $targets.buildGradle
    $gradleUpdated = Apply-RegexReplace -Content $gradleUpdated -Pattern '\bversionName\s*=\s*"\d+\.\d+\.\d+"' -Replacement "versionName = `"$Version`"" -Label "versionName" -FilePath $targets.buildGradle
    if ($gradleUpdated -ne $gradleText) {
        Write-Utf8File $targets.buildGradle $gradleUpdated
        Write-Host "[UPDATED] android-app/app/build.gradle.kts -> $Version ($Code)"
    } else {
        Write-Host "[OK]      android-app/app/build.gradle.kts already $Version ($Code)"
    }

    # 2. ios-app/project.yml
    $ymlText = Read-Utf8File $targets.projectYml
    $ymlUpdated = Apply-RegexReplace -Content $ymlText -Pattern 'MARKETING_VERSION:\s*"?\d+\.\d+\.\d+"?' -Replacement "MARKETING_VERSION: `"$Version`"" -Label "MARKETING_VERSION" -FilePath $targets.projectYml
    $ymlUpdated = Apply-RegexReplace -Content $ymlUpdated -Pattern 'CURRENT_PROJECT_VERSION:\s*"?\d+"?' -Replacement "CURRENT_PROJECT_VERSION: `"$Code`"" -Label "CURRENT_PROJECT_VERSION" -FilePath $targets.projectYml
    if ($ymlUpdated -ne $ymlText) {
        Write-Utf8File $targets.projectYml $ymlUpdated
        Write-Host "[UPDATED] ios-app/project.yml -> $Version ($Code)"
    } else {
        Write-Host "[OK]      ios-app/project.yml already $Version ($Code)"
    }

    # 3. docs/app.js
    $jsText = Read-Utf8File $targets.docsJs
    $jsUpdated = Apply-RegexReplace -Content $jsText -Pattern 'releases/tag/v\d+\.\d+\.\d+' -Replacement "releases/tag/v$Version" -Label "FALLBACK URL" -FilePath $targets.docsJs
    $badgeReplacement = if ($Stable) { "`$1`"v$Version`"" } else { "`$1`"v$Version · Beta`"" }
    $jsUpdated = Apply-RegexReplace -Content $jsUpdated -Pattern '(\$\("ver-badge"\)\.textContent\s*=\s*)"v\d+\.\d+\.\d+(?:\s+[^\r\n"]*?Beta)?"' -Replacement $badgeReplacement -Label "fallback badge" -FilePath $targets.docsJs
    $jsUpdated = Apply-RegexReplace -Content $jsUpdated -Pattern '(vers[^\r\n"]*\sv)\d+\.\d+\.\d+(\s+no GitHub)' -Replacement "`${1}$Version`$2" -Label "fallback status" -FilePath $targets.docsJs
    if ($jsUpdated -ne $jsText) {
        Write-Utf8File $targets.docsJs $jsUpdated
        Write-Host "[UPDATED] docs/app.js -> v$Version"
    } else {
        Write-Host "[OK]      docs/app.js already v$Version"
    }

    # 4. docs/index.html
    $htmlText = Read-Utf8File $targets.docsHtml
    $htmlUpdated = Apply-RegexReplace -Content $htmlText -Pattern 'https://github\.com/NuncaMorto/Vida-Academica/releases/tag/v\d+\.\d+\.\d+' -Replacement "https://github.com/NuncaMorto/Vida-Academica/releases/tag/v$Version" -Label "fallback link" -FilePath $targets.docsHtml
    if ($htmlUpdated -ne $htmlText) {
        Write-Utf8File $targets.docsHtml $htmlUpdated
        Write-Host "[UPDATED] docs/index.html -> v$Version"
    } else {
        Write-Host "[OK]      docs/index.html already v$Version"
    }

    # 5. ios-app/CampusUFOP/Features/Profile/ReleaseNotesSheetView.swift
    $swiftNotesText = Read-Utf8File $targets.swiftNotes
    $swiftNotesUpdated = Apply-RegexReplace -Content $swiftNotesText -Pattern 'currentVersionString\s*=\s*"\d+\.\d+\.\d+"' -Replacement "currentVersionString = `"$Version`"" -Label "currentVersionString" -FilePath $targets.swiftNotes
    if ($swiftNotesUpdated -ne $swiftNotesText) {
        Write-Utf8File $targets.swiftNotes $swiftNotesUpdated
        Write-Host "[UPDATED] ios-app/.../ReleaseNotesSheetView.swift -> $Version"
    } else {
        Write-Host "[OK]      ios-app/.../ReleaseNotesSheetView.swift currentVersionString already $Version"
    }
}

# Verification pass across version files
$errors = [System.Collections.Generic.List[string]]::new()
$warnings = [System.Collections.Generic.List[string]]::new()
$vEsc = [regex]::Escape($Version)

$gradleCheck = Read-Utf8File $targets.buildGradle
if ($gradleCheck -notmatch "\bversionCode\s*=\s*$Code\b") {
    $errors.Add("android-app/app/build.gradle.kts missing versionCode = $Code")
}
if ($gradleCheck -notmatch "\bversionName\s*=\s*`"$vEsc`"") {
    $errors.Add("android-app/app/build.gradle.kts missing versionName = `"$Version`"")
}

$ymlCheck = Read-Utf8File $targets.projectYml
if ($ymlCheck -notmatch "MARKETING_VERSION:\s*`"??$vEsc`"??") {
    $errors.Add("ios-app/project.yml missing MARKETING_VERSION: `"$Version`"")
}
if ($ymlCheck -notmatch "CURRENT_PROJECT_VERSION:\s*`"??$Code`"??") {
    $errors.Add("ios-app/project.yml missing CURRENT_PROJECT_VERSION: `"$Code`"")
}

$jsCheck = Read-Utf8File $targets.docsJs
if ($jsCheck -notmatch "releases/tag/v$vEsc") {
    $errors.Add("docs/app.js FALLBACK tag is not v$Version")
}
$badgeCheckRx = '\$\("ver-badge"\)\.textContent\s*=\s*"v' + $vEsc + '(?:\s+[^\r\n"]*?Beta)?"'
if ($jsCheck -notmatch $badgeCheckRx) {
    $errors.Add("docs/app.js fallback badge is not v$Version")
} elseif ($Stable -and $jsCheck -match ('\$\("ver-badge"\)\.textContent\s*=\s*"v' + $vEsc + '\s+[^\r\n"]*?Beta"')) {
    $errors.Add("docs/app.js fallback badge still has Beta suffix under -Stable")
}
if ($jsCheck -notmatch "\sv$vEsc\s+no GitHub") {
    $errors.Add("docs/app.js fallback status text does not reference v$Version")
}

$htmlCheck = Read-Utf8File $targets.docsHtml
if ($htmlCheck -notmatch "releases/tag/v$vEsc") {
    $errors.Add("docs/index.html fallback link is not v$Version")
}

$swiftCheck = Read-Utf8File $targets.swiftNotes
if ($swiftCheck -notmatch "currentVersionString\s*=\s*`"$vEsc`"") {
    $errors.Add("ReleaseNotesSheetView.swift currentVersionString is not `"$Version`"")
}

# Release notes & CHANGELOG synchronism checks
$changelogText = Read-Utf8File $targets.changelog
$sectionRx = [regex]::new("(?ms)^##\s+v$vEsc\b.*?(?=^---\s*$|\z)")
$sectionMatch = $sectionRx.Match($changelogText)
if (-not $sectionMatch.Success) {
    $msg = "CHANGELOG.md does not contain section '## v$Version'"
    if ($Strict) { $errors.Add($msg) } else { $warnings.Add($msg) }
} elseif ($sectionMatch.Value -notmatch "<!--\s*versionCode:\s*$Code\s*-->") {
    $msg = "CHANGELOG.md section '## v$Version' is missing footer '<!-- versionCode: $Code -->'"
    if ($Strict) { $errors.Add($msg) } else { $warnings.Add($msg) }
}

$androidNotesText = Read-Utf8File $targets.androidNotes
if ($androidNotesText -notmatch "version\s*=\s*`"v$vEsc`"") {
    $msg = "ReleaseNotesDialog.kt does not contain an entry for version = `"v$Version`""
    if ($Strict) { $errors.Add($msg) } else { $warnings.Add($msg) }
}

if ($swiftCheck -notmatch "version:\s*`"v$vEsc`"") {
    $msg = "ReleaseNotesSheetView.swift does not contain an entry for version: `"v$Version`""
    if ($Strict) { $errors.Add($msg) } else { $warnings.Add($msg) }
}

foreach ($w in $warnings) {
    Write-Host "[WARN] $w" -ForegroundColor Yellow
}
foreach ($e in $errors) {
    Write-Host "[FAIL] $e" -ForegroundColor Red
}

if ($errors.Count -gt 0) {
    Write-Error "Version check failed with $($errors.Count) error(s)."
    exit 1
}

Write-Host "[PASS] Version synchronization verified for v$Version (versionCode=$Code)." -ForegroundColor Green
exit 0
