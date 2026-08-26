CREATE TABLE IF NOT EXISTS batch_definition (
  batch_id VARCHAR(64) PRIMARY KEY,
  batch_type VARCHAR(80) NOT NULL,
  schedule_cycle VARCHAR(120) NOT NULL,
  predecessor_batch_id VARCHAR(64),
  successor_batch_id VARCHAR(64),
  execution_parameters TEXT,
  max_execution_seconds INTEGER NOT NULL CHECK (max_execution_seconds > 0),
  owner_user_id VARCHAR(64) NOT NULL REFERENCES user_account(user_id),
  use_yn CHAR(1) NOT NULL CHECK (use_yn IN ('Y','N')),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE batch_definition IS '평가자료 생성·연계·점수산출 배치의 정의, 실행주기, 선후행 관계, 파라미터와 담당자를 관리한다.';
COMMENT ON COLUMN batch_definition.predecessor_batch_id IS 'batch_definition.batch_id 참조 의도 (순환/선후행 업무 검증은 애플리케이션에서 수행)';
COMMENT ON COLUMN batch_definition.successor_batch_id IS 'batch_definition.batch_id 참조 의도 (순환/선후행 업무 검증은 애플리케이션에서 수행)';
COMMENT ON COLUMN batch_definition.execution_parameters IS 'BatchDefinitionService.create/update 시 API 요청 JSON을 문자열로 저장하고 조회 응답에서 반환한다';
COMMENT ON COLUMN batch_definition.use_yn IS 'Y:사용|N:미사용';

CREATE TABLE IF NOT EXISTS batch_execution (
  execution_id VARCHAR(64) PRIMARY KEY,
  batch_id VARCHAR(64) NOT NULL REFERENCES batch_definition(batch_id),
  operation_type VARCHAR(30) NOT NULL CHECK (operation_type IN ('MANUAL_RUN','STOP','RERUN')),
  execution_parameters TEXT,
  reason VARCHAR(500) NOT NULL,
  operator_user_id VARCHAR(64) NOT NULL REFERENCES user_account(user_id),
  execution_status VARCHAR(30) NOT NULL CHECK (execution_status IN ('REQUESTED','RUNNING','STOP_REQUESTED','STOPPED','COMPLETED','FAILED')),
  original_execution_id VARCHAR(64),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE batch_execution IS '배치 수동실행·중지·재실행 요청과 상태를 원천 업무자료와 분리하여 기록한다.';
COMMENT ON COLUMN batch_execution.operation_type IS 'MANUAL_RUN:수동실행|STOP:중지|RERUN:재실행';
COMMENT ON COLUMN batch_execution.execution_parameters IS 'BatchExecutionService manual/rerun 요청 JSON을 문자열로 저장하고 조회 응답에서 반환한다';
COMMENT ON COLUMN batch_execution.execution_status IS 'REQUESTED:요청됨|RUNNING:실행중|STOP_REQUESTED:중지요청|STOPPED:중지됨|COMPLETED:완료|FAILED:실패';
COMMENT ON COLUMN batch_execution.original_execution_id IS 'batch_execution.execution_id 참조 의도 (재실행/중지 원실행 연결)';

CREATE TABLE IF NOT EXISTS batch_execution_result (
  execution_id VARCHAR(64) PRIMARY KEY REFERENCES batch_execution(execution_id),
  started_at TIMESTAMP,
  ended_at TIMESTAMP,
  processed_count INTEGER NOT NULL DEFAULT 0 CHECK (processed_count >= 0),
  success_count INTEGER NOT NULL DEFAULT 0 CHECK (success_count >= 0),
  failure_count INTEGER NOT NULL DEFAULT 0 CHECK (failure_count >= 0),
  excluded_count INTEGER NOT NULL DEFAULT 0 CHECK (excluded_count >= 0),
  elapsed_seconds INTEGER NOT NULL DEFAULT 0 CHECK (elapsed_seconds >= 0),
  log_file_path VARCHAR(500),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE batch_execution_result IS '배치 실행ID별 시작·종료시각, 처리 건수, 소요시간과 로그파일 경로를 조회 전용으로 보존한다.';
COMMENT ON COLUMN batch_execution_result.log_file_path IS '배치 런타임이 완료 시 갱신하는 로그 파일 참조 경로';

CREATE TABLE IF NOT EXISTS batch_reprocess_target (
  target_id VARCHAR(64) PRIMARY KEY,
  original_execution_id VARCHAR(64) NOT NULL REFERENCES batch_execution(execution_id),
  target_type VARCHAR(30) NOT NULL CHECK (target_type IN ('EXECUTION','ITEM')),
  failure_reference VARCHAR(200) NOT NULL,
  failure_status VARCHAR(30) NOT NULL CHECK (failure_status IN ('FAILED','READY','REPROCESSED')),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE batch_reprocess_target IS '실패 배치 또는 개별 실패 건 중 재처리 가능한 대상을 관리한다.';
COMMENT ON COLUMN batch_reprocess_target.target_type IS 'EXECUTION:실행단위|ITEM:개별건';
COMMENT ON COLUMN batch_reprocess_target.failure_status IS 'FAILED:실패|READY:재처리대기|REPROCESSED:재처리완료';

CREATE TABLE IF NOT EXISTS batch_reprocess_execution (
  reprocess_execution_id VARCHAR(64) PRIMARY KEY,
  original_execution_id VARCHAR(64) NOT NULL REFERENCES batch_execution(execution_id),
  target_id VARCHAR(64) NOT NULL REFERENCES batch_reprocess_target(target_id),
  reason VARCHAR(500) NOT NULL,
  result_status VARCHAR(30) NOT NULL CHECK (result_status IN ('REQUESTED','RUNNING','COMPLETED','FAILED')),
  operator_user_id VARCHAR(64) NOT NULL REFERENCES user_account(user_id),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE batch_reprocess_execution IS '원실행과 실패 대상을 덮어쓰지 않고 별도 재처리 실행 결과를 보존한다.';
COMMENT ON COLUMN batch_reprocess_execution.result_status IS 'REQUESTED:요청됨|RUNNING:실행중|COMPLETED:완료|FAILED:실패';

CREATE INDEX IF NOT EXISTS idx_batch_definition_owner ON batch_definition(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_batch_execution_batch_status ON batch_execution(batch_id, execution_status);
CREATE INDEX IF NOT EXISTS idx_batch_execution_original ON batch_execution(original_execution_id);
CREATE INDEX IF NOT EXISTS idx_batch_reprocess_target_original ON batch_reprocess_target(original_execution_id, failure_status);
CREATE INDEX IF NOT EXISTS idx_batch_reprocess_execution_original ON batch_reprocess_execution(original_execution_id, result_status);

INSERT INTO batch_definition (batch_id, batch_type, schedule_cycle, predecessor_batch_id, successor_batch_id, execution_parameters, max_execution_seconds, owner_user_id, use_yn)
SELECT 'BATCH-EVAL-DATA','EVALUATION_DATA','DAILY 02:00',NULL,'BATCH-SCORE-CALC','{"year":"2026"}',3600,'U-ADMIN','Y'
WHERE NOT EXISTS (SELECT 1 FROM batch_definition WHERE batch_id='BATCH-EVAL-DATA');
INSERT INTO batch_definition (batch_id, batch_type, schedule_cycle, predecessor_batch_id, successor_batch_id, execution_parameters, max_execution_seconds, owner_user_id, use_yn)
SELECT 'BATCH-SCORE-CALC','SCORE_CALCULATION','DAILY 03:00','BATCH-EVAL-DATA',NULL,'{"year":"2026"}',5400,'U-ADMIN','Y'
WHERE NOT EXISTS (SELECT 1 FROM batch_definition WHERE batch_id='BATCH-SCORE-CALC');

INSERT INTO batch_execution (execution_id, batch_id, operation_type, execution_parameters, reason, operator_user_id, execution_status, original_execution_id)
SELECT 'EXEC-SEED-FAILED','BATCH-EVAL-DATA','MANUAL_RUN','{"year":"2026"}','초기 실패 이력','U-ADMIN','FAILED',NULL
WHERE NOT EXISTS (SELECT 1 FROM batch_execution WHERE execution_id='EXEC-SEED-FAILED');
INSERT INTO batch_execution_result (execution_id, started_at, ended_at, processed_count, success_count, failure_count, excluded_count, elapsed_seconds, log_file_path)
SELECT 'EXEC-SEED-FAILED',CURRENT_TIMESTAMP - INTERVAL '10' MINUTE,CURRENT_TIMESTAMP - INTERVAL '5' MINUTE,20,18,2,0,300,'/var/log/batch/EXEC-SEED-FAILED.log'
WHERE NOT EXISTS (SELECT 1 FROM batch_execution_result WHERE execution_id='EXEC-SEED-FAILED');
INSERT INTO batch_reprocess_target (target_id, original_execution_id, target_type, failure_reference, failure_status)
SELECT 'RPT-EXEC-SEED-FAILED','EXEC-SEED-FAILED','EXECUTION','EXEC-SEED-FAILED','FAILED'
WHERE NOT EXISTS (SELECT 1 FROM batch_reprocess_target WHERE target_id='RPT-EXEC-SEED-FAILED');

INSERT INTO menu (menu_id, parent_menu_id, menu_level, menu_name, display_order, screen_id, url, icon, business_category, description, use_yn)
SELECT 'MENU-BATCH', 'MENU-SYS', 'MIDDLE', '배치작업 관리', 5, NULL, NULL, 'batch', '시스템', '배치작업 관리', 'Y'
WHERE NOT EXISTS (SELECT 1 FROM menu WHERE menu_id='MENU-BATCH');
INSERT INTO menu (menu_id, parent_menu_id, menu_level, menu_name, display_order, screen_id, url, icon, business_category, description, use_yn)
SELECT 'MENU-BATCH-DEF','MENU-BATCH','LEAF','배치 정의 관리',1,'SCR-CMN-BATCH-DEF','/admin/batch-definitions','batch-def','시스템','배치 정의 관리','Y'
WHERE NOT EXISTS (SELECT 1 FROM menu WHERE menu_id='MENU-BATCH-DEF');
INSERT INTO menu (menu_id, parent_menu_id, menu_level, menu_name, display_order, screen_id, url, icon, business_category, description, use_yn)
SELECT 'MENU-BATCH-EXEC','MENU-BATCH','LEAF','배치 실행 관리',2,'SCR-CMN-BATCH-EXEC','/admin/batch-executions','batch-run','시스템','배치 실행 관리','Y'
WHERE NOT EXISTS (SELECT 1 FROM menu WHERE menu_id='MENU-BATCH-EXEC');
INSERT INTO menu (menu_id, parent_menu_id, menu_level, menu_name, display_order, screen_id, url, icon, business_category, description, use_yn)
SELECT 'MENU-BATCH-RESULT','MENU-BATCH','LEAF','배치 결과 조회',3,'SCR-CMN-BATCH-RESULT','/admin/batch-results','batch-result','시스템','배치 결과 조회','Y'
WHERE NOT EXISTS (SELECT 1 FROM menu WHERE menu_id='MENU-BATCH-RESULT');
INSERT INTO menu (menu_id, parent_menu_id, menu_level, menu_name, display_order, screen_id, url, icon, business_category, description, use_yn)
SELECT 'MENU-BATCH-REPROCESS','MENU-BATCH','LEAF','배치 오류 재처리',4,'SCR-CMN-BATCH-REPROCESS','/admin/batch-reprocess','batch-reprocess','시스템','배치 오류 재처리','Y'
WHERE NOT EXISTS (SELECT 1 FROM menu WHERE menu_id='MENU-BATCH-REPROCESS');

INSERT INTO menu_permission (permission_id, target_type, target_id, menu_id, allowed, use_yn)
SELECT 'PERM-R09-' || menu_id, 'ROLE', 'R09', menu_id, TRUE, 'Y' FROM menu m WHERE menu_id IN ('MENU-BATCH-DEF','MENU-BATCH-EXEC','MENU-BATCH-RESULT','MENU-BATCH-REPROCESS') AND NOT EXISTS (SELECT 1 FROM menu_permission p WHERE p.target_type='ROLE' AND p.target_id='R09' AND p.menu_id=m.menu_id);
