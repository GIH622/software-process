# PowerShell：离线检查 Java 17 语法。注意不是完整编译！
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path target/syntax | Out-Null
javac --release 17 -d target/syntax tools/SyntaxAudit.java
if ($LASTEXITCODE -ne 0) { throw '无法编译 SyntaxAudit 工具' }
java -cp target/syntax SyntaxAudit src/main/java src/test/java
if ($LASTEXITCODE -ne 0) { throw 'Java 语法解析失败' }
