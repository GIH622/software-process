# 开发增量 ZIP 的安全合入脚本；默认只预览，只有添加 -Apply 才实际复制。
# 在本地 Git 克隆仓库内运行前请处理已有未提交变更，避免混入队友正在修改的文件。
param(
    [Parameter(Mandatory=$true)][string]$RepoPath,
    [Parameter(Mandatory=$true)][string]$OverlayPath,
    [switch]$Apply
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath $RepoPath).Path.TrimEnd('\')
$overlay = (Resolve-Path -LiteralPath $OverlayPath).Path.TrimEnd('\')
if (!(Test-Path (Join-Path $repo '.git'))) {
    throw "目标 $repo 不是已有 Git 克隆目录（缺少 .git），已停止。"
}
if ($repo -eq $overlay) { throw '增量包目录与仓库目录不可相同。' }
if ($overlay.StartsWith($repo + '\', [System.StringComparison]::OrdinalIgnoreCase)) {
    throw '请将增量包解压到仓库之外，再执行本脚本。'
}
$files = @(Get-ChildItem -LiteralPath $overlay -File -Recurse)
$conflicts = @()
foreach ($file in $files) {
    $relative = $file.FullName.Substring($overlay.Length).TrimStart([char[]]@('\','/'))
    if ($relative -match '(^|[\\/])\.git([\\/]|$)') {
        throw '增量中发现 .git，已停止。'
    }
    $destination = Join-Path $repo $relative
    if (Test-Path -LiteralPath $destination) { $conflicts += $relative }
}
Write-Host "待新增文件 $($files.Count) 个；与仓库已有路径冲突 $($conflicts.Count) 个。"
if ($conflicts.Count -gt 0) {
    $conflicts | ForEach-Object { Write-Host "[冲突，未复制] $_" }
    throw '存在同名路径，请先比较差异。不会覆盖队友文件。'
}
if (!$Apply) {
    Write-Host '预览模式：未复制文件。确认无冲突、团队仓库没有未提交修改后，添加 -Apply 再运行。'
    exit 0
}
if (!(Get-Command git -ErrorAction SilentlyContinue)) { throw '缺少 Git 命令。' }
$status = @(& git -C $repo status --porcelain)
if ($LASTEXITCODE -ne 0) { throw 'Git 状态检查失败。' }
if ($status.Count -gt 0) {
    $status | ForEach-Object { Write-Host $_ }
    throw 'Git 工作区有未提交变更：请先联系实际修改人处理，禁止自动覆盖。'
}
foreach ($file in $files) {
    $relative = $file.FullName.Substring($overlay.Length).TrimStart([char[]]@('\','/'))
    $destination = Join-Path $repo $relative
    $parent = Split-Path $destination -Parent
    New-Item -ItemType Directory -Force -Path $parent | Out-Null
    Copy-Item -LiteralPath $file.FullName -Destination $destination
}
Write-Host "只新增了 $($files.Count) 个文件，没有修改任何既有文件。请先 git status、执行 mvn clean test，再自行分批提交。"
