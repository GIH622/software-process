# Windows 本机联调与 Postman 执行手册

> 执行日期：2026-10-22（Asia/Shanghai）
> 适用：开发自测、QA 接口验证、交接后复现
> 原则：只在独立开发库 `library_dev` 做写操作；不要对队友的 `library_db` 执行建表、重置或批量写测试。

## 1. 实测环境

| 项目 | 实测值 | 说明 |
|---|---|---|
| 操作系统 | Windows 11 | — |
| JDK | `C:\Users\袁ASUS\.jdks\ms-17.0.19`（OpenJDK 17.0.19） | **必须用 17**；本机默认 PATH 是 JDK 24，会导致 Mockito 无法 self-attach，37 个测试全部报错 |
| Maven | 3.10.0（`C:\env\apache-maven`） | 已在 PATH |
| 数据库 | 本机 MySQL 8.0.45，`127.0.0.1:3306`，独立库 `library_dev`，受限账号 `library_app` | 已实测通过；不要使用 root 直接跑应用 |
| Node / newman | Node 24.15.0 / newman 6.2.3（`npx newman`） | 用于无人值守执行 Postman 集合 |
| 应用端口 | 8080 | `application.yml` 默认值 |

## 2. 创建开发库与受限账号

以下命令在已安装 MySQL 客户端的前提下执行。**不要在 3306 上用 root 跑应用**。

```powershell
$MYSQL="C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
$env:MYSQL_PWD='<root 口令>'
& $MYSQL -u root --host=127.0.0.1 --port=3306 -e "
CREATE DATABASE IF NOT EXISTS library_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER IF NOT EXISTS 'library_app'@'localhost' IDENTIFIED BY '<应用口令>';
CREATE USER IF NOT EXISTS 'library_app'@'127.0.0.1' IDENTIFIED BY '<应用口令>';
GRANT ALL PRIVILEGES ON library_dev.* TO 'library_app'@'localhost';
GRANT ALL PRIVILEGES ON library_dev.* TO 'library_app'@'127.0.0.1';
FLUSH PRIVILEGES;"
```

应用账号只需要 `library_dev` 权限，不要授予全局 DDL 权限。

## 3. 建表（顺序不可颠倒）

```powershell
$MYSQL="C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
$env:MYSQL_PWD='<应用口令>'
# 必须在项目的 sql 目录下执行：MySQL 8 客户端对中文绝对路径 SOURCE 会报 Failed to open file
& $MYSQL -u library_app --host=127.0.0.1 --port=3306 `
  --default-character-set=utf8mb4 --database=library_dev `
  --execute="SOURCE library_init.sql; SOURCE requirement_change_log.sql; SHOW TABLES;"
```

期望 5 张表：`reader_account`、`book_inventory`、`borrow_record`、`operation_log`、`requirement_change_log`。

**不要执行** `sql/library_db.sql`：旧库已于 2026-10-23 退役，该 Navicat 快照含 `DROP TABLE`，且已停止 Git 跟踪。旧结构存量库（口令列 `password_md5`）改跑 `sql/10_retire_legacy_migration.sql` 一次性迁移到 `password_hash`。

## 4. 编译与自动化测试

```powershell
$env:JAVA_HOME='C:\Users\袁ASUS\.jdks\ms-17.0.19'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -B clean test
```

实测结果：`Tests run: 38, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。

## 5. 启动应用（demo 配置）

`demo` Profile 会校验当前库名必须是 `library_dev`，然后补建演示读者 1001~1010 和样例图书；不会动其他库。

```powershell
$env:JAVA_HOME='C:\Users\袁ASUS\.jdks\ms-17.0.19'
$env:DB_URL='jdbc:mysql://127.0.0.1:3306/library_dev?serverTimezone=Asia%2FShanghai&useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true'
$env:DB_USERNAME='library_app'
$env:DB_PASSWORD='<应用口令>'
$env:LIBRARY_ADMIN_ACCOUNT='admin'
$env:LIBRARY_ADMIN_PASSWORD='<本机管理员口令>'
$env:LIBRARY_DEMO_READER_PASSWORD='<演示读者口令，至少8位>'
$env:SPRING_PROFILES_ACTIVE='demo'
mvn -B -DskipTests package
java -jar target\library-process-dev-1.0.0-SNAPSHOT.jar
```

健康检查：`http://127.0.0.1:8080/api/health` 返回 `{"code":200,...,"service":"UP"}`。
健康检查只代表 HTTP 存活，不代表数据库事务已验收。

也可使用交互式脚本 `scripts\start-local.ps1`，它固定连 3306，并已改为“`mvn package` + `java -jar`”启动（与本节一致）。

## 6. Postman 集合执行

集合：`postman/Library_Local_Integration.postman_collection.json`
环境模板：`postman/Library_Local_Template.postman_environment.json`

集合已按执行条件分成两个文件夹：

