-- 只用于你个人电脑：创建全新的 library_dev 演示库，不碰队友 library_db。
-- 强烈建议在 MySQL Workbench 核对目标连接后执行。
CREATE DATABASE IF NOT EXISTS library_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- 开发专用账户请使用本机管理权限单独创建并授予 library_dev.* 权限；不要让应用用 root。
-- 参见 docs/03-Windows本机联调与Postman.md 中的建库与账户步骤。
