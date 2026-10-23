-- OPTIONAL: MySQL 8.x 增强索引，非日常初始化脚本！仅在备份、人工审批和重复检查清零后执行一次。
-- 本脚本改变现有表结构，绝不可自动执行；新建 library_dev 也需主动选择。
-- 预检：SELECT book_inventory_id,COUNT(*) FROM borrow_record WHERE status='BORROWED'
--       GROUP BY book_inventory_id HAVING COUNT(*)>1;
-- 预检：SHOW COLUMNS FROM borrow_record LIKE 'active_book_id';
ALTER TABLE borrow_record
 ADD COLUMN active_book_id INT GENERATED ALWAYS AS
 (CASE WHEN status='BORROWED' THEN book_inventory_id ELSE NULL END) STORED,
 ADD UNIQUE INDEX uk_active_borrow_book (active_book_id);
-- MySQL UNIQUE允许多个NULL，因此历史已归还记录不受单一副本多次借阅的限制。
