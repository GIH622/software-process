# 📚 图书借阅归还登记系统

> **软件过程课程实践项目｜四人协作开发｜需求可追溯 · 事务一致性 · 规范化交付**

本项目面向高校图书馆常见的图书借阅与归还登记场景，设计并实现一套轻量级的 Web 图书管理系统。系统以**读者、馆藏图书副本和借阅记录**为核心，支持图书检索、借阅、归还、逾期管理以及管理员基础维护功能，目标是减少人工登记错误，让图书状态与借阅记录保持一致。

与单纯实现功能的课程作业不同，本项目同时服务于《软件过程》实践中的**需求管理、规格说明与评审、需求—代码—测试追踪、需求变更控制和 GitHub 团队协作**。小组通过分支开发、代码审核、测试验证和版本基线管理，使每一项功能都有对应需求依据和可检查的交付过程。

## ✨ 项目目标

- **借还业务电子化**：支持基于读者证号、图书条码的登记和查询。
- **数据一致性**：借阅与归还涉及的多张表必须在同一事务中更新，失败时整体回滚。
- **并发可靠性**：通过 MySQL InnoDB 事务、行级锁及必要的数据约束，避免同一副本被重复借出。
- **逾期可管理**：记录应还日期、实际归还日期及逾期天数，并根据规则限制借阅。
- **过程可追溯**：把 R001/R002 等需求与代码、测试用例、Commit 和变更记录关联起来。

## 🧩 功能范围

| 模块 | 主要功能 | 当前说明 |
| --- | --- | --- |
| **R001 图书借阅** | 验证读者状态和借阅上限，检查副本是否可借，创建借阅记录并更新馆藏状态 | 核心业务 |
| **R002 图书归还** | 检查有效借阅记录，登记实际归还时间、计算逾期天数、恢复图书可借状态 | 核心业务 |
| 图书及记录查询 | 按书名、ISBN 等检索馆藏，查询当前用户的借阅记录 | 配套功能 |
| 逾期处理 | 按逾期天数限制借阅、处理冻结状态及归还后的状态重新判断 | 规则仍需最终评审 |
| 读者与馆藏管理 | 管理员新增读者/图书、冻结或解冻读者、注销读者、下架图书 | 管理员功能 |
| 操作记录与导出 | 查询操作日志、逾期信息、导出借阅记录 CSV | 管理与审计 |
| **R003 图书续借** | 候选方案为最多续借 1 次、延长 15 天 | **需求变更功能，默认关闭；迁移与验收后才启用** |

**当前业务口径与待决项：** 默认借期 30 天；逾期**超过 35 天（第 36 天起）**进入 `FROZEN`，管理员可在管理台点击“解冻”按钮解除，解冻时会按剩余未还逾期重新计算状态。普通逾期图书归还后，如没有其他未归还逾期记录，可恢复正常。旧版 SRS 中“超过 30 天”的写法需由需求分析师修订。本期**不实现逾期罚款或在线支付**。

## 🛠 技术栈

| 分类 | 技术 | 用途 |
| --- | --- | --- |
| 后端语言 | **Java 17** | 业务开发及运行基线 |
| Web 框架 | **Spring Boot 3.3.5** | HTTP API、参数校验与应用管理 |
| 数据访问 | **Spring JDBC（JdbcTemplate）** | 参数化 SQL、事务内数据库操作 |
| 数据库 | **MySQL 8 / InnoDB** | 业务持久化、外键、行锁和事务 |
| 前端 | HTML / CSS / JavaScript | 基础浏览器操作页面 |
| 构建工具 | Maven | 编译、依赖管理和测试 |
| 自动化测试 | JUnit 5、Spring Boot Test、H2 | 业务、接口和回滚测试 |
| 接口联调 | Postman | 请求验证及响应检查 |
| 团队协作 | Git、GitHub、Pull Request | 分支管理、代码评审和过程留痕 |



## 🏗 系统架构

```text
浏览器页面 / Postman
        │ HTTP + JSON
        ▼
Controller（请求入口、参数校验）
        │
        ├── SessionAuthInterceptor（登录会话、权限与 CSRF 检查）
        ▼
Service（业务规则、@Transactional 事务）
        │
        ▼
Mapper（JdbcTemplate + 参数化 SQL）
        │
        ▼
MySQL / InnoDB
  ├── reader_account           读者信息
  ├── book_inventory           馆藏图书副本
  ├── borrow_record            借阅记录
  ├── operation_log            操作日志
  └── requirement_change_log   需求变更记录
```

**借阅事务示例：** 检查读者 → 锁定相应数据 → 校验图书可借状态 → 新增借阅记录 → 更新图书状态和读者借阅数 → 一次性提交；发生异常则回滚，避免只更新了一部分数据。归还操作采用相同的事务一致性原则。

## 📁 项目目录

