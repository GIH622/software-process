# Postman导入与执行说明

导入Library_Local_Integration.postman_collection.json与Library_Local_Template.postman_environment.json；本地界面填写readerPassword/adminPassword（绝不导出包含密码的真实环境）。Cookie Jar须启用，baseUrl保持同一主机名。

首轮顺序：00健康→01读者登录（存csrf）→02检索→03借阅（存recordId）→04借阅记录→05归还→06重复归还预期409；07管理员登录会覆盖当前session，08/09/10/12属于管理员操作，尤其10新增图书只手动执行一次。11 R003默认应409；13只有在变更迁移且启用R003并重新借书后才单独执行。**不要无脑运行整个Collection**，其中有写操作。

真实DB检查：运行sql/04_verify_consistency.sql并记录截图。参见docs/03-Windows本机联调与Postman.md。
