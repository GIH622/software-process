# 不触碰数据库的工具/端口预检；适合 Windows PowerShell 5+/7。
$ErrorActionPreference='Continue'
foreach($exe in @('java','javac','mvn','mysql')){
  $tool=Get-Command $exe -ErrorAction SilentlyContinue
  if($null -eq $tool){ Write-Host "[缺失] $exe - 请安装并加入 PATH" }
  else { Write-Host "[找到] $exe : $($tool.Source)" }
}
Write-Host '[本机3306端口检查]'
Test-NetConnection 127.0.0.1 -Port 3306 | Select-Object ComputerName,RemotePort,TcpTestSucceeded
Write-Host '[注意] 端口连通不等于密码正确/SQL结构正确。不要在此输出密码。'

if (!(Get-Command mvn -ErrorAction SilentlyContinue)) {
  $candidate = 'C:\env\apache-maven\bin\mvn.cmd'
  if (Test-Path $candidate) {
    Write-Host "[提示] Maven 实际已安装但终端 PATH 未识别；可使用：& `"$candidate`" -version"
  }
}
