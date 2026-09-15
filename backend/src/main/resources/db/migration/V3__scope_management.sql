CREATE TABLE IF NOT EXISTS position_assignment (
  assignment_id VARCHAR(64) PRIMARY KEY,
  position_code VARCHAR(64) NOT NULL,
  position_name VARCHAR(100),
  user_id VARCHAR(64) NOT NULL REFERENCES user_account(user_id),
  organization_code VARCHAR(64) NOT NULL REFERENCES organization(organization_code),
  valid_from DATE NOT NULL,
  valid_to DATE,
  status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE','EXPIRED','REVOKED')),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE position_assignment IS '보직코드별 대상 사용자, 소속조직과 유효기간을 관리하여 기간별 보직 권한 판정 원천으로 사용한다.';
COMMENT ON COLUMN position_assignment.status IS 'ACTIVE:유효|EXPIRED:만료|REVOKED:회수';

CREATE TABLE IF NOT EXISTS business_assignee (
  assignee_id VARCHAR(64) PRIMARY KEY,
  business_organization_code VARCHAR(64) NOT NULL REFERENCES organization(organization_code),
  assignee_user_id VARCHAR(64) NOT NULL REFERENCES user_account(user_id),
  business_area_code VARCHAR(64) NOT NULL,
  data_scope VARCHAR(64) NOT NULL,
  processing_permission BOOLEAN NOT NULL,
  valid_from DATE NOT NULL,
  valid_to DATE,
  status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE','EXPIRED','REVOKED')),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE business_assignee IS '업무조직별 담당자, 담당 업무영역, 데이터 범위, 처리 권한과 지정기간을 관리한다.';
COMMENT ON COLUMN business_assignee.business_area_code IS 'EVALUATION:교수업적평가|RESEARCH:학술지원|ADMISSION:입학인재|COMMON:공통운영';
COMMENT ON COLUMN business_assignee.data_scope IS '업무조직·업무영역 안에서 유효한 데이터 범위 코드';
COMMENT ON COLUMN business_assignee.status IS 'ACTIVE:지정|EXPIRED:지정종료|REVOKED:회수';

CREATE TABLE IF NOT EXISTS data_scope_rule (
  rule_id VARCHAR(64) PRIMARY KEY,
  role_code VARCHAR(10) NOT NULL REFERENCES role(role_code),
  data_scope_type VARCHAR(30) NOT NULL CHECK (data_scope_type IN ('SELF','DEPARTMENT','COLLEGE','BUSINESS_AREA','ALL')),
  organization_code VARCHAR(64) REFERENCES organization(organization_code),
  business_area_code VARCHAR(64),
  use_yn CHAR(1) NOT NULL CHECK (use_yn IN ('Y','N')),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE data_scope_rule IS '역할별 본인·학과·단과대학·담당업무·전체 데이터 범위 유형과 서버 조회조건 강제 적용 기준을 관리한다.';
COMMENT ON COLUMN data_scope_rule.data_scope_type IS 'SELF:본인|DEPARTMENT:소속학과|COLLEGE:단과대학|BUSINESS_AREA:담당업무|ALL:전체';
COMMENT ON COLUMN data_scope_rule.business_area_code IS '업무담당자 지정의 business_assignee.business_area_code 참조 의도 (FK 미선언)';
COMMENT ON COLUMN data_scope_rule.use_yn IS 'Y:사용|N:미사용';

CREATE INDEX IF NOT EXISTS idx_position_assignment_effective ON position_assignment(position_code, organization_code, valid_from, valid_to, status);
CREATE INDEX IF NOT EXISTS idx_position_assignment_user ON position_assignment(user_id, valid_from, valid_to, status);
CREATE INDEX IF NOT EXISTS idx_business_assignee_effective ON business_assignee(assignee_user_id, business_organization_code, business_area_code, valid_from, valid_to, status);
CREATE INDEX IF NOT EXISTS idx_business_assignee_area ON business_assignee(business_area_code, processing_permission, status);
CREATE INDEX IF NOT EXISTS idx_data_scope_rule_role ON data_scope_rule(role_code, data_scope_type, use_yn);

INSERT INTO code_group (group_id, group_name, description, management_department, use_yn)
SELECT 'DATA_SCOPE_TYPE','데이터 범위 유형','역할별 서버 조회조건 강제 적용 유형','시스템관리자','Y'
WHERE NOT EXISTS (SELECT 1 FROM code_group WHERE group_id='DATA_SCOPE_TYPE');
INSERT INTO detail_code (group_id, code_value, code_name, parent_code_value, sort_order, extra_attributes, valid_from, valid_to, use_yn)
SELECT 'DATA_SCOPE_TYPE','SELF','본인',NULL,1,'{}','2020-01-01',NULL,'Y' WHERE NOT EXISTS (SELECT 1 FROM detail_code WHERE group_id='DATA_SCOPE_TYPE' AND code_value='SELF');
INSERT INTO detail_code (group_id, code_value, code_name, parent_code_value, sort_order, extra_attributes, valid_from, valid_to, use_yn)
SELECT 'DATA_SCOPE_TYPE','DEPARTMENT','소속학과',NULL,2,'{}','2020-01-01',NULL,'Y' WHERE NOT EXISTS (SELECT 1 FROM detail_code WHERE group_id='DATA_SCOPE_TYPE' AND code_value='DEPARTMENT');
INSERT INTO detail_code (group_id, code_value, code_name, parent_code_value, sort_order, extra_attributes, valid_from, valid_to, use_yn)
SELECT 'DATA_SCOPE_TYPE','COLLEGE','단과대학',NULL,3,'{}','2020-01-01',NULL,'Y' WHERE NOT EXISTS (SELECT 1 FROM detail_code WHERE group_id='DATA_SCOPE_TYPE' AND code_value='COLLEGE');
INSERT INTO detail_code (group_id, code_value, code_name, parent_code_value, sort_order, extra_attributes, valid_from, valid_to, use_yn)
SELECT 'DATA_SCOPE_TYPE','BUSINESS_AREA','담당업무',NULL,4,'{}','2020-01-01',NULL,'Y' WHERE NOT EXISTS (SELECT 1 FROM detail_code WHERE group_id='DATA_SCOPE_TYPE' AND code_value='BUSINESS_AREA');
INSERT INTO detail_code (group_id, code_value, code_name, parent_code_value, sort_order, extra_attributes, valid_from, valid_to, use_yn)
SELECT 'DATA_SCOPE_TYPE','ALL','전체',NULL,5,'{}','2020-01-01',NULL,'Y' WHERE NOT EXISTS (SELECT 1 FROM detail_code WHERE group_id='DATA_SCOPE_TYPE' AND code_value='ALL');

INSERT INTO position_assignment (assignment_id, position_code, position_name, user_id, organization_code, valid_from, valid_to, status)
SELECT 'PA-SEED-CHAIR','DEPT_CHAIR','학과장','U-002','DEPT-CS','2026-01-01','2026-12-31','ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM position_assignment WHERE assignment_id='PA-SEED-CHAIR');
INSERT INTO business_assignee (assignee_id, business_organization_code, assignee_user_id, business_area_code, data_scope, processing_permission, valid_from, valid_to, status)
SELECT 'BA-SEED-EVAL','COL-EDU','U-001','EVALUATION','COL-EDU',TRUE,'2026-01-01','2026-12-31','ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM business_assignee WHERE assignee_id='BA-SEED-EVAL');
INSERT INTO data_scope_rule (rule_id, role_code, data_scope_type, organization_code, business_area_code, use_yn)
SELECT 'DS-R01-SELF','R01','SELF',NULL,NULL,'Y' WHERE NOT EXISTS (SELECT 1 FROM data_scope_rule WHERE rule_id='DS-R01-SELF');
INSERT INTO data_scope_rule (rule_id, role_code, data_scope_type, organization_code, business_area_code, use_yn)
SELECT 'DS-R03-COLLEGE','R03','COLLEGE','COL-EDU','EVALUATION','Y' WHERE NOT EXISTS (SELECT 1 FROM data_scope_rule WHERE rule_id='DS-R03-COLLEGE');
INSERT INTO data_scope_rule (rule_id, role_code, data_scope_type, organization_code, business_area_code, use_yn)
SELECT 'DS-R07-BUSINESS','R07','BUSINESS_AREA',NULL,'EVALUATION','Y' WHERE NOT EXISTS (SELECT 1 FROM data_scope_rule WHERE rule_id='DS-R07-BUSINESS');

INSERT INTO menu (menu_id, parent_menu_id, menu_level, menu_name, display_order, screen_id, url, icon, business_category, description, use_yn)
SELECT 'MENU-POSITION','MENU-USER-ORG','LEAF','보직 관리',3,'SCR-CMN-POSITION','/admin/positions','position','시스템','보직 대상자 및 유효기간 관리','Y'
WHERE NOT EXISTS (SELECT 1 FROM menu WHERE menu_id='MENU-POSITION');
INSERT INTO menu (menu_id, parent_menu_id, menu_level, menu_name, display_order, screen_id, url, icon, business_category, description, use_yn)
SELECT 'MENU-BUSINESS-ASSIGNEE','MENU-USER-ORG','LEAF','업무담당자 관리',4,'SCR-CMN-BUSINESS-ASSIGNEE','/admin/business-assignees','assignee','시스템','업무조직별 담당자·담당영역 지정','Y'
WHERE NOT EXISTS (SELECT 1 FROM menu WHERE menu_id='MENU-BUSINESS-ASSIGNEE');
INSERT INTO menu (menu_id, parent_menu_id, menu_level, menu_name, display_order, screen_id, url, icon, business_category, description, use_yn)
SELECT 'MENU-DATA-SCOPE','MENU-ROLE-AUTH','LEAF','데이터 범위 권한',4,'SCR-CMN-DATA-SCOPE','/admin/data-scope-rules','scope','시스템','역할별 데이터 범위 규칙 설정','Y'
WHERE NOT EXISTS (SELECT 1 FROM menu WHERE menu_id='MENU-DATA-SCOPE');

INSERT INTO menu_permission (permission_id, target_type, target_id, menu_id, allowed, use_yn)
SELECT 'PERM-R09-' || menu_id, 'ROLE', 'R09', menu_id, TRUE, 'Y'
FROM menu m
WHERE menu_id IN ('MENU-POSITION','MENU-BUSINESS-ASSIGNEE','MENU-DATA-SCOPE')
  AND NOT EXISTS (SELECT 1 FROM menu_permission p WHERE p.target_type='ROLE' AND p.target_id='R09' AND p.menu_id=m.menu_id);
