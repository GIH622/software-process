# 根据真实 library_db.sql 的兼容性分析

> **状态更新（2026-10-23）**：旧库 `library_db` 已退役。本文所述的 MD5 兼容登录、`password_md5` 旧列等兼容策略已被一次性迁移取代（`sql/10_retire_legacy_migration.sql`），详见 `docs/14-里程碑-旧库退役与统一新库.md`。本文保留作为历史记录。

## 导出快照核查结果
导出文件头显示：MySQL8.0.44、源库 `library_db`、2026-10-21 09:12:37。表如下：

| 表 | 实际重要字段 | 代码处理 |
|---|---|---|
| `reader_account` | reader_id、card_no、password_md5、status、borrowed_count、borrow_limit | 直接访问这些真实字段；历史MD5兼容登录、不开自动改写 |
| `book_inventory` | book_inventory_id、barcode、isbn、title、author、category、location、status | 唯一条码定位单本副本；重复ISBN允许多条 |
| `borrow_record` | borrow_record_id、reader_id、book_inventory_id、borrow_time、expected_return_date、actual_return_time、overdue_days、status | R001/R002无须额外列；R003需经审批新增renew_count |
| `operation_log` | log_id、operation、operator、reader_id、book_isbn、detail、op_time | 保存ISBN字段；条码附在detail中 |
| `requirement_change_log` | change_id、req_code、approval_result、status、implement_date、verify_result、baseline_version等 | 不覆盖真实记录，不创建虚构审批结论 |

现有备份中 R003 变更编号 `CR-2026-1020-001`，审批结果为批准、状态已批准，实施日期与验证结果NULL。需要成员2和组长找会议/禅道证据核对后再使用。

## 如果数据库在 zlx 的另一台电脑
导出写着 `Source Host: localhost`，只表示它当时运行在 zlx 自己的电脑上；**在你电脑写 `localhost` 连到的是你自己的MySQL，并不会连到队友的电脑**。跨电脑访问需要队友同意，提供局域网主机IP、端口及专门的只读/最小权限账户；管理员必须正确配置MySQL的监听地址、防火墙和`user@host`授权。不要开放3306到公网，更不要共享root密码。先用 `scripts/mysql-readonly-preflight.ps1` 做只读查询，得到明确授权后再约定测试窗口。

## 先做只读预检（绝不重建表）
备份原文件另存档，在目标库运行 `sql/02_existing_db_read_only_audit.sql`。请比对表、列、索引、测试书本及真实借阅计数。**禁止**将原导出的`DROP TABLE IF EXISTS`脚本直接在现有库执行；它会破坏已有数据。不要拿已授权的MySQL口令放进application.yml、Postman导出或Git提交。

## 强事务仍有一个数据库层缺口
真实库只有普通索引，并没有“同副本在借记录唯一”约束。当前 Service 通过事务中锁副本、受保护UPDATE、重新查状态来防并发；但绕过应用用SQL手动插入可能出现冲突。建议团队先运行唯一冲突检查（应该0行），备份并确认后在 feature/db-active-index 分支评审`sql/03_add_active_loan_unique_index.sql`。这是**可选结构增强**，它包含 ALTER TABLE，不得由启动脚本自动执行。

## 旧密码字段
原 `password_md5 VARCHAR(64)` 的现有数据仍为旧哈希。新增读者使用BCrypt长度60，会存到同名列中；这意味着旧版登录程序可能无法识别新值，所以禁止旧版和新版服务在同一共享库并行写入，而不事先协调密码策略。若准备执行升级，先备份、全员切换新实现，再显式设置 `LIBRARY_LEGACY_MD5_UPGRADE_ENABLED=true` 并做好回退计划。

## 部署前门槛
1. 组长和数据库管理员确认现有数据库名称、表结构、账号权限和备份位置。
2. 先在 `library_dev` 全部通过自动测试和真实MySQL测试。
3. 让数据库管理员提供限定范围的账号（非root），避免程序获得DDL权限。
4. 收集只读预检结果，确认现有计数、在借记录不矛盾。
5. 只有在小组授权后，才修改DB_URL指向现有数据库做实际读写联调。
6. R003 的 `ALTER TABLE` 独立提交PR并由组长合并；实施后记录执行人、日期、原因、验证截图，才可以启用R003配置。
