-- MySQL 8.0：只读验收 SQL。下面三种不一致查询理应返回0行；如有记录，停止验收并排查。
-- 1 读者的借阅计数与真实在借记录不一致。
SELECT a.reader_id,a.card_no,a.borrowed_count,COUNT(r.borrow_record_id) actual_active_count
  FROM reader_account a LEFT JOIN borrow_record r ON a.reader_id=r.reader_id AND r.status='BORROWED'
  GROUP BY a.reader_id,a.card_no,a.borrowed_count
  HAVING a.borrowed_count<>actual_active_count;
-- 2 在借记录与图书状态不一致。
SELECT r.borrow_record_id,b.barcode,r.status,b.status AS book_status FROM borrow_record r
  JOIN book_inventory b ON b.book_inventory_id=r.book_inventory_id
  WHERE r.status='BORROWED' AND b.status<>'BORROWED';
-- 3 图书状态BORROWED却没有对应在借记录。
SELECT b.barcode,b.status FROM book_inventory b LEFT JOIN borrow_record r
  ON b.book_inventory_id=r.book_inventory_id AND r.status='BORROWED'
  WHERE b.status='BORROWED' AND r.borrow_record_id IS NULL;
-- 4 一个副本多条在借：不能出现；现有共享库并无唯一生成列；先靠事务行锁，并建议经过审批后安装03可选索引。
SELECT book_inventory_id,COUNT(*) active_count FROM borrow_record WHERE status='BORROWED'
  GROUP BY book_inventory_id HAVING COUNT(*)>1;
-- 5 样例查询（按照需求补充文档中的条码，不依赖存在真实表数据）。
SELECT barcode,title,location,status FROM book_inventory WHERE barcode IN ('BC-0001','BC-0002','BC-0003');
