# Temporary migration: replace hardcoded dp spacing with Spacing/Radius design tokens.
# Safe for compilation because Spacing/Radius are Dp values; only import management matters.
$ErrorActionPreference = 'Stop'

$spacingMap = @{
    2  = 'Spacing.xxs'
    4  = 'Spacing.xs'
    8  = 'Spacing.sm'
    12 = 'Spacing.md'
    16 = 'Spacing.lg'
    24 = 'Spacing.xl'
    32 = 'Spacing.xxl'
}
$radiusMap = @{
    2  = 'Radius.sm'
    4  = 'Radius.sm'
    8  = 'Radius.sm'
    12 = 'Radius.md'
    16 = 'Radius.lg'
    24 = 'Radius.xl'
    32 = 'Radius.xl'
}

$roots = @(
    'app/src/main/java/me/rerere/rikkahub/ui/pages',
    'app/src/main/java/me/rerere/rikkahub/ui/components'
)

$spacingRe = [regex]'(?i)((?:RoundedCornerShape|RoundCornerShape|CutCornerShape)\s*\(\s*)(\d+)\.dp'
$cornerRe  = [regex]'cornerRadius\s*=\s*(\d+)\.dp'
$dpRe      = [regex]'(?<![.\w])(\d+)\.dp'

$count = 0
foreach ($root in $roots) {
    $files = Get-ChildItem -Path $root -Recurse -Filter *.kt
    foreach ($f in $files) {
        $text = [System.IO.File]::ReadAllText($f.FullName)
        $orig = $text

        # 1) shape constructors -> Radius
        $text = $spacingRe.Replace($text, {
            param($m)
            $num = [int]$m.Groups[2].Value
            if ($radiusMap.ContainsKey($num)) { $m.Groups[1].Value + $radiusMap[$num] } else { $m.Value }
        })
        # 2) cornerRadius = N.dp -> Radius
        $text = $cornerRe.Replace($text, {
            param($m)
            $num = [int]$m.Groups[1].Value
            if ($radiusMap.ContainsKey($num)) { "cornerRadius = " + $radiusMap[$num] } else { $m.Value }
        })
        # 3) remaining standalone N.dp -> Spacing
        $text = $dpRe.Replace($text, {
            param($m)
            $num = [int]$m.Groups[1].Value
            if ($spacingMap.ContainsKey($num)) { $spacingMap[$num] } else { $m.Value }
        })

        $needSpacing = $text -match 'Spacing\.'
        $needRadius  = $text -match 'Radius\.'

        if ($needSpacing -and ($text -notmatch 'me\.rerere\.rikkahub\.ui\.theme\.Spacing')) {
            $text = [regex]::Replace($text, '^(package .*)$', '$1' + [Environment]::NewLine + 'import me.rerere.rikkahub.ui.theme.Spacing', [System.Text.RegularExpressions.RegexOptions]::Multiline)
        }
        if ($needRadius -and ($text -notmatch 'me\.rerere\.rikkahub\.ui\.theme\.Radius')) {
            $text = [regex]::Replace($text, '^(package .*)$', '$1' + [Environment]::NewLine + 'import me.rerere.rikkahub.ui.theme.Radius', [System.Text.RegularExpressions.RegexOptions]::Multiline)
        }

        if ($text -ne $orig) {
            [System.IO.File]::WriteAllText($f.FullName, $text)
            $count++
            Write-Host "Migrated: $($f.FullName)"
        }
    }
}
Write-Host "Total migrated files: $count"
