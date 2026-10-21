-- 占位候选迁移，仅 CCB 批准、备份并核对旧表后由成员3手动执行。
-- 切勿自动运行。library_init.sql 的 v1.0 不含 renew_count。
ALTER TABLE borrow_record ADD COLUMN renew_count INT NOT NULL DEFAULT 0;
-- MySQL 8.0.16+：可在人工确认后再添加 CHECK 约束。
-- ALTER TABLE borrow_record ADD CONSTRAINT chk_renew_count CHECK (renew_count BETWEEN 0 AND 1);
