param(
    [switch]$SkipSwiftc
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$repoRootPrefix = $repoRoot.TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
$iosRoot = Join-Path (Join-Path $repoRoot "ios-app") "CampusUFOP"

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

function Skip-SwiftStringLiteral {
    param(
        [string]$Source,
        [int]$StartIndex,
        [bool]$IsTriple,
        [int]$Hashes,
        [System.Text.StringBuilder]$Sb
    )
    $n = $Source.Length
    $i = $StartIndex
    $quotePart = if ($IsTriple) { '"""' } else { '"' }
    $hashPart = if ($Hashes -gt 0) { '#' * $Hashes } else { '' }
    $closeDelim = $quotePart + $hashPart
    $interpPrefix = '\' + $hashPart + '('

    while ($i -lt $n) {
        $ch = $Source[$i]

        # String interpolation: \(...) or \#(...)
        if (($i + $interpPrefix.Length) -le $n -and $Source.Substring($i, $interpPrefix.Length) -eq $interpPrefix) {
            [void]$Sb.Append('(')
            $i = Strip-SwiftCodeSegment -Source $Source -StartIndex ($i + $interpPrefix.Length) -StopAtCloseParen $true -Sb $Sb
            continue
        }

        # Escape sequences inside string literals
        if ($ch -eq '\') {
            if ($Hashes -eq 0) {
                if (($i + 1) -lt $n) {
                    if ($Source[$i + 1] -eq "`r" -and ($i + 2) -lt $n -and $Source[$i + 2] -eq "`n") {
                        [void]$Sb.Append("`n")
                        $i += 3
                    } else {
                        if ($Source[$i + 1] -eq "`n") {
                            [void]$Sb.Append("`n")
                        }
                        $i += 2
                    }
                    continue
                }
                $i++
                continue
            } else {
                if (($i + 1 + $Hashes) -le $n -and $Source.Substring($i + 1, $Hashes) -eq $hashPart) {
                    $afterEsc = $i + 1 + $Hashes
                    if ($afterEsc -lt $n) {
                        if ($Source[$afterEsc] -eq "`n") {
                            [void]$Sb.Append("`n")
                        }
                        $i = $afterEsc + 1
                    } else {
                        $i = $afterEsc
                    }
                    continue
                }
                $i++
                continue
            }
        }

        # Closing delimiter
        if (($i + $closeDelim.Length) -le $n -and $Source.Substring($i, $closeDelim.Length) -eq $closeDelim) {
            $i += $closeDelim.Length
            break
        }

        if ($ch -eq "`n") {
            [void]$Sb.Append("`n")
            $i++
            if (-not $IsTriple) {
                break
            }
            continue
        }

        $i++
    }

    return $i
}

function Strip-SwiftCodeSegment {
    param(
        [string]$Source,
        [int]$StartIndex,
        [bool]$StopAtCloseParen,
        [System.Text.StringBuilder]$Sb
    )
    $n = $Source.Length
    $i = $StartIndex
    $parenDepth = 0

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
                        [void]$Sb.Append("`n")
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
                $litStart = $j + $(if ($isTriple) { 3 } else { 1 })
                $i = Skip-SwiftStringLiteral -Source $Source -StartIndex $litStart -IsTriple $isTriple -Hashes $hashes -Sb $Sb
                continue
            }
        }

        # Standard triple-quoted or single-line string literal
        if ($c -eq '"') {
            $isTriple = (($i + 2) -lt $n -and $Source[$i + 1] -eq '"' -and $Source[$i + 2] -eq '"')
            $litStart = $i + $(if ($isTriple) { 3 } else { 1 })
            $i = Skip-SwiftStringLiteral -Source $Source -StartIndex $litStart -IsTriple $isTriple -Hashes 0 -Sb $Sb
            continue
        }

        if ($StopAtCloseParen) {
            if ($c -eq '(') {
                $parenDepth++
            } elseif ($c -eq ')') {
                if ($parenDepth -eq 0) {
                    [void]$Sb.Append(')')
                    return ($i + 1)
                }
                $parenDepth--
            }
        }

        if ($c -ne "`r") {
            [void]$Sb.Append($c)
        }
        $i++
    }

    return $i
}

function Strip-SwiftCommentsAndStrings([string]$Source) {
    $sb = [System.Text.StringBuilder]::new($Source.Length)
    [void](Strip-SwiftCodeSegment -Source $Source -StartIndex 0 -StopAtCloseParen $false -Sb $sb)
    return $sb.ToString()
}

