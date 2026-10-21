-- 选做变更：SQL备份中CR-2026-1020-001的审批结果为“批准”，但无实施/验证日期。
-- 必须由组长核对CCB记录、数据库已备份、明确变更窗口后，在feature/CR-001执行一次。
-- 先检查：SELECT DATABASE(); SHOW COLUMNS FROM borrow_record LIKE 'renew_count';
-- 只应对缺少 renew_count 的旧库执行一次，不要在重复运行时忽略 Duplicate column 错误。
ALTER TABLE borrow_record ADD COLUMN renew_count INT NOT NULL DEFAULT 0 COMMENT 'R003 已续借次数';
ALTER TABLE borrow_record ADD CONSTRAINT chk_renew_count CHECK (renew_count BETWEEN 0 AND 1);
-- 迁移/测试通过后配置 LIBRARY_RENEWAL_ENABLED=true 再重启；先不开功能开关。
