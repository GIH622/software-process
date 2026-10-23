-- ============================================================================
-- 一次性迁移：旧库结构（password_md5）→ 新结构（password_hash），旧库正式退役。
-- 决议依据：docs/14-里程碑-旧库退役与统一新库.md（2026-10-23）
--
-- 适用对象：
--   * 已用旧版 library_init.sql（password_md5 结构）建过表的存量 library_dev；
--   * 曾导入过团队 library_db.sql（MD5 口令）数据的库。
--   全新建库者直接运行新版 library_init.sql，跳过本脚本。
--
-- 执行前：务必在 Navicat 先转储备份 library_dev（结构与数据）。
-- 执行者：可用 root 或具备 library_dev.* ALTER/UPDATE 权限的账号；应用账号不要跑 DDL。
-- 执行后：重启应用（java -jar）即可，代码已全部切换到 password_hash。
--
-- 口令策略：MD5 无法换算为 BCrypt，残留 MD5 行统一重置为演示口令 123456。
--   演示库可接受；如有真实读者，请另行通知其修改口令。
-- ============================================================================

-- 1) 列改名。若报 Unknown column 'password_md5'，说明已迁移过，跳过本句。
ALTER TABLE reader_account RENAME COLUMN password_md5 TO password_hash;

-- 2) 口令统一 BCrypt：残留 MD5（32位十六进制、非 $2 开头）重置为 123456 的 BCrypt 哈希。
--    已是 BCrypt（$2 开头）的行不受影响，本句可重复执行。
UPDATE reader_account
   SET password_hash='$2a$10$DraSR2IslAF9XPxm.gRkge16m6ow/egTpSObjoom0vX.6YsE0gTWG'
 WHERE password_hash NOT LIKE '$2%';

-- 3) 自检：应返回 0 行（不存在非 BCrypt 口令，且列名已切换）。
SELECT COUNT(*) AS not_bcrypt_rows FROM reader_account WHERE password_hash NOT LIKE '$2%';