```text
software-process/
├── README.md                         # 项目主页说明
├── pom.xml                           # Maven 配置（开发工程合入后）
├── .env.example                      # 本地环境变量示例（不得填写真实密码）
├── .gitignore                        # Git 忽略规则
├── 1-需求获取与分析/                # 需求池与需求分析报告
├── 2-需求规格说明与评审/            # SRS 与需求评审记录
├── 3-需求跟踪矩阵建立/              # 测试用例、追踪矩阵与证据
├── 4-需求变更控制/                  # 变更请求、影响分析和记录
├── src/
│   ├── main/java/edu/course/library/
│   │   ├── controller/               # 接口层
│   │   ├── service/                  # 业务和事务层
│   │   ├── mapper/                   # 数据访问层
│   │   ├── model/                    # 领域数据模型
│   │   └── config/                   # 运行配置和权限控制
│   ├── main/resources/static/        # 基础页面
│   └── test/                         # JUnit 自动化测试
├── sql/                              # 新库建表、只读审计、可选迁移
├── postman/                          # Postman 集合与环境模板
├── scripts/                          # Windows 启动、检查和备份脚本
├── docs/                             # 技术说明、部署及测试指导
├── proposals/                        # R003 等候选变更资料
└── .github/workflows/                # CI 配置
```


## 🚀 本地运行（Windows）

### 1. 环境要求

- JDK **17 或 21**（项目按 Java 17 编译；尽量避免用未经验证的更高版本）。
- Maven 3.9+。
- MySQL 8.0。
- VS Code 或 IntelliJ IDEA；推荐安装 Postman 进行 API 联调。

先检查工具：

```powershell
java -version
javac -version
mvn -version
```

如果 VS Code 中暂时找不到 `mvn`，且 Maven 安装在 `C:\env\apache-maven`，可以用完整路径执行：

```powershell
& "C:\env\apache-maven\bin\mvn.cmd" -version
```

### 2. 在自己的电脑准备独立开发库

**不要直接在队友的 `library_db` 上初始化、清空或导入数据库。** 先在本机使用 `library_dev`：

1. 确认连接的是本地 MySQL，执行 `sql/01_create_fresh_dev_db.sql` 创建开发库。
2. 选中 `library_dev` 为当前数据库，依次执行 `sql/library_init.sql` 和 `sql/requirement_change_log.sql`。
3. 创建一个仅对 `library_dev` 有权限的 MySQL 应用账号，不建议后端直接使用 `root`。
4. **旧结构存量库**（口令列仍叫 `password_md5`）请改跑 `sql/10_retire_legacy_migration.sql` 完成一次性迁移。
5. 旧库 `library_db.sql` 已于 2026-10-23 退役并停止 Git 跟踪，不要再执行或分发该导出文件（含 `DROP TABLE`）。

更详细的步骤见 `docs/03-Windows本机联调与Postman.md`。

### 3. 编译并运行自动化测试

打开项目根目录（包含 `pom.xml`），执行：

```powershell
mvn clean test
```

如果 Maven 尚未加入 PATH：

```powershell
& "C:\env\apache-maven\bin\mvn.cmd" clean test
```

测试结果以本机 Maven 输出及 `target/surefire-reports/` 记录为准，不能把“测试方法已经写好”视为“测试已经通过”。

### 4. 启动服务

项目使用 `src/main/resources/application.yml` 读取环境变量，包括 `DB_URL`、`DB_USERNAME` 和 `DB_PASSWORD`。请勿将真实密码提交到 GitHub。

Windows 下推荐在项目根目录运行交互式启动脚本：

```powershell
.\scripts\start-local.ps1
```

脚本会提示输入本地数据库账号、密码和演示账号信息，并使用 `library_dev` 的演示配置启动 Spring Boot。若 PowerShell 执行策略限制运行，请先查看本地联调手册，确认脚本可信后再按需调整。

服务启动后访问：

- 首页：`http://localhost:8080/`
- 健康检查：`http://localhost:8080/api/health`

健康接口响应不代表 MySQL 业务事务和全部功能都已经验收成功。

## 🔌 主要接口

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| `POST` | `/api/auth/login` | 登录 |
| `GET` | `/api/auth/me` | 获取当前会话信息 |
| `POST` | `/api/auth/logout` | 退出登录 |
| `GET` | `/api/books` | 检索馆藏 |
| `POST` | `/api/borrow` | 借阅图书（R001） |
| `POST` | `/api/return` | 归还图书（R002） |
| `GET` | `/api/borrow_records/my` | 查询本人借阅记录 |
| `POST` | `/api/borrow_records/{id}/renew` | 续借（R003，默认关闭） |
| `GET` | `/api/admin/overdue` | 管理员查看逾期情况 |
| `GET` | `/api/admin/logs` | 管理员查看操作记录 |

