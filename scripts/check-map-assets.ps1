<#
.SYNOPSIS
    Validates UFOP offline 2D map assets, strict JSON/GeoJSON structure, privacy hygiene,
    manifest <-> disk <-> assets.rs parity, UniFFI generated Kotlin/Swift bindings,
    and Android native jniLibs freshness.
    Compatible with Windows PowerShell 5.1 and cross-platform PowerShell 7+ (pwsh).
#>
param(
    [switch]$StrictNative
)

$ErrorActionPreference = "Stop"

function Join-PathParts {
    param(
        [Parameter(Mandatory = $true)][string]$Base,
        [Parameter(Mandatory = $true)][string[]]$Parts
    )
    $current = $Base
    foreach ($part in $Parts) {
        $current = Join-Path $current $part
    }
    return $current
}

function Normalize-RelPath {
    param([string]$RelPath)
    if ([string]::IsNullOrWhiteSpace($RelPath)) {
        return ""
    }
    $norm = $RelPath.Trim().Replace('\', '/')
    if ($norm.StartsWith('./')) {
        $norm = $norm.Substring(2)
    }
    return $norm
}

function Get-JsonObjectProperty {
    param(
        [Parameter(Mandatory = $true)]$Element,
        [Parameter(Mandatory = $true)][string]$Name
    )
    foreach ($prop in $Element.EnumerateObject()) {
        if ($prop.Name -eq $Name) {
            return $prop.Value
        }
    }
    return $null
}

function Get-DictValue {
    param(
        [Parameter(Mandatory = $true)]$Container,
        [Parameter(Mandatory = $true)][string]$Key
    )
    if ($Container -is [System.Collections.IDictionary]) {
        if ($null -ne $Container.PSObject.Methods['ContainsKey']) {
            if ($Container.ContainsKey($Key)) {
                return ,$Container[$Key]
            }
            return $null
        }
        if ($Container.Contains($Key)) {
            return ,$Container[$Key]
        }
        return $null
    }
    $prop = $Container.PSObject.Properties[$Key]
    if ($null -ne $prop) {
        return ,$prop.Value
    }
    return $null
}

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$MapDataDir = Join-PathParts -Base $RepoRoot -Parts @("rust-core", "assets", "map_data")
$ManifestPath = Join-PathParts -Base $MapDataDir -Parts @("maps", "campus_manifest.json")
$AssetsRsPath = Join-PathParts -Base $RepoRoot -Parts @("rust-core", "src", "domain", "map", "assets.rs")
$UniffiTomlPath = Join-PathParts -Base $RepoRoot -Parts @("rust-core", "uniffi.toml")

$Errors = [System.Collections.Generic.List[string]]::new()

function Add-CheckFailure {
    param([string]$Message)
    $script:Errors.Add($Message)
    Write-Host "[FAIL] $Message" -ForegroundColor Red
}

function Add-CheckPass {
    param([string]$Message)
    Write-Host "[PASS] $Message" -ForegroundColor Green
}

foreach ($reqPath in @($MapDataDir, $ManifestPath, $AssetsRsPath, $UniffiTomlPath)) {
    if (-not (Test-Path -LiteralPath $reqPath)) {
        Add-CheckFailure "Required path not found: $reqPath"
    }
}

if ($Errors.Count -gt 0) {
    exit 1
}

# 1. Strict JSON & GeoJSON validation for all .json / .geojson files under rust-core/assets/map_data/
$UseSystemTextJson = $false
try {
    if ($null -ne ([type]"System.Text.Json.JsonDocument")) {
        $UseSystemTextJson = $true
    }
} catch {
    $UseSystemTextJson = $false
}

$JsSerializer = $null
if (-not $UseSystemTextJson) {
    try {
        Add-Type -AssemblyName System.Web.Extensions -ErrorAction Stop
        $JsSerializer = [System.Web.Script.Serialization.JavaScriptSerializer]::new()
        $JsSerializer.MaxJsonLength = [int]::MaxValue
    } catch {
        $JsSerializer = $null
    }
}

$MapDataFullDir = [System.IO.Path]::GetFullPath($MapDataDir)
$MapDataPrefix = $MapDataFullDir.TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar

$AllMapDataFiles = Get-ChildItem -Path $MapDataDir -Recurse -File | Sort-Object FullName
$JsonAndGeoFiles = @($AllMapDataFiles | Where-Object { $_.Extension -in @(".json", ".geojson") })
$GeoJsonFiles = @($JsonAndGeoFiles | Where-Object { $_.Extension -eq ".geojson" })

$JsonValidCount = 0
$GeoJsonValidCount = 0
$ManifestObj = $null

foreach ($file in $JsonAndGeoFiles) {
    $rel = [System.IO.Path]::GetFullPath($file.FullName).Substring($MapDataPrefix.Length).Replace('\', '/')
    $raw = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)

    if ([string]::IsNullOrWhiteSpace($raw)) {
        Add-CheckFailure "Empty JSON/GeoJSON file: $rel"
        continue
    }

    $isGeoJson = ($file.Extension -eq ".geojson")
    $isManifest = ($rel -eq "maps/campus_manifest.json")

    try {
        if ($UseSystemTextJson) {
            $doc = [System.Text.Json.JsonDocument]::Parse($raw)
            try {
                $root = $doc.RootElement
                $JsonValidCount++

                if ($isGeoJson) {
                    if ($root.ValueKind -ne [System.Text.Json.JsonValueKind]::Object) {
                        Add-CheckFailure "GeoJSON root is not a JSON object: $rel"
                        continue
                    }
                    $typeProp = Get-JsonObjectProperty -Element $root -Name "type"
                    $featuresProp = Get-JsonObjectProperty -Element $root -Name "features"
                    if ($null -eq $typeProp -or $typeProp.ValueKind -ne [System.Text.Json.JsonValueKind]::String -or $typeProp.GetString() -ne "FeatureCollection") {
                        Add-CheckFailure "GeoJSON missing type='FeatureCollection': $rel"
                        continue
                    }
                    if ($null -eq $featuresProp -or $featuresProp.ValueKind -ne [System.Text.Json.JsonValueKind]::Array) {
                        Add-CheckFailure "GeoJSON missing 'features' array: $rel"
                        continue
                    }
                    $GeoJsonValidCount++
                }

                if ($isManifest) {
                    if ($root.ValueKind -ne [System.Text.Json.JsonValueKind]::Object) {
                        Add-CheckFailure "campus_manifest.json root is not a JSON object"
                        continue
                    }
                    $campusesProp = Get-JsonObjectProperty -Element $root -Name "campuses"
                    if ($null -eq $campusesProp -or $campusesProp.ValueKind -ne [System.Text.Json.JsonValueKind]::Array) {
                        Add-CheckFailure "campus_manifest.json missing 'campuses' array"
                    } else {
                        $campusesList = [System.Collections.Generic.List[hashtable]]::new()
                        foreach ($cElem in $campusesProp.EnumerateArray()) {
                            $cidProp = Get-JsonObjectProperty -Element $cElem -Name "campus_id"
                            $baseProp = Get-JsonObjectProperty -Element $cElem -Name "base_map"
                            $occProp = Get-JsonObjectProperty -Element $cElem -Name "occupancy_file"
                            $ovArrProp = Get-JsonObjectProperty -Element $cElem -Name "indoor_overlays"

                            $cid = if ($null -ne $cidProp -and $cidProp.ValueKind -eq [System.Text.Json.JsonValueKind]::String) { $cidProp.GetString() } else { "" }
                            $baseMap = if ($null -ne $baseProp -and $baseProp.ValueKind -eq [System.Text.Json.JsonValueKind]::String) { $baseProp.GetString() } else { "" }
                            $occFile = if ($null -ne $occProp -and $occProp.ValueKind -eq [System.Text.Json.JsonValueKind]::String) { $occProp.GetString() } else { "" }
                            $overlays = [System.Collections.Generic.List[string]]::new()

                            if ($null -ne $ovArrProp -and $ovArrProp.ValueKind -eq [System.Text.Json.JsonValueKind]::Array) {
                                foreach ($ovElem in $ovArrProp.EnumerateArray()) {
                                    $ovFileProp = Get-JsonObjectProperty -Element $ovElem -Name "file"
                                    if ($null -ne $ovFileProp -and $ovFileProp.ValueKind -eq [System.Text.Json.JsonValueKind]::String) {
                                        $overlays.Add($ovFileProp.GetString())
                                    } else {
                                        $overlays.Add("")
                                    }
                                }
                            }

                            $campusesList.Add(@{
                                campus_id       = $cid
                                base_map        = $baseMap
                                occupancy_file  = $occFile
                                indoor_overlays = $overlays
                            })
                        }
                        $ManifestObj = $campusesList
                    }
                }
            } finally {
                $doc.Dispose()
            }
        } else {
            $parsed = $null
            if ($null -ne $JsSerializer) {
                $parsed = $JsSerializer.DeserializeObject($raw)
            } else {
                $parsed = $raw | ConvertFrom-Json
            }
            $JsonValidCount++

            if ($isGeoJson) {
                if ($null -eq $parsed) {
                    Add-CheckFailure "GeoJSON parsed as null: $rel"
                    continue
                }
                $typeVal = Get-DictValue -Container $parsed -Key "type"
                $featuresVal = Get-DictValue -Container $parsed -Key "features"
                if ($typeVal -ne "FeatureCollection") {
                    Add-CheckFailure "GeoJSON missing type='FeatureCollection': $rel"
                    continue
                }
                if (-not ($featuresVal -is [System.Collections.IList] -or $featuresVal -is [System.Array])) {
                    Add-CheckFailure "GeoJSON missing 'features' array: $rel"
                    continue
                }
                $GeoJsonValidCount++
            }

            if ($isManifest) {
                $campusesRaw = Get-DictValue -Container $parsed -Key "campuses"
                if (-not ($campusesRaw -is [System.Collections.IList] -or $campusesRaw -is [System.Array])) {
                    Add-CheckFailure "campus_manifest.json missing 'campuses' array"
                } else {
                    $campusesList = [System.Collections.Generic.List[hashtable]]::new()
                    foreach ($cElem in $campusesRaw) {
                        $cidRaw = Get-DictValue -Container $cElem -Key "campus_id"
                        $baseRaw = Get-DictValue -Container $cElem -Key "base_map"
                        $occRaw = Get-DictValue -Container $cElem -Key "occupancy_file"
                        $ovArrRaw = Get-DictValue -Container $cElem -Key "indoor_overlays"

                        $cid = if ($null -ne $cidRaw) { [string]$cidRaw } else { "" }
                        $baseMap = if ($null -ne $baseRaw) { [string]$baseRaw } else { "" }
                        $occFile = if ($null -ne $occRaw) { [string]$occRaw } else { "" }
                        $overlays = [System.Collections.Generic.List[string]]::new()

                        if ($null -ne $ovArrRaw) {
                            foreach ($ovElem in $ovArrRaw) {
                                $ovFileRaw = Get-DictValue -Container $ovElem -Key "file"
                                if ($null -ne $ovFileRaw) {
                                    $overlays.Add([string]$ovFileRaw)
                                } else {
                                    $overlays.Add("")
                                }
                            }
                        }

                        $campusesList.Add(@{
                            campus_id       = $cid
                            base_map        = $baseMap
                            occupancy_file  = $occFile
                            indoor_overlays = $overlays
                        })
                    }
                    $ManifestObj = $campusesList
                }
            }
        }
    } catch {
        Add-CheckFailure "JSON parse error in $rel ($($_.Exception.GetType().Name): $($_.Exception.Message))"
    }
}

if ($JsonAndGeoFiles.Count -eq 0) {
    Add-CheckFailure "No JSON/GeoJSON files found under rust-core/assets/map_data/"
} elseif ($JsonValidCount -eq $JsonAndGeoFiles.Count -and $GeoJsonValidCount -eq $GeoJsonFiles.Count) {
    Add-CheckPass "Strict JSON valid for $JsonValidCount/$($JsonAndGeoFiles.Count) asset file(s) ($GeoJsonValidCount/$($GeoJsonFiles.Count) GeoJSON FeatureCollections)."
}

# 2. Cross-validate campus_manifest.json <-> disk files <-> rust-core/src/domain/map/assets.rs
$AssetsRsText = [System.IO.File]::ReadAllText($AssetsRsPath, [System.Text.Encoding]::UTF8)
$AssetsRsDir = Split-Path $AssetsRsPath -Parent

$IncludeMatches = [regex]::Matches(
    $AssetsRsText,
    '\bpub\s+const\s+([A-Za-z0-9_]+)\s*:\s*&str\s*=\s*include_str!\(\s*"([^"]+)"\s*\)\s*;'
)

$ConstToRelPath = @{}
$EmbeddedRelPaths = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)

