/*
 Navicat Premium Dump SQL

 Source Server         : MySQL01
 Source Server Type    : MySQL
 Source Server Version : 80044 (8.0.44)
 Source Host           : localhost:3306
 Source Schema         : library_db

 Target Server Type    : MySQL
 Target Server Version : 80044 (8.0.44)
 File Encoding         : 65001

 Date: 21/10/2026 09:12:37
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for book_inventory
-- ----------------------------
DROP TABLE IF EXISTS `book_inventory`;
CREATE TABLE `book_inventory`  (
  `book_inventory_id` int NOT NULL AUTO_INCREMENT,
  `barcode` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '图书条码（唯一标识每一本书）',
  `isbn` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'ISBN号（同一种书可能有多本）',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '书名',
  `author` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '作者',
  `category` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '分类号（如I247.5科幻）',
  `location` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '馆藏位置（如A区-3排-05架）',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'AVAILABLE' COMMENT 'AVAILABLE(在馆)/BORROWED(借出)',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`book_inventory_id`) USING BTREE,
  UNIQUE INDEX `barcode`(`barcode` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of book_inventory
-- ----------------------------
INSERT INTO `book_inventory` VALUES (1, 'BC-0001', '978-7-222-22222-2', '三体', '刘慈欣', 'I247.5', 'A区-3排-05架', 'AVAILABLE', '2026-10-20 21:01:16');
INSERT INTO `book_inventory` VALUES (2, 'BC-0002', '978-7-222-22222-2', '三体', '刘慈欣', 'I247.5', 'B区-1排-02架', 'AVAILABLE', '2026-10-20 21:01:16');
INSERT INTO `book_inventory` VALUES (3, 'BC-0003', '978-7-111-11111-1', '格林童话', '格林兄弟', 'I516.88', 'C区-儿童区', 'AVAILABLE', '2026-10-20 21:01:16');

-- ----------------------------
-- Table structure for borrow_record
-- ----------------------------
DROP TABLE IF EXISTS `borrow_record`;
CREATE TABLE `borrow_record`  (
  `borrow_record_id` int NOT NULL AUTO_INCREMENT,
  `reader_id` int NOT NULL COMMENT '哪个读者',
  `book_inventory_id` int NOT NULL COMMENT '哪本书',
  `borrow_time` datetime NOT NULL COMMENT '借阅时间',
  `expected_return_date` date NOT NULL COMMENT '应还日期',
  `actual_return_time` datetime NULL DEFAULT NULL COMMENT '实际归还日期',
  `overdue_days` int NOT NULL DEFAULT 0 COMMENT '逾期天数',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'BORROWED' COMMENT 'BORROWED/RETURNED',
  PRIMARY KEY (`borrow_record_id`) USING BTREE,
  INDEX `fk_borrow_book`(`book_inventory_id` ASC) USING BTREE,
  INDEX `idx_borrow_record_reader`(`reader_id` ASC) USING BTREE,
  INDEX `idx_borrow_record_status`(`status` ASC) USING BTREE,
  CONSTRAINT `fk_borrow_book` FOREIGN KEY (`book_inventory_id`) REFERENCES `book_inventory` (`book_inventory_id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_borrow_reader` FOREIGN KEY (`reader_id`) REFERENCES `reader_account` (`reader_id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `borrow_record_chk_1` CHECK (`overdue_days` >= 0)
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of borrow_record
-- ----------------------------

-- ----------------------------
-- Table structure for operation_log
-- ----------------------------
DROP TABLE IF EXISTS `operation_log`;
CREATE TABLE `operation_log`  (
  `log_id` int NOT NULL AUTO_INCREMENT,
  `operation` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'BORROW/RETURN/ERROR',
  `operator` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `reader_id` int NULL DEFAULT NULL,
  `book_isbn` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `detail` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `op_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`log_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of operation_log
-- ----------------------------

-- ----------------------------
-- Table structure for reader_account
-- ----------------------------
DROP TABLE IF EXISTS `reader_account`;
CREATE TABLE `reader_account`  (
  `reader_id` int NOT NULL AUTO_INCREMENT,
  `card_no` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '读者证号',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '姓名',
  `password_md5` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '密码的MD5哈希值',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/RESTRICTED/FROZEN',
  `borrowed_count` int NOT NULL DEFAULT 0 COMMENT '当前借阅数',
  `borrow_limit` int NOT NULL DEFAULT 5 COMMENT '借阅上限',
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '联系电话',
  `register_date` date NOT NULL COMMENT '注册日期',
  PRIMARY KEY (`reader_id`) USING BTREE,
  UNIQUE INDEX `card_no`(`card_no` ASC) USING BTREE,
  CONSTRAINT `reader_account_chk_1` CHECK (`borrowed_count` >= 0),
  CONSTRAINT `reader_account_chk_2` CHECK (`borrow_limit` > 0)
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of reader_account
-- ----------------------------
INSERT INTO `reader_account` VALUES (1, '1001', '张三', 'e10adc3949ba59ab6e05720eb09ac739', 'NORMAL', 0, 5, NULL, '2026-09-01');
INSERT INTO `reader_account` VALUES (2, '1002', '李四', 'e10adc3949ba59ab6e05720eb09ac739', 'NORMAL', 0, 5, NULL, '2026-09-01');

-- ----------------------------
-- Table structure for requirement_change_log
-- ----------------------------
DROP TABLE IF EXISTS `requirement_change_log`;
CREATE TABLE `requirement_change_log`  (
  `change_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '变更编号，如CR-001',
  `req_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '关联需求编号，如R003',
  `change_title` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '变更标题',
  `change_desc` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '变更内容描述',
  `change_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '变更原因(必填)',
  `proposer` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '提出人',
  `propose_date` date NOT NULL COMMENT '提出日期',
  `impact_analysis` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '影响分析结论',
  `approval_result` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '审批结论',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '待审批' COMMENT '状态',
  `implementer` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '实施人',
  `implement_date` date NULL DEFAULT NULL COMMENT '实施日期',
  `verify_result` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '验证结果',
  `baseline_version` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '关联基线版本',
  PRIMARY KEY (`change_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of requirement_change_log
-- ----------------------------
INSERT INTO `requirement_change_log` VALUES ('CR-2026-1020-001', 'R003', '新增图书续借功能', '在借阅记录表增加renew_count字段，允许续借一次', '读者外出实习无法及时归还，希望增加自助续借', '耿佳钰', '2026-10-20', '影响低，仅需加1个字段和1个接口', '批准', '已批准', '袁跃宝', NULL, NULL, 'v1.1');

SET FOREIGN_KEY_CHECKS = 1;
