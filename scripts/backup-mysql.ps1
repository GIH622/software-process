# 备份现有数据：使用交互密码，避免把密码硬编码到命令行/PowerShell历史。
$database=Read-Host '要备份的数据库名称'
$hostname=Read-Host 'MySQL host（本机为127.0.0.1）'
$username=Read-Host 'MySQL 用户名'
New-Item -ItemType Directory -Force -Path backups | Out-Null
$path="backups/$database-$(Get-Date -Format yyyyMMdd-HHmmss).sql"
Write-Host 'mysqldump 将提示输入密码。请保留备份在未上传GitHub的安全位置。'
# 使用 mysqldump 原生 --result-file，避免 PowerShell 的 UTF-8 BOM/重定向破坏备份；--single-transaction 适用于 InnoDB。
& mysqldump --host=$hostname --user=$username --password --single-transaction --routines --triggers --default-character-set=utf8mb4 --result-file=$path $database
if($LASTEXITCODE -ne 0){Write-Warning 'mysqldump 未成功完成；请删除不完整备份并排查。';exit 1}
Write-Host "数据库备份完成：$path（该目录已在 .gitignore 中）"
