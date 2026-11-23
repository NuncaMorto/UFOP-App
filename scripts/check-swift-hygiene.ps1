param(
    [switch]$SkipSwiftc
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$iosRoot = Join-Path $repoRoot "ios-app\CampusUFOP"

if (-not (Test-Path -LiteralPath $iosRoot)) {
    Write-Error "iOS source directory not found: $iosRoot"
    exit 1
}

$swiftFiles = Get-ChildItem -Path $iosRoot -Filter "*.swift" -Recurse -File |
    Where-Object {
        $norm = $_.FullName.Replace('\', '/')
        $norm -notmatch '/CoreFFI/Generated/'
    } |
    Sort-Object FullName

if ($swiftFiles.Count -eq 0) {
    Write-Error "No Swift source files found under $iosRoot"
    exit 1
}

function Strip-SwiftCommentsAndStrings([string]$Source) {
    $sb = [System.Text.StringBuilder]::new($Source.Length)
    $n = $Source.Length
    $i = 0

    while ($i -lt $n) {
        $c = $Source[$i]

        # Line comment // ...
        if ($c -eq '/' -and ($i + 1) -lt $n -and $Source[$i + 1] -eq '/') {
            $i += 2
            while ($i -lt $n -and $Source[$i] -ne "`n") {
                $i++
            }
            continue
        }

        # Nested block comment /* ... */
        if ($c -eq '/' -and ($i + 1) -lt $n -and $Source[$i + 1] -eq '*') {
            $depth = 1
            $i += 2
            while ($i -lt $n -and $depth -gt 0) {
                if ($Source[$i] -eq '/' -and ($i + 1) -lt $n -and $Source[$i + 1] -eq '*') {
                    $depth++
                    $i += 2
                } elseif ($Source[$i] -eq '*' -and ($i + 1) -lt $n -and $Source[$i + 1] -eq '/') {
                    $depth--
                    $i += 2
                } else {
                    if ($Source[$i] -eq "`n") {
                        [void]$sb.Append("`n")
                    }
                    $i++
                }
            }
            continue
        }

        # Raw strings #"..."# / #"""..."""#
        if ($c -eq '#') {
            $hashes = 0
            $j = $i
            while ($j -lt $n -and $Source[$j] -eq '#') {
                $hashes++
                $j++
            }
            if ($j -lt $n -and $Source[$j] -eq '"') {
                $isTriple = (($j + 2) -lt $n -and $Source[$j + 1] -eq '"' -and $Source[$j + 2] -eq '"')
                $closeDelim = $(if ($isTriple) { '"""' } else { '"' }) + ('#' * $hashes)
                $i = $j + $(if ($isTriple) { 3 } else { 1 })
                while ($i -lt $n) {
                    if ($Source[$i] -eq "`n") {
                        [void]$sb.Append("`n")
                    }
                    if (($i + $closeDelim.Length) -le $n -and $Source.Substring($i, $closeDelim.Length) -eq $closeDelim) {
                        $i += $closeDelim.Length
                        break
                    }
                    $i++
                }
                continue
            }
        }

        # Triple-quoted multi-line string literal """..."""
        if ($c -eq '"' -and ($i + 2) -lt $n -and $Source[$i + 1] -eq '"' -and $Source[$i + 2] -eq '"') {
            $i += 3
            while ($i -lt $n) {
                if ($Source[$i] -eq '\' -and ($i + 1) -lt $n) {
                    if ($Source[$i + 1] -eq "`n") { [void]$sb.Append("`n") }
                    $i += 2
                    continue
                }
                if ($Source[$i] -eq '"' -and ($i + 2) -lt $n -and $Source[$i + 1] -eq '"' -and $Source[$i + 2] -eq '"') {
                    $i += 3
                    break
                }
                if ($Source[$i] -eq "`n") {
                    [void]$sb.Append("`n")
                }
                $i++
            }
            continue
        }

        # Single-line string literal "..."
        if ($c -eq '"') {
            $i++
            while ($i -lt $n) {
                if ($Source[$i] -eq '\' -and ($i + 1) -lt $n) {
                    $i += 2
                    continue
                }
                if ($Source[$i] -eq '"' -or $Source[$i] -eq "`n") {
                    if ($Source[$i] -eq "`n") {
                        [void]$sb.Append("`n")
                    }
                    $i++
                    break
                }
                $i++
            }
            continue
        }

        [void]$sb.Append($c)
        $i++
    }

    return $sb.ToString()
}

$kotlinRules = @(
    @{ Name = "Kotlin 'null'";        Pattern = '\bnull\b' }
    @{ Name = "Kotlin 'listOf('";     Pattern = '\blistOf\s*\(' }
    @{ Name = "Kotlin 'emptySet('";   Pattern = '\bemptySet\s*\(' }
    @{ Name = "Kotlin 'mutableSetOf('"; Pattern = '\bmutableSetOf\s*\(' }
    @{ Name = "Kotlin 'val' decl";    Pattern = '\bval\s+[A-Za-z_][A-Za-z0-9_]*\s*[:=]' }
    @{ Name = "Kotlin 'Boolean'";     Pattern = '\bBoolean\b' }
    @{ Name = "Kotlin 'Unit'";        Pattern = '\bUnit\b' }
)

$violations = [System.Collections.Generic.List[string]]::new()
$swiftc = if (-not $SkipSwiftc) { Get-Command swiftc -ErrorAction SilentlyContinue } else { $null }

foreach ($file in $swiftFiles) {
    $relPath = $file.FullName.Substring($repoRoot.Length + 1).Replace('\', '/')
    $raw = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
    $sanitized = Strip-SwiftCommentsAndStrings $raw

    # Check brace balance on sanitized code
    $depth = 0
    $underflowLine = -1
    $lineNo = 1
    for ($idx = 0; $idx -lt $sanitized.Length; $idx++) {
        $ch = $sanitized[$idx]
        if ($ch -eq "`n") {
            $lineNo++
        } elseif ($ch -eq '{') {
            $depth++
        } elseif ($ch -eq '}') {
            $depth--
            if ($depth -lt 0 -and $underflowLine -lt 0) {
                $underflowLine = $lineNo
            }
        }
    }

    if ($underflowLine -gt 0 -or $depth -ne 0) {
        $violations.Add("${relPath}: Unbalanced curly braces (final depth=$depth, first underflow line=$underflowLine)")
    }

    # Check line-by-line for Kotlinisms
    $lines = $sanitized -split "`n"
    for ($ln = 0; $ln -lt $lines.Length; $ln++) {
        $lineText = $lines[$ln]
        if ([string]::IsNullOrWhiteSpace($lineText)) { continue }
        foreach ($rule in $kotlinRules) {
            if ($lineText -match $rule.Pattern) {
                $violations.Add("${relPath}:$($ln + 1): Detected $($rule.Name) -> $($lineText.Trim())")
            }
        }
    }

    if ($null -ne $swiftc) {
        & $swiftc.Source -parse $file.FullName 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) {
            $violations.Add("${relPath}: swiftc -parse exited with code $LASTEXITCODE")
        }
    }
}

Write-Host "Scanned $($swiftFiles.Count) Swift files under ios-app/CampusUFOP (excluding CoreFFI/Generated/)."
if ($null -eq $swiftc) {
    Write-Host "[INFO] swiftc not found in PATH; executed lexical Swift hygiene + brace checks."
} else {
    Write-Host "[INFO] Verified native syntax via swiftc -parse."
}

if ($violations.Count -gt 0) {
    foreach ($v in $violations) {
        Write-Host "[FAIL] $v" -ForegroundColor Red
    }
    Write-Error "Swift hygiene check failed with $($violations.Count) issue(s)."
    exit 1
}

Write-Host "[PASS] Swift hygiene check passed with 0 issues." -ForegroundColor Green
exit 0
