-- library_dev 唯一权威建表脚本（2026-10-23 旧库退役后，结构与 library_db 脱钩）。
-- 不含 CREATE DATABASE / USE / DROP，必须由执行者先手动选中 library_dev！
-- 口令列统一为 password_hash（BCrypt）。旧结构存量库请改跑 sql/10_retire_legacy_migration.sql。
-- 绝对不要把已退役的 library_db.sql 导出文件当作增量迁移执行。
CREATE TABLE IF NOT EXISTS reader_account (
 reader_id INT AUTO_INCREMENT PRIMARY KEY,card_no VARCHAR(30) NOT NULL UNIQUE,
 name VARCHAR(50) NOT NULL,password_hash VARCHAR(72) NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'NORMAL',borrowed_count INT NOT NULL DEFAULT 0,
 borrow_limit INT NOT NULL DEFAULT 5,phone VARCHAR(20),register_date DATE NOT NULL,
 CONSTRAINT ck_reader_count CHECK (borrowed_count >= 0),
 CONSTRAINT ck_reader_limit CHECK (borrow_limit > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS book_inventory (
 book_inventory_id INT AUTO_INCREMENT PRIMARY KEY,barcode VARCHAR(50) NOT NULL UNIQUE,
 isbn VARCHAR(20) NOT NULL,title VARCHAR(200) NOT NULL,author VARCHAR(100),
 category VARCHAR(50),location VARCHAR(50),status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
 created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_book_isbn(isbn),INDEX idx_book_status(status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS borrow_record (
 borrow_record_id INT AUTO_INCREMENT PRIMARY KEY,reader_id INT NOT NULL,
 book_inventory_id INT NOT NULL,borrow_time DATETIME NOT NULL,
 expected_return_date DATE NOT NULL,actual_return_time DATETIME,
 overdue_days INT NOT NULL DEFAULT 0,status VARCHAR(20) NOT NULL DEFAULT 'BORROWED',
 INDEX idx_borrow_reader(reader_id),INDEX idx_borrow_status(status),
 INDEX idx_borrow_copy_status(book_inventory_id,status),
 FOREIGN KEY(reader_id) REFERENCES reader_account(reader_id),
 FOREIGN KEY(book_inventory_id) REFERENCES book_inventory(book_inventory_id),
 CONSTRAINT ck_overdue CHECK(overdue_days >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS operation_log (
 log_id INT AUTO_INCREMENT PRIMARY KEY,operation VARCHAR(30) NOT NULL,
 operator VARCHAR(50),reader_id INT,book_isbn VARCHAR(20),
 detail VARCHAR(500),op_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_operation_time(op_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