foreach ($m in $IncludeMatches) {
    $constName = $m.Groups[1].Value
    $incRel = $m.Groups[2].Value
    $incNative = $incRel.Replace('/', [System.IO.Path]::DirectorySeparatorChar)
    $resolved = [System.IO.Path]::GetFullPath((Join-Path $AssetsRsDir $incNative))

    if (-not (Test-Path -LiteralPath $resolved -PathType Leaf)) {
        Add-CheckFailure "assets.rs include_str! target missing on disk for ${constName}: $incRel"
        continue
    }
    if (-not $resolved.StartsWith($MapDataPrefix, [System.StringComparison]::Ordinal)) {
        Add-CheckFailure "assets.rs include_str! target escapes map_data directory for ${constName}: $incRel"
        continue
    }

    $relUnderMapData = $resolved.Substring($MapDataPrefix.Length).Replace('\', '/')
    $ConstToRelPath[$constName] = $relUnderMapData
    [void]$EmbeddedRelPaths.Add($relUnderMapData)
}

if ($ConstToRelPath.Count -eq 0) {
    Add-CheckFailure "No include_str! declarations parsed from rust-core/src/domain/map/assets.rs"
}

$FnMatch = [regex]::Match(
    $AssetsRsText,
    '(?s)pub\s+fn\s+get_embedded_asset\b.*?match\s+clean\s*\{(?<body>.*?)\}'
)

$MappedAssetPaths = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
if (-not $FnMatch.Success) {
    Add-CheckFailure "Could not locate match block in get_embedded_asset in assets.rs"
} else {
    $armMatches = [regex]::Matches(
        $FnMatch.Groups["body"].Value,
        '"([^"]+)"\s*=>\s*Some\(\s*([A-Za-z0-9_]+)\s*\)'
    )
    foreach ($arm in $armMatches) {
        $armKey = Normalize-RelPath $arm.Groups[1].Value
        $armConst = $arm.Groups[2].Value
        [void]$MappedAssetPaths.Add($armKey)

        if (-not $ConstToRelPath.ContainsKey($armConst)) {
            Add-CheckFailure "get_embedded_asset arm '$armKey' references unknown const '$armConst'"
        } elseif ($ConstToRelPath[$armConst] -ne $armKey) {
            Add-CheckFailure "get_embedded_asset arm '$armKey' points to '$armConst' whose include_str! is '$($ConstToRelPath[$armConst])'"
        }
    }

    foreach ($kv in $ConstToRelPath.GetEnumerator()) {
        if (-not $MappedAssetPaths.Contains($kv.Value)) {
            Add-CheckFailure "assets.rs const '$($kv.Key)' ($($kv.Value)) is not mapped in get_embedded_asset"
        }
    }
}

$ParityFailed = $false
if ($null -eq $ManifestObj -or $ManifestObj.Count -eq 0) {
    Add-CheckFailure "campus_manifest.json has no campuses"
    $ParityFailed = $true
} else {
    foreach ($campus in $ManifestObj) {
        $cid = $campus["campus_id"]
        $baseRel = Normalize-RelPath $campus["base_map"]
        if ([string]::IsNullOrWhiteSpace($baseRel)) {
            Add-CheckFailure "Manifest campus '$cid' has empty base_map"
            $ParityFailed = $true
        } else {
            $baseDisk = Join-Path $MapDataFullDir $baseRel.Replace('/', [System.IO.Path]::DirectorySeparatorChar)
            if (-not (Test-Path -LiteralPath $baseDisk -PathType Leaf)) {
                Add-CheckFailure "Manifest base_map missing on disk for '$cid': $baseRel"
                $ParityFailed = $true
            }
            if (-not $MappedAssetPaths.Contains($baseRel)) {
                Add-CheckFailure "Manifest base_map not mapped in assets.rs for '$cid': $baseRel"
                $ParityFailed = $true
            }
        }

        $occRel = Normalize-RelPath $campus["occupancy_file"]
        if (-not [string]::IsNullOrWhiteSpace($occRel)) {
            $occDisk = Join-Path $MapDataFullDir $occRel.Replace('/', [System.IO.Path]::DirectorySeparatorChar)
            if (-not (Test-Path -LiteralPath $occDisk -PathType Leaf)) {
                Add-CheckFailure "Manifest occupancy_file missing on disk for '$cid': $occRel"
                $ParityFailed = $true
            }
            if (-not $MappedAssetPaths.Contains($occRel)) {
                Add-CheckFailure "Manifest occupancy_file not mapped in assets.rs for '$cid': $occRel"
                $ParityFailed = $true
            }
        }

        foreach ($ovRelRaw in $campus["indoor_overlays"]) {
            $ovRel = Normalize-RelPath $ovRelRaw
            if ([string]::IsNullOrWhiteSpace($ovRel)) {
                Add-CheckFailure "Manifest indoor_overlay entry has empty file path for '$cid'"
                $ParityFailed = $true
                continue
            }
            $ovDisk = Join-Path $MapDataFullDir $ovRel.Replace('/', [System.IO.Path]::DirectorySeparatorChar)
            if (-not (Test-Path -LiteralPath $ovDisk -PathType Leaf)) {
                Add-CheckFailure "Manifest indoor_overlay missing on disk for '$cid': $ovRel"
                $ParityFailed = $true
            }
            if (-not $MappedAssetPaths.Contains($ovRel)) {
                Add-CheckFailure "Manifest indoor_overlay not mapped in assets.rs for '$cid': $ovRel"
                $ParityFailed = $true
            }
        }
    }

    if (-not $ParityFailed -and $Errors.Count -eq 0) {
        Add-CheckPass "Manifest <-> disk <-> assets.rs parity verified ($($ManifestObj.Count) campuses, $($MappedAssetPaths.Count) embedded assets)."
    }
}

# Account for every file under rust-core/assets/map_data/
$ExpectedNonEmbedded = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
[void]$ExpectedNonEmbedded.Add("maps/indoor/TEMPLATE_building_overlay.geojson")
[void]$ExpectedNonEmbedded.Add("schema/test_schedule_resolutions_2026_2.json")

$AccountingErrors = 0
foreach ($expectedNonEmb in $ExpectedNonEmbedded) {
    $nonEmbDisk = Join-Path $MapDataFullDir $expectedNonEmb.Replace('/', [System.IO.Path]::DirectorySeparatorChar)
    if (-not (Test-Path -LiteralPath $nonEmbDisk -PathType Leaf)) {
        Add-CheckFailure "Expected non-embedded template/fixture missing on disk: $expectedNonEmb"
        $AccountingErrors++
    }
}

$DiskRelSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
foreach ($file in $AllMapDataFiles) {
    $rel = [System.IO.Path]::GetFullPath($file.FullName).Substring($MapDataPrefix.Length).Replace('\', '/')
    [void]$DiskRelSet.Add($rel)

    if ($file.Extension -notin @(".json", ".geojson")) {
        Add-CheckFailure "Unexpected non-JSON/GeoJSON file in map_data: $rel"
        $AccountingErrors++
        continue
    }

    $isEmbedded = $MappedAssetPaths.Contains($rel)
    $isExpectedNonEmb = $ExpectedNonEmbedded.Contains($rel)
    if (-not $isEmbedded -and -not $isExpectedNonEmb) {
        Add-CheckFailure "Unaccounted map_data asset (neither embedded in assets.rs nor in expected non-embedded allowlist): $rel"
        $AccountingErrors++
    } elseif ($isEmbedded -and $isExpectedNonEmb) {
        Add-CheckFailure "Asset listed as non-embedded template/fixture is also embedded in assets.rs: $rel"
        $AccountingErrors++
    }
}

if ($AccountingErrors -eq 0) {
    Add-CheckPass "Accounted for all $($DiskRelSet.Count) file(s) under rust-core/assets/map_data/ ($($MappedAssetPaths.Count) embedded, $($ExpectedNonEmbedded.Count) template/fixture)."
}

# 3. Privacy scan over all files under rust-core/assets/map_data/
# Construct local-user path regexes via concatenation to avoid self-matching CI git grep checks.
$WinUserRx = [regex]::new(('[A-Za-z]:' + '\\' + 'Users' + '\\' + '[^\\\s"''`]+'))
$MacUserRx = [regex]::new(('/' + 'Users' + '/' + '[^/\s"''`]+'))
$LinuxUserRx = [regex]::new(('/' + 'home' + '/' + '[^/\s"''`]+'))
$TokenRx = [regex]::new(('(?:' + 'gh' + '[pous]_[A-Za-z0-9]{20,}|' + 'AIza' + '[0-9A-Za-z_-]{30,})'))
$EmailRx = [regex]::new('[A-Za-z0-9._%+-]+@(?:gmail|hotmail|outlook|yahoo|live|icloud|proton|protonmail|uol|bol|terra)\.(?:com|com\.br|net|org|me)\b')

$PrivacyRules = @(
    @{ Name = "Personal email"; Regex = $EmailRx }
    @{ Name = "API/GitHub token"; Regex = $TokenRx }
    @{ Name = "Windows user path"; Regex = $WinUserRx }
    @{ Name = "macOS user path"; Regex = $MacUserRx }
    @{ Name = "Linux home path"; Regex = $LinuxUserRx }
)

$PrivacyViolations = 0
foreach ($file in $AllMapDataFiles) {
    $rel = [System.IO.Path]::GetFullPath($file.FullName).Substring($MapDataPrefix.Length).Replace('\', '/')
    $raw = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)

    foreach ($rule in $PrivacyRules) {
        $match = $rule.Regex.Match($raw)
        if ($match.Success) {
            $lineNumber = 1 + ($raw.Substring(0, $match.Index) -split "`n").Count - 1
            $PrivacyViolations++
            Add-CheckFailure "Privacy violation in map_data/$rel at line $lineNumber (rule: $($rule.Name))"
        }
    }
}

if ($PrivacyViolations -eq 0) {
    Add-CheckPass "Privacy scan passed across $($AllMapDataFiles.Count) map_data file(s) (0 tokens/emails/local paths)."
}

# 4. Parse rust-core/uniffi.toml and validate Kotlin & Swift generated bindings
$UniffiText = [System.IO.File]::ReadAllText($UniffiTomlPath, [System.Text.Encoding]::UTF8)
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

function Strip-RustComments {
    param([string]$Source)
    # Remove /* ... */ e // ... preservando strings basicas e quebras de linha
    $noBlock = [regex]::Replace($Source, '/\*[\s\S]*?\*/', {
        param($m)
        return ([regex]::Replace($m.Value, '[^\n]', ' '))
    })
    $lines = $noBlock -split "`r?`n"
    $out = [System.Collections.Generic.List[string]]::new($lines.Length)
    foreach ($ln in $lines) {
        $idx = $ln.IndexOf('//')
        if ($idx -ge 0) {
            $out.Add($ln.Substring(0, $idx))
        } else {
            $out.Add($ln)
        }
    }
    return ($out -join "`n")
}

function Get-ExpectedRustFfiChecksums {
    param([string]$FfiDir)
    $expected = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    if (-not (Test-Path -LiteralPath $FfiDir -PathType Container)) {
        return ,$expected
    }

    $rsFiles = Get-ChildItem -LiteralPath $FfiDir -Filter "*.rs" -File | Sort-Object Name
    foreach ($file in $rsFiles) {
        $raw = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
        $clean = Strip-RustComments -Source $raw

        # Tokenizacao simples por linha/chaves suficiente para blocos #[uniffi::export] em rust-core/src/ffi/
        $braceDepth = 0
        $pendingExport = $false
        $pendingConstructor = $false
        $inExportImpl = $false
        $implStructLower = ""
        $implBraceDepth = 0

        foreach ($rawLine in ($clean -split "`n")) {
            $line = $rawLine.Trim()
            if ([string]::IsNullOrWhiteSpace($line)) { continue }

            if (-not $inExportImpl) {
                if ($line -match '#\[\s*uniffi::export\b') {
                    $pendingExport = $true
                    continue
                }
                if ($pendingExport -and $braceDepth -eq 0) {
                    if ($line -match '^#\[') {
                        continue
                    }
                    if ($line -match '^(?:pub(?:\([^)]*\))?\s+)?fn\s+([A-Za-z0-9_]+)\s*\(') {
                        $fnName = $Matches[1]
                        [void]$expected.Add("uniffi_campus_core_checksum_func_$fnName")
                        $pendingExport = $false
                    } elseif ($line -match '^impl\s+(?:<[^>]+>\s*)?([A-Za-z0-9_]+)\b') {
                        $implStructLower = $Matches[1].ToLowerInvariant()
                        $inExportImpl = $true
                        $implBraceDepth = $braceDepth
                        $pendingExport = $false
                        $pendingConstructor = $false
                    } elseif ($line -notmatch '^pub\s+') {
                        $pendingExport = $false
                    }
                }
            } else {
                if ($braceDepth -eq ($implBraceDepth + 1)) {
                    if ($line -match '#\[\s*uniffi::constructor\b') {
                        $pendingConstructor = $true
                    } elseif ($line -match '^pub\s+(?:async\s+)?fn\s+([A-Za-z0-9_]+)\s*\(') {
                        $mName = $Matches[1]
                        if ($pendingConstructor) {
                            [void]$expected.Add("uniffi_campus_core_checksum_constructor_${implStructLower}_$mName")
                        } else {
                            [void]$expected.Add("uniffi_campus_core_checksum_method_${implStructLower}_$mName")
                        }
                        $pendingConstructor = $false
                    } elseif ($line -notmatch '^#\[' -and $line -notmatch '^pub\s*$') {
                        if ($line -match ';') {
                            $pendingConstructor = $false
                        }
                    }
                }
            }

            $opens = ([regex]::Matches($line, '\{')).Count
            $closes = ([regex]::Matches($line, '\}')).Count
            $braceDepth += ($opens - $closes)

            if ($inExportImpl -and $braceDepth -le $implBraceDepth) {
                $inExportImpl = $false
                $implStructLower = ""
                $pendingConstructor = $false
            }
        }
    }

    return ,$expected
}

if ([string]::IsNullOrWhiteSpace($KotlinPkg) -or [string]::IsNullOrWhiteSpace($KotlinCdylib) -or [string]::IsNullOrWhiteSpace($SwiftModule)) {
    Add-CheckFailure "Failed to parse package_name/cdylib_name/module_name from rust-core/uniffi.toml"
} else {
    $RequiredSymbols = @("CampusEngine", "CampusMapEngine", "RenderBatch", "validateOverlay")
    $KotlinContractVersion = $null
    $SwiftContractVersion = $null
    $KotlinChecksums = @{}
    $SwiftChecksums = @{}
    $KtReadable = $false
    $SwReadable = $false

    # Kotlin binding check
    $PkgSubParts = $KotlinPkg.Split('.')
    $KotlinBindingPath = Join-PathParts -Base (Join-PathParts -Base $RepoRoot -Parts @("android-app", "app", "src", "main", "java")) -Parts ($PkgSubParts + @("$KotlinCdylib.kt"))

    if (-not (Test-Path -LiteralPath $KotlinBindingPath -PathType Leaf)) {
        Add-CheckFailure "Kotlin UniFFI binding not found: $KotlinBindingPath"
    } else {
        $ktInfo = Get-Item -LiteralPath $KotlinBindingPath
        if ($ktInfo.Length -le 0) {
            Add-CheckFailure "Kotlin UniFFI binding is empty: $KotlinBindingPath"
        } else {
            $ktText = [System.IO.File]::ReadAllText($KotlinBindingPath, [System.Text.Encoding]::UTF8)
            $pkgEsc = [regex]::Escape($KotlinPkg)
            $ktMissing = [System.Collections.Generic.List[string]]::new()
            if ($ktText -notmatch "(?m)^\s*package\s+$pkgEsc\b") {
                $ktMissing.Add("package $KotlinPkg")
            }
            foreach ($sym in $RequiredSymbols) {
                if ($ktText -notmatch "\b$([regex]::Escape($sym))\b") {
                    $ktMissing.Add($sym)
                }
            }
            if ($ktMissing.Count -gt 0) {
                Add-CheckFailure "Kotlin UniFFI binding missing required declarations/symbols: $($ktMissing -join ', ')"
            } else {
                $KtReadable = $true
                if ($ktText -match '\bval\s+bindings_contract_version\s*=\s*(\d+)') {
                    $KotlinContractVersion = $Matches[1]
                }
                $ktCsMatches = [regex]::Matches($ktText, 'lib\.(uniffi_campus_core_checksum_[A-Za-z0-9_]+)\(\)\s*!=\s*(\d+)\.toShort\(\)')
                foreach ($m in $ktCsMatches) {
                    $KotlinChecksums[$m.Groups[1].Value] = $m.Groups[2].Value
                }
                Add-CheckPass "Kotlin UniFFI binding verified ($KotlinPkg/$KotlinCdylib.kt, $($ktInfo.Length) bytes, $($KotlinChecksums.Count) checksums)."
            }
        }
    }

    # Swift bindings check
    $SwiftGenDir = Join-PathParts -Base $RepoRoot -Parts @("ios-app", "CampusUFOP", "CoreFFI", "Generated")
    $SwiftFile = Join-Path $SwiftGenDir "$SwiftModule.swift"
    $SwiftHeader = Join-Path $SwiftGenDir "${SwiftModule}FFI.h"
    $SwiftModuleMap = Join-Path $SwiftGenDir "${SwiftModule}FFI.modulemap"

    $swiftMissingFiles = $false
    foreach ($sf in @($SwiftFile, $SwiftHeader, $SwiftModuleMap)) {
        if (-not (Test-Path -LiteralPath $sf -PathType Leaf)) {
            Add-CheckFailure "Swift UniFFI binding file not found: $sf"
            $swiftMissingFiles = $true
        } elseif ((Get-Item -LiteralPath $sf).Length -le 0) {
            Add-CheckFailure "Swift UniFFI binding file is empty: $sf"
            $swiftMissingFiles = $true
        }
    }

    if (-not $swiftMissingFiles) {
        $mmText = [System.IO.File]::ReadAllText($SwiftModuleMap, [System.Text.Encoding]::UTF8)
        $swText = [System.IO.File]::ReadAllText($SwiftFile, [System.Text.Encoding]::UTF8)
        $modEsc = [regex]::Escape("${SwiftModule}FFI")
        $swMissing = [System.Collections.Generic.List[string]]::new()

        if ($mmText -notmatch "\bmodule\s+$modEsc\b") {
            $swMissing.Add("module ${SwiftModule}FFI in modulemap")
        }
        foreach ($sym in $RequiredSymbols) {
            if ($swText -notmatch "\b$([regex]::Escape($sym))\b") {
                $swMissing.Add($sym)
            }
        }
        if ($swMissing.Count -gt 0) {
            Add-CheckFailure "Swift UniFFI bindings missing required declarations/symbols: $($swMissing -join ', ')"
        } else {
            $SwReadable = $true
            if ($swText -match '\blet\s+bindings_contract_version\s*=\s*(\d+)') {
                $SwiftContractVersion = $Matches[1]
            }
            $swCsMatches = [regex]::Matches($swText, 'if\s*\(\s*(uniffi_campus_core_checksum_[A-Za-z0-9_]+)\(\)\s*!=\s*(\d+)\s*\)')
            foreach ($m in $swCsMatches) {
                $SwiftChecksums[$m.Groups[1].Value] = $m.Groups[2].Value
            }
            $swBytes = (Get-Item -LiteralPath $SwiftFile).Length
            Add-CheckPass "Swift UniFFI bindings verified ($SwiftModule.swift + FFI header + modulemap, $swBytes bytes, $($SwiftChecksums.Count) checksums)."
        }
    }

    # Cross-check contract version, Kotlin <-> Swift checksum parity, and Rust FFI exports
    if ($KtReadable -and $SwReadable) {
        $parityOk = $true
        if ([string]::IsNullOrWhiteSpace($KotlinContractVersion) -or [string]::IsNullOrWhiteSpace($SwiftContractVersion)) {
            Add-CheckFailure "Could not parse UniFFI bindings_contract_version from Kotlin ('$KotlinContractVersion') or Swift ('$SwiftContractVersion')."
            $parityOk = $false
        } elseif ($KotlinContractVersion -ne $SwiftContractVersion) {
            Add-CheckFailure "UniFFI contract version mismatch: Kotlin=$KotlinContractVersion vs Swift=$SwiftContractVersion."
            $parityOk = $false
        }

        if ($KotlinChecksums.Count -eq 0 -or $SwiftChecksums.Count -eq 0) {
            Add-CheckFailure "No UniFFI API checksums parsed (Kotlin=$($KotlinChecksums.Count), Swift=$($SwiftChecksums.Count))."
            $parityOk = $false
        } else {
            foreach ($k in $KotlinChecksums.Keys) {
                if (-not $SwiftChecksums.ContainsKey($k)) {
                    Add-CheckFailure "UniFFI checksum present in Kotlin but missing in Swift: $k"
                    $parityOk = $false
                } elseif ($KotlinChecksums[$k] -ne $SwiftChecksums[$k]) {
                    Add-CheckFailure "UniFFI checksum mismatch for '$k': Kotlin=$($KotlinChecksums[$k]) vs Swift=$($SwiftChecksums[$k])"
                    $parityOk = $false
                }
            }
            foreach ($k in $SwiftChecksums.Keys) {
                if (-not $KotlinChecksums.ContainsKey($k)) {
                    Add-CheckFailure "UniFFI checksum present in Swift but missing in Kotlin: $k"
                    $parityOk = $false
                }
            }
        }

        $FfiDir = Join-PathParts -Base $RepoRoot -Parts @("rust-core", "src", "ffi")
        $ExpectedFfi = Get-ExpectedRustFfiChecksums -FfiDir $FfiDir
        if ($ExpectedFfi.Count -eq 0) {
            Add-CheckFailure "No #[uniffi::export] declarations parsed from rust-core/src/ffi/"
            $parityOk = $false
        } else {
            foreach ($expSym in $ExpectedFfi) {
                if (-not $KotlinChecksums.ContainsKey($expSym)) {
                    Add-CheckFailure "Rust FFI export '$expSym' is missing from Kotlin binding (run .\scripts\sync-uniffi-bindings.ps1)."
                    $parityOk = $false
                }
                if (-not $SwiftChecksums.ContainsKey($expSym)) {
                    Add-CheckFailure "Rust FFI export '$expSym' is missing from Swift binding (run .\scripts\sync-uniffi-bindings.ps1)."
                    $parityOk = $false
                }
            }
            foreach ($k in $KotlinChecksums.Keys) {
                if (-not $ExpectedFfi.Contains($k)) {
                    Add-CheckFailure "Kotlin binding has stale checksum '$k' not found in rust-core/src/ffi/ (run .\scripts\sync-uniffi-bindings.ps1)."
                    $parityOk = $false
                }
            }
        }

        if ($parityOk) {
            Add-CheckPass "UniFFI checksum & FFI export parity verified ($($ExpectedFfi.Count) symbols, contract v$KotlinContractVersion, Kotlin <-> Swift <-> rust-core/src/ffi 100% match)."
        }
    }
}

# 5. Android native jniLibs freshness check (warn by default, fail under -StrictNative)
$JniLibsDir = Join-PathParts -Base $RepoRoot -Parts @("android-app", "app", "src", "main", "jniLibs")
$ExpectedAbis = @("arm64-v8a", "armeabi-v7a", "x86_64")

$RustInputFiles = [System.Collections.Generic.List[System.IO.FileInfo]]::new()
$RustSrcDir = Join-PathParts -Base $RepoRoot -Parts @("rust-core", "src")
if (Test-Path -LiteralPath $RustSrcDir) {
    foreach ($f in (Get-ChildItem -LiteralPath $RustSrcDir -Recurse -File)) { $RustInputFiles.Add($f) }
}
foreach ($f in $AllMapDataFiles) { $RustInputFiles.Add($f) }
foreach ($relCfgParts in @(@("rust-core", "Cargo.toml"), @("rust-core", "uniffi.toml"), @("Cargo.lock"))) {
    $cfgPath = Join-PathParts -Base $RepoRoot -Parts $relCfgParts
    if (Test-Path -LiteralPath $cfgPath -PathType Leaf) {
        $RustInputFiles.Add((Get-Item -LiteralPath $cfgPath))
    }
}

$NewestRustUtc = [datetime]::MinValue
foreach ($rf in $RustInputFiles) {
    if ($rf.LastWriteTimeUtc -gt $NewestRustUtc) {
        $NewestRustUtc = $rf.LastWriteTimeUtc
    }
}

if (-not (Test-Path -LiteralPath $JniLibsDir -PathType Container)) {
    if ($StrictNative) {
        Add-CheckFailure "Android jniLibs missing at $JniLibsDir (run .\scripts\build-android-ffi.ps1)."
    } else {
        Write-Host "[INFO] android-app/app/src/main/jniLibs not present locally (expected in CI before cargo-ndk)." -ForegroundColor DarkYellow
    }
} else {
    $MissingAbis = [System.Collections.Generic.List[string]]::new()
    $OldestSoUtc = [datetime]::MaxValue
    foreach ($abi in $ExpectedAbis) {
        $soPath = Join-Path (Join-Path $JniLibsDir $abi) "libcampus_core.so"
        if (-not (Test-Path -LiteralPath $soPath -PathType Leaf) -or (Get-Item -LiteralPath $soPath).Length -le 0) {
            $MissingAbis.Add($abi)
        } else {
            $soTime = (Get-Item -LiteralPath $soPath).LastWriteTimeUtc
            if ($soTime -lt $OldestSoUtc) {
                $OldestSoUtc = $soTime
            }
        }
    }

    if ($MissingAbis.Count -gt 0) {
        $msg = "Android jniLibs missing/empty ABI(s): $($MissingAbis -join ', '). Run .\scripts\build-android-ffi.ps1."
        if ($StrictNative) { Add-CheckFailure $msg } else { Write-Host "[WARN] $msg" -ForegroundColor Yellow }
    } elseif ($NewestRustUtc -gt $OldestSoUtc) {
        $msg = "Android jniLibs/libcampus_core.so is older than rust-core/src or map_data changes ($($OldestSoUtc.ToString('u')) < $($NewestRustUtc.ToString('u'))). Run .\scripts\build-android-ffi.ps1."
        if ($StrictNative) { Add-CheckFailure $msg } else { Write-Host "[WARN] $msg" -ForegroundColor Yellow }
    } else {
        Add-CheckPass "Android jniLibs/libcampus_core.so fresh for all $($ExpectedAbis.Count) ABIs."
    }
}

if ($Errors.Count -gt 0) {
    Write-Host "[FAIL] Map assets & UniFFI validation failed with $($Errors.Count) error(s)." -ForegroundColor Red
    exit 1
}

Write-Host "[PASS] All map assets, manifest parity, privacy, and UniFFI binding checks passed." -ForegroundColor Green
exit 0
