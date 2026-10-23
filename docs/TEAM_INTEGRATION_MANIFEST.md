# v4 整合差异清单（未执行 Git 合并）
- 以 `software-process.zip` 的 12 个工作区文件为准，没有覆盖、重命名或删除队友文件。
- 新建 Maven `pom.xml`，Spring Boot `src/main`，JUnit `src/test`，数据库安全脚本、Postman、页面和开发文档。
- 本次有意不加入旧 v3 生成的 Word/Excel，因为团队根目录的四阶段正式文件应当是唯一来源。
- 不复制 `.git`：历史应由真实克隆仓库保留；整合包也不代表已真实 push/merge。
- 原始数据库 `library_db.sql` 在团队项目根目录已经存在且被 Git 跟踪；本次没有对它做二次复制或任何改动。

## 对 v3.1.1 源码的明确增量
| 文件 | 修改 |
|---|---|
| `config/LibrarySettings.java` | 冻结阈值默认 30→35；续借延长默认 7→15 |
| `application.yml` | 采用相同配置，补充 SRS 和 CR-001 待确认注释 |
| `application-test.yml` | 35/15 测试基准 |
| `BorrowServiceIntegrationTest.java` | 超35冻结改36天；新增35天 RESTRICTED、归还后管理员解冻测试 |
| `RenewalServiceIntegrationTest.java` | 成功续借从+7天改为+15天 |
| `scripts/start-local.ps1` | 兼容未加入PATH的 Windows Maven 安装 |
| `proposals/CR-001-续借候选方案/说明.md` | 修正旧版过时 API `/api/renew` 与 404 说明 |
| `docs/*.md` | 测试数量、规则口径、续借行为和需求差异同步更新 |

## 当前状态（不要写成已验收）
- JUnit 测试方法 36 个，是**已编写**而不是**已运行通过**。
- Java 17 语法解析、JSON/JS 语法与 ZIP 完整性可做离线检查；
  当前没有 Maven 完整依赖及 MySQL/Postman 真实服务连接结果。
- R003 默认关闭；MySQL 并发/锁测试仍必须在真实 `library_dev` 中执行。

## 提交组织建议
一个可编译、可审核的 R001/R002 核心项目作为完整 PR，不因“需要分批”而把同一 Service 拆成无法编译的半成品；
每阶段通过真实 Commit 留下分工和修改原因，之后由组长按功能拆合 PR。