1. **主流程（自动执行，00-12）**：健康 → 读者登录 → 检索 → 借阅 → 我的记录 → 归还 → 重复归还 409 → 管理员登录 → 逾期名单 → 日志 → 新增图书 → R003 默认关闭 409 → CSV 导出。
2. **R003 变更回归（默认关闭，需迁移+启用后单独执行）**：自带“读者登录 → 借 `BC-0003` → 保存 `renewRecordId` → 续借”链路，不依赖主流程中已归还的 `recordId`；只有 CR-001 审批、执行 `sql/20_CR001_renewal_optional.sql` 并设置 `LIBRARY_RENEWAL_ENABLED=true` 后才运行。

命令行执行（环境文件放在仓库外，避免提交真实口令）：

```powershell
npx newman run "postman\Library_Local_Integration.postman_collection.json" `
  -e "$env:TEMP\library-env.json" `
  --folder "主流程（自动执行，00-12）" `
  --reporters cli,json --reporter-json-export "$env:TEMP\newman-main.json"
```

实测结果：2026-10-22 跑出 13 请求 / 16 断言全部通过（重复入库 409 分支，`newman-main-mysql8-20261022-postfix.json`）；2026-10-23 旧库退役、清库重建后复跑 13 请求 / 15 断言全部通过（首次入库 200 分支，`newman-main-mysql8-20261023-retire.json`）。两份证据合计覆盖 200/409 两条入库分支。

### 复跑注意

- 读者 1001 当天已借过某 ISBN 后，再借**同 ISBN** 的另一副本会被 `DUPLICATE_BORROW` 拒绝（409），这是现有业务规则，不是接口故障。复跑时换一本不同 ISBN 的副本（如 `BC-0003`），或清空本地开发库后重来。
- 第 10 步“管理员新书入库”只应执行一次，复跑请修改环境变量 `newBookBarcode`，否则唯一条码会冲突。
- 管理员登录会覆盖客户端会话；此后的写请求必须使用登录响应里的**管理员** `csrfToken`。原集合第 11 步误用读者 token，已修正为 `{{adminCsrfToken}}`。

## 7. 数据库一致性与并发验收

一致性检查（4 类不一致查询应全部 0 行，最后一条样例查询会返回 3 行）：

```powershell
$MYSQL="C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
$env:MYSQL_PWD='<应用口令>'
# 在项目的 sql 目录下执行
& $MYSQL -u library_app --host=127.0.0.1 --port=3306 --default-character-set=utf8mb4 `
  --database=library_dev --table --execute="SOURCE 04_verify_consistency.sql"
```

10 线程同副本争抢（需先确认 `BC-CONC-001` 为 `AVAILABLE`）：

```powershell
$env:BASE_URL='http://127.0.0.1:8080'
$env:LIBRARY_DEMO_READER_PASSWORD='<演示读者口令>'
python scripts\mysql-concurrency-test.py
```

实测结果：`success=1, expected=1`，其余 9 个请求均为 `409 BOOK_UNAVAILABLE`；测试后已归还该书，避免留下脏数据。

## 8. 切换到其他 MySQL 实例（可选）

当前配置与实测均指向本机 3306 MySQL 8.0.45。如需换库：

1. 在目标实例创建 `library_dev` 和 `library_app`，执行第 3 节建表脚本。
2. 修改 `DB_URL` 的主机/端口，重启应用。
3. 重新执行第 6、7 节，替换证据文件。
4. 不要直接用 root 运行应用；root 只用于建库授权。

## 9. 常见故障

| 现象 | 原因 | 处理 |
|---|---|---|
| 36 个测试全部 `Could not initialize plugin: interface org.mockito.plugins.MockMaker` | 用 JDK 24 运行，Mockito/ByteBuddy 无法动态挂载 | 设置 `JAVA_HOME` 到 JDK 17 后重跑 |
| `mvn spring-boot:run` 报 `ClassNotFoundException: LibraryApplication` | 中文路径下 forked classpath 解析异常（本机实测） | 改用 `mvn -DskipTests package` + `java -jar target\...jar` |
| MySQL 8 客户端 `SOURCE 中文绝对路径` 报 `Failed to open file, error: 2` | 客户端对非 ASCII 路径处理异常 | 进入 `sql` 目录后用相对路径 `SOURCE library_init.sql` |
| 登录返回 401 | 演示读者 1001~1010 尚未由 demo 初始化，或口令不对 | 确认 `SPRING_PROFILES_ACTIVE=demo`、`LIBRARY_DEMO_READER_PASSWORD` 一致 |
| 借阅返回 409 `DUPLICATE_BORROW` | 同一读者当天已借过同 ISBN（含已归还） | 换不同 ISBN 的副本，或等次日/清空本地开发库 |
| 写请求返回 403 `INVALID_CSRF` | 会话被管理员登录覆盖，仍用旧 token | 使用最新登录响应的 `csrfToken`/`adminCsrfToken` |
| 应用启动报“只允许连接 library_dev” | 连到了 `library_db` 或其他库 | 修改 `DB_URL`，demo 模式禁止污染其他库 |
