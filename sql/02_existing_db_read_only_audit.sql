-- 只读：先在Navicat/Workbench选择队友 library_db，执行前不涉及任何DML、DDL。
SELECT DATABASE() AS current_schema, @@version AS mysql_version, @@session.time_zone AS session_zone;
SELECT table_name,table_rows FROM information_schema.tables
 WHERE table_schema=DATABASE() AND table_name IN
 ('reader_account','book_inventory','borrow_record','operation_log','requirement_change_log') ORDER BY table_name;
SELECT table_name,column_name,column_type FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name IN
 ('reader_account','book_inventory','borrow_record','operation_log','requirement_change_log')
 ORDER BY table_name,ordinal_position;
SELECT table_name,index_name,non_unique,column_name FROM information_schema.statistics
 WHERE table_schema=DATABASE() AND table_name IN ('borrow_record','book_inventory')
 ORDER BY table_name,index_name,seq_in_index;
-- 校验现有业务数据，而不改变任何记录。
SELECT bi.barcode,bi.title,bi.status FROM book_inventory bi ORDER BY bi.book_inventory_id LIMIT 30;
SELECT r.card_no,r.name,r.status,r.borrowed_count,r.borrow_limit FROM reader_account r LIMIT 30;
SELECT change_id,req_code,status,approval_result,implement_date,verify_result,baseline_version
 FROM requirement_change_log ORDER BY propose_date DESC LIMIT 20;
-- 若这里出现行，代表一份副本已经有多条有效借阅，不可直接加唯一索引。
SELECT book_inventory_id,COUNT(*) AS active_count FROM borrow_record
 WHERE status='BORROWED' GROUP BY book_inventory_id HAVING COUNT(*)>1;
-- 当前计数偏差，必须先人工核查原因。
SELECT r.reader_id,r.card_no,r.borrowed_count,
 (SELECT COUNT(*) FROM borrow_record br WHERE br.reader_id=r.reader_id AND br.status='BORROWED') AS real_count
 FROM reader_account r
 WHERE r.borrowed_count <> (SELECT COUNT(*) FROM borrow_record br WHERE br.reader_id=r.reader_id AND br.status='BORROWED');