涉及登录态的写请求需按项目要求携带会话 Cookie 和 CSRF Token。完整请求体及返回值请参考 `postman/Library_Local_Integration.postman_collection.json`。

**Postman 测试建议：** 依次验证健康检查、读者登录、图书检索、借阅、本人借阅记录、归还、重复归还；管理员操作和 R003 场景单独执行。集合包含数据库写操作，**不建议在共享数据库上直接 Run All**。

## ✅ 测试与质量保障

项目围绕以下场景设计测试：

- 正常借阅、借阅数量超限、图书不可借、读者状态异常。
- 正常归还、重复归还、无效借阅记录、逾期状态变化。
- 事务执行中故意触发异常，验证多表数据整体回滚。
- 未登录、权限不足、参数非法、CSRF 缺失等接口行为。
- 真实 MySQL 中的并发借阅与数据库一致性检查。
- R003 变更启用后的续借次数、借期和回归测试。

开发增量中**已编写 37 个 JUnit 测试方法**，2026-10-23 已在本机 JDK 17 环境实际执行：**37/37 通过**（H2，0 失败 0 错误；tc029 旧 MD5 兼容用例随旧库退役删除）。Postman 主流程经 newman 实测通过：2026-10-22 跑出 **13 请求 / 16 断言**（重复入库 409 分支），2026-10-23 新库重置后跑出 **13 请求 / 15 断言**（首次入库 200 分支），两条分支均 0 失败；并在本机 **MySQL 8.0.45** 的 `library_dev` 上完成真实库一致性检查（0 行不一致）与 10 线程并发争抢（仅 1 次成功）。证据与复现命令见 [本机联调与 Postman 手册](docs/03-Windows本机联调与Postman.md) 和 [测试运行证据](docs/05-测试用例与运行证据.md)。

> 环境说明：最终证据来自本机 3306 的 MySQL 8.0.45（InnoDB），应用使用仅授权 `library_dev.*` 的受限账号；R003 续借回归未执行，功能仍默认关闭。

## 👥 小组协作与软件过程交付

本项目由四名成员分工协作，采用 GitHub feature 分支和 Pull Request 组织代码审查。

| 角色 | 主要职责 |
| --- | --- |
| 曾丽欣 · 项目负责人 | 仓库管理、需求评审、PR 审核、基线版本及交付统筹 |
| 耿佳钰 · 需求分析师 | 需求池、分析报告、SRS、需求追踪矩阵与需求变更记录 |
| 袁跃宝 · 开发工程师 | SQL、借还事务、服务端接口、自动化测试及技术联调 |
| 刘宇阳 · 测试工程师 | 测试用例、Postman/数据库验证、缺陷管理和回归测试 |

工作流：**需求确认 → 创建分支 → 编码与测试 → Commit → Push → Pull Request → 同伴审核 → 合并 → 更新追踪记录**。

Commit 应说明修改内容及原因，例如：

```text
feat(borrow): 实现R001借阅事务（原因：确保借阅记录与库存更新原子性）
test(return): 补充R002异常回滚用例（原因：验证归还失败时数据库一致性）
docs(requirements): 修订AC-7验收口径（原因：统一逾期归还后的状态规则）
```

需求冻结与变更基线可使用 `baseline-req-v1.0`、`baseline-req-v1.1` 等 Tag，但应在**实际评审通过、变更实施和验证完成后**创建，不提前伪造状态。

## ⚠️ 需求变更和安全说明

1. **R003 续借属于受控变更**：候选规则为延长 15 天、最多一次；默认关闭。需要确认 CR-001 审批、数据库迁移及 QA 结果后再启用。
2. **逾期阈值口径已确认**：逾期超过 35 天（第 36 天起）冻结，由管理员在管理台点击解冻；旧版 SRS 中“超过 30 天”的表述需成员 2 与组长修订。
3. **旧库已退役**：`library_db.sql` 已于 2026-10-23 停止跟踪、不再分发；新库唯一权威结构为 `sql/library_init.sql`（`password_hash` BCrypt），旧结构存量库跑 `sql/10_retire_legacy_migration.sql`。
4. **保护凭据**：不得提交数据库密码、Cookie、包含真实密码的 Postman 环境文件、`.env`、日志或个人备份。
5. **真实证据优先**：Git 提交、PR、JUnit、Postman、MySQL 和需求评审结果，均以实际记录为准。

## 📖 相关资料

- 四个课程阶段文件夹：需求获取、需求规格与评审、需求跟踪、需求变更控制。
- `docs/`：系统结构、联调部署、需求差异、团队决议与上传说明。
- `postman/README.md`：Postman 导入与执行顺序。
- `sql/`：建表、只读审计、一致性验证及受控迁移脚本。

---

**课程关键词：** 需求管理 · 软件过程 · Spring Boot · MySQL · 数据库事务 · 并发控制 · 自动化测试 · GitHub 协作
