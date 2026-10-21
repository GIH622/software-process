-- 与已收到的 2026-10-21 library_db.sql 相同字段结构；仅用于新库缺表时创建。
-- 不会自动写入、修改或伪造现有 CR 审批记录。
CREATE TABLE IF NOT EXISTS requirement_change_log (
  change_id VARCHAR(20) NOT NULL COMMENT '需求变更编号',
  req_code VARCHAR(20) NOT NULL,
  change_title VARCHAR(100) NOT NULL,
  change_desc TEXT NOT NULL,
  change_reason VARCHAR(500) NOT NULL,
  proposer VARCHAR(50) NOT NULL,
  propose_date DATE NOT NULL,
  impact_analysis TEXT NULL,
  approval_result VARCHAR(20) NULL,
  status VARCHAR(20) NOT NULL DEFAULT '待审批',
  implementer VARCHAR(50) NULL,
  implement_date DATE NULL,
  verify_result VARCHAR(200) NULL,
  baseline_version VARCHAR(20) NULL,
  PRIMARY KEY(change_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
