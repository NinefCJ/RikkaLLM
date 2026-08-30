$root = "e:/Code/RikkaLLM"
$appRoot = Join-Path $root "app"
$skipBuild = '\\build\\'
$validExt = @('.kt','.kts','.xml','.json','.pro','.properties','.gradle','.txt','.md','.conf','.yaml','.yml')

# 1) Move Room schema directory to the new package path
$oldSchema = Join-Path $appRoot "schemas\me.rerere.rikkahub.data.db.AppDatabase"
$newSchema = Join-Path $appRoot "schemas\com.ninef.rikkallm.data.db.AppDatabase"
if (Test-Path $oldSchema) {
    Move-Item -Path $oldSchema -Destination $newSchema -Force
    Write-Host "Moved schema dir -> $newSchema"
}

# 2) Collect candidate files (app tree minus build/, plus root-level files)
$paths = @()
$paths += Get-ChildItem -Path $appRoot -Recurse -File -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -notmatch $skipBuild } |
    Select-Object -ExpandProperty FullName
$paths += Get-ChildItem -Path $root -File -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty FullName
$paths = $paths | Sort-Object -Unique

$count = 0
foreach ($p in $paths) {
    if ($validExt -notcontains [System.IO.Path]::GetExtension($p)) { continue }
    try {
        $c = [System.IO.File]::ReadAllText($p)
    } catch {
        continue
    }
    if ($c.Contains('me.rerere.rikkahub')) {
        $c = $c.Replace('me.rerere.rikkahub', 'com.ninef.rikkallm')
        [System.IO.File]::WriteAllText($p, $c)
        $count++
    }
}
Write-Host "Replaced package in $count files"
