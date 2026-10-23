# Postman导入与执行说明

导入Library_Local_Integration.postman_collection.json与Library_Local_Template.postman_environment.json；本地界面填写readerPassword/adminPassword（绝不导出包含密码的真实环境）。Cookie Jar须启用，baseUrl保持同一主机名。

集合已分为两个文件夹：

1. **主流程（自动执行，00-12）**：00健康→01读者登录（存csrf）→02检索→03借阅（存recordId）→04借阅记录→05归还→06重复归还预期409；07管理员登录会覆盖当前session，08/09/10/12属于管理员操作。10重复执行会返回409 `BOOK_EXISTS`，断言已允许200/409，可重复跑。11在管理员会话下使用`adminCsrfToken`，R003默认应返回409。
2. **R003 变更回归（默认关闭，需迁移+启用后单独执行）**：13读者登录→14借阅`BC-0003`并保存`renewRecordId`→15续借。只有CR-001审批、执行`sql/20_CR001_renewal_optional.sql`、设置`LIBRARY_RENEWAL_ENABLED=true`后才单独执行；不再复用主流程中已归还的`recordId`。

命令行执行主流程：

```powershell
npx newman run "postman\Library_Local_Integration.postman_collection.json" -e "$env:TEMP\library-env.json" --folder "主流程（自动执行，00-12）"
```

复跑注意：同一读者当天借过某ISBN后，再借同ISBN另一副本会返回409 `DUPLICATE_BORROW`（现有业务规则），请换不同ISBN副本或换读者；10新增图书重复执行返回409属预期。2026-10-22在本机MySQL 8.0.45实测13请求/16断言全部通过（409分支，`docs/evidence/newman-main-mysql8-20261022-postfix.json`）；2026-10-23旧库退役、清库重建后复跑13请求/15断言全部通过（200分支，`docs/evidence/newman-main-mysql8-20261023-retire.json`）。

真实DB检查：运行sql/04_verify_consistency.sql并记录截图。参见docs/03-Windows本机联调与Postman.md。