function Test-SwiftDelimitersBalanced {
    param(
        [string]$SanitizedCode,
        [string]$RelPath,
        [System.Collections.Generic.List[string]]$Violations
    )
    $stack = [System.Collections.Generic.List[hashtable]]::new()
    $lineNo = 1

    for ($idx = 0; $idx -lt $SanitizedCode.Length; $idx++) {
        $ch = $SanitizedCode[$idx]
        if ($ch -eq "`n") {
            $lineNo++
            continue
        }

        if ($ch -eq '{' -or $ch -eq '(' -or $ch -eq '[') {
            $expected = switch ($ch) {
                '{' { '}' }
                '(' { ')' }
                '[' { ']' }
            }
            $stack.Add(@{
                Char     = $ch
                Expected = $expected
                Line     = $lineNo
            })
        } elseif ($ch -eq '}' -or $ch -eq ')' -or $ch -eq ']') {
            if ($stack.Count -eq 0) {
                $Violations.Add("${RelPath}:${lineNo}: Unmatched closing '$ch' (delimiter stack underflow)")
                continue
            }

            $top = $stack[$stack.Count - 1]
            if ($top.Expected -eq $ch) {
                $stack.RemoveAt($stack.Count - 1)
                continue
            }

            # Look down the stack for a matching opener to report unclosed inner delimiters accurately
            $matchIdx = -1
            for ($k = $stack.Count - 2; $k -ge 0; $k--) {
                if ($stack[$k].Expected -eq $ch) {
                    $matchIdx = $k
                    break
                }
            }

            if ($matchIdx -ge 0) {
                while ($stack.Count - 1 -gt $matchIdx) {
                    $unclosed = $stack[$stack.Count - 1]
                    $stack.RemoveAt($stack.Count - 1)
                    $Violations.Add("${RelPath}:${lineNo}: Delimiter mismatch — expected '$($unclosed.Expected)' for '$($unclosed.Char)' opened at line $($unclosed.Line), found '$ch'")
                }
                $stack.RemoveAt($stack.Count - 1)
            } else {
                $Violations.Add("${RelPath}:${lineNo}: Delimiter mismatch — expected '$($top.Expected)' for '$($top.Char)' opened at line $($top.Line), found '$ch'")
            }
        }
    }

    for ($k = $stack.Count - 1; $k -ge 0; $k--) {
        $rem = $stack[$k]
        $Violations.Add("${RelPath}:$($rem.Line): Unclosed '$($rem.Char)' (expected '$($rem.Expected)' before end of file)")
    }
}

$kotlinRules = @(
    @{ Name = "Kotlin 'null'";         Pattern = '\bnull\b' }
    @{ Name = "Kotlin 'listOf('";      Pattern = '\blistOf\s*\(' }
    @{ Name = "Kotlin 'emptySet('";    Pattern = '\bemptySet\s*\(' }
    @{ Name = "Kotlin 'mutableSetOf('"; Pattern = '\bmutableSetOf\s*\(' }
    @{ Name = "Kotlin 'val' decl";     Pattern = '\bval\s+[A-Za-z_][A-Za-z0-9_]*\s*[:=]' }
    @{ Name = "Kotlin 'Boolean'";      Pattern = '\bBoolean\b' }
    @{ Name = "Kotlin 'Unit'";         Pattern = '\bUnit\b' }
)

$violations = [System.Collections.Generic.List[string]]::new()
$swiftc = if (-not $SkipSwiftc) { Get-Command swiftc -ErrorAction SilentlyContinue } else { $null }

$coreMapCount = 0
$featuresMapCount = 0

foreach ($file in $swiftFiles) {
    $relPath = $file.FullName.Substring($repoRootPrefix.Length).Replace('\', '/')
    if ($relPath -match '/Core/Map/[^/]+\.swift$') { $coreMapCount++ }
    if ($relPath -match '/Features/Map/[^/]+\.swift$') { $featuresMapCount++ }

    $raw = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
    $sanitized = Strip-SwiftCommentsAndStrings $raw

    Test-SwiftDelimitersBalanced -SanitizedCode $sanitized -RelPath $relPath -Violations $violations

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

if ($coreMapCount -le 0) {
    $violations.Add("Expected > 0 Swift files scanned under ios-app/CampusUFOP/Core/Map/, found $coreMapCount.")
}
if ($featuresMapCount -le 0) {
    $violations.Add("Expected > 0 Swift files scanned under ios-app/CampusUFOP/Features/Map/, found $featuresMapCount.")
}

Write-Host "Scanned $($swiftFiles.Count) Swift files under ios-app/CampusUFOP (excluding CoreFFI/Generated/; Core/Map=$coreMapCount, Features/Map=$featuresMapCount)."
if ($null -eq $swiftc) {
    Write-Host "[INFO] swiftc not found in PATH; executed lexical Swift hygiene + delimiter stack checks ({}, (), [])."
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
