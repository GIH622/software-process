# 安全只读预检。需本机安装 mysql 命令行；连接本地或同学服务器时按实际地址输入。
$hostName=Read-Host 'MySQL 主机IP或主机名（本人电脑用127.0.0.1）'
$dbName=Read-Host '已存在的数据库名（队友指南写library_db）'
$userName=Read-Host '只读MySQL用户名（不要用root远程暴露）'
Write-Host '下一个 MySQL -p 提示会安全交互输入密码；本脚本只执行 information_schema 查询。'
mysql --host=$hostName --user=$userName --password --database=$dbName --table --execute="SELECT DATABASE() AS db, CURRENT_USER() AS account, VERSION() AS server_version; SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME,ORDINAL_POSITION;"
