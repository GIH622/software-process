# Windows PowerShell：本机 library_dev 安全启动脚本；不记录/回显密码。
# 必须先手动建库并完成 SQL；确保你在包含 pom.xml 的仓库根目录运行。
$ErrorActionPreference = 'Stop'
if (!(Test-Path 'pom.xml')) { throw '请先 cd 到项目根目录' }
$env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/library_dev?serverTimezone=Asia%2FShanghai&useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true'
$env:DB_USERNAME = Read-Host '本机 MySQL 应用用户名（如 library_app）'
if ($env:DB_USERNAME -eq 'root') { throw '安全要求：不要使用root运行Spring Boot，请先创建library_app受限账号' }
$sec = Read-Host '本机 MySQL 应用密码' -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec)
try { $env:DB_PASSWORD=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
$env:LIBRARY_ADMIN_ACCOUNT='admin'
$secA = Read-Host '自己设置的演示管理员密码' -AsSecureString
$ptrA = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secA)
try { $env:LIBRARY_ADMIN_PASSWORD=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptrA) }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptrA) }
$secR = Read-Host '演示读者统一测试密码（至少8位，仅 library_dev）' -AsSecureString
$ptrR = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secR)
try { $env:LIBRARY_DEMO_READER_PASSWORD=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptrR) }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptrR) }
$env:SPRING_PROFILES_ACTIVE='demo'
Write-Host '正在启动 library_dev 演示配置；若数据库名不是 library_dev，程序会拒绝插入演示数据。'
# 优先使用 PATH 里的 Maven；没有设置 PATH 时尝试 MAVEN_HOME 和本机已知安装目录。
$mvnCommand = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if ($mvnCommand) {
    $mvnPath = $mvnCommand.Source
} elseif ($env:MAVEN_HOME -and (Test-Path (Join-Path $env:MAVEN_HOME 'bin\mvn.cmd'))) {
    $mvnPath = Join-Path $env:MAVEN_HOME 'bin\mvn.cmd'
} elseif (Test-Path 'C:\env\apache-maven\bin\mvn.cmd') {
    $mvnPath = 'C:\env\apache-maven\bin\mvn.cmd'
} else {
    throw '找不到 mvn.cmd：请安装 Maven，配置 PATH/MAVEN_HOME，再运行本脚本。'
}
Write-Host "使用 Maven：$mvnPath"
# 本机中文路径下 mvn spring-boot:run 会报 ClassNotFoundException: LibraryApplication，
# 因此固定采用“打包 + java -jar”，与 docs/03 的实测方式保持一致。
$jar = 'target\library-process-dev-1.0.0-SNAPSHOT.jar'
& $mvnPath -B -DskipTests package
if ($LASTEXITCODE -ne 0) { throw "Maven 打包失败，退出码 $LASTEXITCODE" }
if (!(Test-Path $jar)) { throw "未找到 $jar，请确认 mvn package 是否成功" }
java -jar $jar
if ($LASTEXITCODE -ne 0) { throw "应用启动失败，退出码 $LASTEXITCODE" }
