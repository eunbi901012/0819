CREATE TABLE IF NOT EXISTS organization (
  organization_code VARCHAR(64) PRIMARY KEY,
  organization_name VARCHAR(200) NOT NULL,
  organization_type VARCHAR(40) NOT NULL CHECK (organization_type IN ('UNIVERSITY','GRADUATE_SCHOOL','COLLEGE','DEPARTMENT','OFFICE')),
  parent_organization_code VARCHAR(64) REFERENCES organization(organization_code),
  effective_start_date DATE NOT NULL,
  effective_end_date DATE,
  use_yn CHAR(1) NOT NULL CHECK (use_yn IN ('Y','N')),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE organization IS '대학·대학원·단과대학·학과·부서 조직 기준정보와 상하위 관계의 현재 상태를 관리한다.';
COMMENT ON COLUMN organization.organization_type IS 'UNIVERSITY:대학|GRADUATE_SCHOOL:대학원|COLLEGE:단과대학|DEPARTMENT:학과|OFFICE:부서';
COMMENT ON COLUMN organization.use_yn IS 'Y:사용|N:미사용';

CREATE TABLE IF NOT EXISTS korus_staff_snapshot (
  staff_no VARCHAR(64) PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  organization_code VARCHAR(64) NOT NULL REFERENCES organization(organization_code),
  position VARCHAR(100),
  rank VARCHAR(100),
  employment_status VARCHAR(40),
  retirement_date DATE,
  last_synced_at TIMESTAMP NOT NULL,
  status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE','RETIRED')),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE korus_staff_snapshot IS 'KORUS 교직원 원천 정보를 로컬 조회 전용 Mock snapshot으로 보관한다.';
COMMENT ON COLUMN korus_staff_snapshot.status IS 'ACTIVE:재직|RETIRED:퇴직';

CREATE TABLE IF NOT EXISTS user_account (
  user_id VARCHAR(64) PRIMARY KEY,
  login_id VARCHAR(100) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  korus_staff_id VARCHAR(64) REFERENCES korus_staff_snapshot(staff_no),
  system_enabled BOOLEAN NOT NULL,
  status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE','DISABLED')),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE user_account IS '내부 로그인 계정과 시스템 사용여부를 관리한다.';
COMMENT ON COLUMN user_account.status IS 'ACTIVE:활성|DISABLED:비활성';

CREATE TABLE IF NOT EXISTS role (
  role_code VARCHAR(10) PRIMARY KEY,
  role_name VARCHAR(100) NOT NULL,
  purpose VARCHAR(500) NOT NULL,
  assignment_criteria VARCHAR(500),
  default_data_scope VARCHAR(100),
  use_yn CHAR(1) NOT NULL CHECK (use_yn IN ('Y','N')),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE role IS 'R01~R09 업무 역할과 부여 기준, 기본 데이터 범위를 관리한다.';
COMMENT ON COLUMN role.role_code IS 'R01:교원|R02:학과장|R03:단과대학원행정실|R04:교수지원과|R05:산학협력단|R06:입학인재관리과|R07:실적부서|R08:점수산출감사자|R09:시스템관리자';
COMMENT ON COLUMN role.use_yn IS 'Y:사용|N:미사용';

CREATE TABLE IF NOT EXISTS organization_user_mapping (
  mapping_id VARCHAR(64) PRIMARY KEY,
  organization_code VARCHAR(64) NOT NULL REFERENCES organization(organization_code),
  user_id VARCHAR(64) NOT NULL REFERENCES user_account(user_id),
  position VARCHAR(100),
  use_yn CHAR(1) NOT NULL CHECK (use_yn IN ('Y','N')),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE organization_user_mapping IS '사용자의 조직 소속과 보직 매핑을 관리한다.';
COMMENT ON COLUMN organization_user_mapping.use_yn IS 'Y:사용|N:미사용';

CREATE TABLE IF NOT EXISTS user_role_assignment (
  assignment_id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL REFERENCES user_account(user_id),
  role_code VARCHAR(10) NOT NULL REFERENCES role(role_code),
  assignment_type VARCHAR(30) NOT NULL CHECK (assignment_type IN ('POSITION_BASED','MANUAL')),
  valid_from DATE NOT NULL,
  valid_to DATE,
  approver_user_id VARCHAR(64) REFERENCES user_account(user_id),
  status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE','REVOKED')),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE user_role_assignment IS '사용자별 역할 부여, 유효기간, 승인자와 회수 상태를 관리한다.';
COMMENT ON COLUMN user_role_assignment.assignment_type IS 'POSITION_BASED:보직기반|MANUAL:수동';
COMMENT ON COLUMN user_role_assignment.status IS 'ACTIVE:활성|REVOKED:회수';

CREATE TABLE IF NOT EXISTS menu (
  menu_id VARCHAR(64) PRIMARY KEY,
  parent_menu_id VARCHAR(64) REFERENCES menu(menu_id),
  menu_level VARCHAR(20) NOT NULL CHECK (menu_level IN ('TOP','MIDDLE','LEAF')),
  menu_name VARCHAR(100) NOT NULL,
  display_order INTEGER NOT NULL,
  screen_id VARCHAR(100),
  url VARCHAR(255),
  icon VARCHAR(100),
  business_category VARCHAR(100),
  description VARCHAR(500),
  use_yn CHAR(1) NOT NULL CHECK (use_yn IN ('Y','N')),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE menu IS '시스템 관리 메뉴 계층과 실행 화면 연결 정보를 관리한다.';
COMMENT ON COLUMN menu.menu_level IS 'TOP:대메뉴|MIDDLE:중메뉴|LEAF:소메뉴';
COMMENT ON COLUMN menu.use_yn IS 'Y:사용|N:미사용';

CREATE TABLE IF NOT EXISTS menu_permission (
  permission_id VARCHAR(64) PRIMARY KEY,
  target_type VARCHAR(10) NOT NULL CHECK (target_type IN ('ROLE','ORG','USER')),
  target_id VARCHAR(64) NOT NULL,
  menu_id VARCHAR(64) NOT NULL REFERENCES menu(menu_id),
  allowed BOOLEAN NOT NULL,
  use_yn CHAR(1) NOT NULL CHECK (use_yn IN ('Y','N')),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (target_type, target_id, menu_id)
);
COMMENT ON TABLE menu_permission IS '역할·조직·사용자 단위 메뉴 접근 허용 여부를 관리한다.';
COMMENT ON COLUMN menu_permission.target_type IS 'ROLE:역할|ORG:조직|USER:사용자';
COMMENT ON COLUMN menu_permission.target_id IS 'role.role_code 또는 organization.organization_code 또는 user_account.user_id 참조 의도 (다형 FK 미선언)';
COMMENT ON COLUMN menu_permission.use_yn IS 'Y:사용|N:미사용';

CREATE TABLE IF NOT EXISTS code_group (
  group_id VARCHAR(64) PRIMARY KEY,
  group_name VARCHAR(100) NOT NULL,
  description VARCHAR(500),
  management_department VARCHAR(100),
  use_yn CHAR(1) NOT NULL CHECK (use_yn IN ('Y','N')),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE code_group IS '공통코드 그룹의 명칭, 설명, 관리부서와 사용여부를 관리한다.';
COMMENT ON COLUMN code_group.use_yn IS 'Y:사용|N:미사용';

CREATE TABLE IF NOT EXISTS detail_code (
  group_id VARCHAR(64) NOT NULL REFERENCES code_group(group_id),
  code_value VARCHAR(64) NOT NULL,
  code_name VARCHAR(100) NOT NULL,
  parent_code_value VARCHAR(64),
  sort_order INTEGER NOT NULL,
  extra_attributes TEXT,
  valid_from DATE,
  valid_to DATE,
  use_yn CHAR(1) NOT NULL CHECK (use_yn IN ('Y','N')),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (group_id, code_value)
);
COMMENT ON TABLE detail_code IS '코드그룹별 상세코드와 계층·연계 속성, 유효기간을 관리한다.';
COMMENT ON COLUMN detail_code.parent_code_value IS 'detail_code.code_value 참조 의도 (동일 group_id 내 논리 참조)';
COMMENT ON COLUMN detail_code.extra_attributes IS 'CodeService.create/update 시 API 요청 JSON을 문자열로 저장하고 조회 응답에서 반환한다';
COMMENT ON COLUMN detail_code.use_yn IS 'Y:사용|N:미사용';

CREATE TABLE IF NOT EXISTS session (
  session_id VARCHAR(128) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL REFERENCES user_account(user_id),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMP NOT NULL,
  status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE','EXPIRED'))
);
COMMENT ON TABLE session IS '로그인 세션 쿠키와 만료 상태를 관리한다.';
COMMENT ON COLUMN session.status IS 'ACTIVE:활성|EXPIRED:만료';

CREATE TABLE IF NOT EXISTS change_history (
  history_id VARCHAR(64) PRIMARY KEY,
  entity_name VARCHAR(100) NOT NULL,
  entity_id VARCHAR(128) NOT NULL,
  before_value TEXT,
  after_value TEXT,
  reason VARCHAR(500),
  actor_user_id VARCHAR(64) NOT NULL REFERENCES user_account(user_id),
  changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE change_history IS '등록·수정·회수성 처리의 변경 전후 값, 처리자, 처리일시, 사유를 추적한다.';
COMMENT ON COLUMN change_history.before_value IS 'CommonService 변경 처리 직전 값을 JSON 문자열로 기록';
COMMENT ON COLUMN change_history.after_value IS 'CommonService 변경 처리 직후 값을 JSON 문자열로 기록';

CREATE INDEX IF NOT EXISTS idx_user_account_login_id ON user_account(login_id);
CREATE INDEX IF NOT EXISTS idx_staff_filter ON korus_staff_snapshot(name, organization_code, rank, employment_status);
CREATE INDEX IF NOT EXISTS idx_org_parent ON organization(parent_organization_code);
CREATE INDEX IF NOT EXISTS idx_user_role_user ON user_role_assignment(user_id, role_code, status);
CREATE INDEX IF NOT EXISTS idx_menu_parent ON menu(parent_menu_id, display_order);
CREATE INDEX IF NOT EXISTS idx_menu_permission_target ON menu_permission(target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_detail_code_group ON detail_code(group_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_session_user_status ON session(user_id, status);

INSERT INTO organization (organization_code, organization_name, organization_type, parent_organization_code, effective_start_date, effective_end_date, use_yn)
SELECT 'KNUE','한국교원대학교','UNIVERSITY',NULL,'2020-01-01',NULL,'Y' WHERE NOT EXISTS (SELECT 1 FROM organization WHERE organization_code='KNUE');
INSERT INTO organization (organization_code, organization_name, organization_type, parent_organization_code, effective_start_date, effective_end_date, use_yn)
SELECT 'COL-EDU','교육과학대학','COLLEGE','KNUE','2020-01-01',NULL,'Y' WHERE NOT EXISTS (SELECT 1 FROM organization WHERE organization_code='COL-EDU');
INSERT INTO organization (organization_code, organization_name, organization_type, parent_organization_code, effective_start_date, effective_end_date, use_yn)
SELECT 'DEPT-CS','컴퓨터교육과','DEPARTMENT','COL-EDU','2020-01-01',NULL,'Y' WHERE NOT EXISTS (SELECT 1 FROM organization WHERE organization_code='DEPT-CS');

INSERT INTO korus_staff_snapshot (staff_no, name, organization_code, position, rank, employment_status, retirement_date, last_synced_at, status)
SELECT '2024001','김교수','DEPT-CS','교수','정교수','재직','2099-12-31',CURRENT_TIMESTAMP,'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM korus_staff_snapshot WHERE staff_no='2024001');
INSERT INTO korus_staff_snapshot (staff_no, name, organization_code, position, rank, employment_status, retirement_date, last_synced_at, status)
SELECT '2024002','이교수','DEPT-CS','학과장','부교수','재직',NULL,CURRENT_TIMESTAMP,'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM korus_staff_snapshot WHERE staff_no='2024002');
INSERT INTO korus_staff_snapshot (staff_no, name, organization_code, position, rank, employment_status, retirement_date, last_synced_at, status)
SELECT 'ADMIN001','관리자','KNUE','시스템관리자','직원','재직',NULL,CURRENT_TIMESTAMP,'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM korus_staff_snapshot WHERE staff_no='ADMIN001');

INSERT INTO user_account (user_id, login_id, password_hash, korus_staff_id, system_enabled, status)
SELECT 'U-ADMIN','admin','admin','ADMIN001',TRUE,'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM user_account WHERE user_id='U-ADMIN');
INSERT INTO user_account (user_id, login_id, password_hash, korus_staff_id, system_enabled, status)
SELECT 'U-001','teacher','teacher','2024001',TRUE,'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM user_account WHERE user_id='U-001');
INSERT INTO user_account (user_id, login_id, password_hash, korus_staff_id, system_enabled, status)
SELECT 'U-002','chair','chair','2024002',TRUE,'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM user_account WHERE user_id='U-002');

INSERT INTO role (role_code, role_name, purpose, assignment_criteria, default_data_scope, use_yn)
SELECT 'R01','교원','본인 관련 업무를 수행하는 일반 사용자 역할','교원 인사정보 기준','SELF','Y' WHERE NOT EXISTS (SELECT 1 FROM role WHERE role_code='R01');
INSERT INTO role (role_code, role_name, purpose, assignment_criteria, default_data_scope, use_yn)
SELECT 'R02','학과장','소속 학과 교원 관련 업무를 확인하는 역할','학과장 보직 기준','DEPARTMENT','Y' WHERE NOT EXISTS (SELECT 1 FROM role WHERE role_code='R02');
INSERT INTO role (role_code, role_name, purpose, assignment_criteria, default_data_scope, use_yn)
SELECT 'R03','단과대학(원) 행정실','단과대학 또는 대학원 행정 처리 역할','행정실 발령 기준','COLLEGE','Y' WHERE NOT EXISTS (SELECT 1 FROM role WHERE role_code='R03');
INSERT INTO role (role_code, role_name, purpose, assignment_criteria, default_data_scope, use_yn)
SELECT 'R04','교수지원과','기준정보와 평가 관련 행정 관리 역할','교수지원과 담당자','ALL','Y' WHERE NOT EXISTS (SELECT 1 FROM role WHERE role_code='R04');
INSERT INTO role (role_code, role_name, purpose, assignment_criteria, default_data_scope, use_yn)
SELECT 'R05','산학협력단','연구비·간접비·지식재산 관련 자료 관리 역할','산학협력단 담당자','RESEARCH','Y' WHERE NOT EXISTS (SELECT 1 FROM role WHERE role_code='R05');
INSERT INTO role (role_code, role_name, purpose, assignment_criteria, default_data_scope, use_yn)
SELECT 'R06','입학인재관리과','입학·취업률 관련 자료 관리 역할','입학인재관리과 담당자','ADMISSION','Y' WHERE NOT EXISTS (SELECT 1 FROM role WHERE role_code='R06');
INSERT INTO role (role_code, role_name, purpose, assignment_criteria, default_data_scope, use_yn)
SELECT 'R07','실적부서','담당 실적 자료 관리 역할','실적부서 담당자','OWN_DEPARTMENT','Y' WHERE NOT EXISTS (SELECT 1 FROM role WHERE role_code='R07');
INSERT INTO role (role_code, role_name, purpose, assignment_criteria, default_data_scope, use_yn)
SELECT 'R08','점수산출 감사자','산출 과정과 근거를 조회하는 감사 역할','감사자 지정','AUDIT','Y' WHERE NOT EXISTS (SELECT 1 FROM role WHERE role_code='R08');
INSERT INTO role (role_code, role_name, purpose, assignment_criteria, default_data_scope, use_yn)
SELECT 'R09','시스템관리자','사용자·조직·메뉴·권한·코드 관리를 수행하는 관리자 역할','시스템 관리자 지정','ALL','Y' WHERE NOT EXISTS (SELECT 1 FROM role WHERE role_code='R09');

INSERT INTO organization_user_mapping (mapping_id, organization_code, user_id, position, use_yn)
SELECT 'MAP-001','DEPT-CS','U-001','교수','Y' WHERE NOT EXISTS (SELECT 1 FROM organization_user_mapping WHERE mapping_id='MAP-001');
INSERT INTO organization_user_mapping (mapping_id, organization_code, user_id, position, use_yn)
SELECT 'MAP-002','KNUE','U-ADMIN','시스템관리자','Y' WHERE NOT EXISTS (SELECT 1 FROM organization_user_mapping WHERE mapping_id='MAP-002');

INSERT INTO user_role_assignment (assignment_id, user_id, role_code, assignment_type, valid_from, valid_to, approver_user_id, status)
SELECT 'URA-ADMIN','U-ADMIN','R09','MANUAL','2020-01-01',NULL,'U-ADMIN','ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM user_role_assignment WHERE assignment_id='URA-ADMIN');
INSERT INTO user_role_assignment (assignment_id, user_id, role_code, assignment_type, valid_from, valid_to, approver_user_id, status)
SELECT 'URA-TEACHER','U-001','R01','POSITION_BASED','2020-01-01',NULL,'U-ADMIN','ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM user_role_assignment WHERE assignment_id='URA-TEACHER');

INSERT INTO menu (menu_id, parent_menu_id, menu_level, menu_name, display_order, screen_id, url, icon, business_category, description, use_yn)
SELECT 'MENU-SYS',NULL,'TOP','시스템 관리',1,NULL,NULL,'settings','시스템','시스템 관리 대메뉴','Y' WHERE NOT EXISTS (SELECT 1 FROM menu WHERE menu_id='MENU-SYS');
INSERT INTO menu (menu_id, parent_menu_id, menu_level, menu_name, display_order, screen_id, url, icon, business_category, description, use_yn)
SELECT 'MENU-USER-ORG','MENU-SYS','MIDDLE','사용자·조직 관리',1,NULL,NULL,'users','시스템','사용자·조직 관리','Y' WHERE NOT EXISTS (SELECT 1 FROM menu WHERE menu_id='MENU-USER-ORG');
INSERT INTO menu (menu_id, parent_menu_id, menu_level, menu_name, display_order, screen_id, url, icon, business_category, description, use_yn)
SELECT 'MENU-ROLE-AUTH','MENU-SYS','MIDDLE','역할·권한 관리',2,NULL,NULL,'shield','시스템','역할·권한 관리','Y' WHERE NOT EXISTS (SELECT 1 FROM menu WHERE menu_id='MENU-ROLE-AUTH');
INSERT INTO menu (menu_id, parent_menu_id, menu_level, menu_name, display_order, screen_id, url, icon, business_category, description, use_yn)
SELECT 'MENU-MANAGE','MENU-SYS','MIDDLE','메뉴 관리',3,NULL,NULL,'menu','시스템','메뉴 관리','Y' WHERE NOT EXISTS (SELECT 1 FROM menu WHERE menu_id='MENU-MANAGE');
INSERT INTO menu (menu_id, parent_menu_id, menu_level, menu_name, display_order, screen_id, url, icon, business_category, description, use_yn)
SELECT 'MENU-CODE','MENU-SYS','MIDDLE','공통코드 관리',4,NULL,NULL,'code','시스템','공통코드 관리','Y' WHERE NOT EXISTS (SELECT 1 FROM menu WHERE menu_id='MENU-CODE');
INSERT INTO menu (menu_id, parent_menu_id, menu_level, menu_name, display_order, screen_id, url, icon, business_category, description, use_yn)
SELECT 'MENU-USER','MENU-USER-ORG','LEAF','사용자 관리',1,'SCR-CMN-USER','/admin/users','user','시스템','사용자 관리','Y' WHERE NOT EXISTS (SELECT 1 FROM menu WHERE menu_id='MENU-USER');
INSERT INTO menu (menu_id, parent_menu_id, menu_level, menu_name, display_order, screen_id, url, icon, business_category, description, use_yn)
SELECT 'MENU-ORG','MENU-USER-ORG','LEAF','조직 관리',2,'SCR-CMN-ORG','/admin/organizations','org','시스템','조직 관리','Y' WHERE NOT EXISTS (SELECT 1 FROM menu WHERE menu_id='MENU-ORG');
INSERT INTO menu (menu_id, parent_menu_id, menu_level, menu_name, display_order, screen_id, url, icon, business_category, description, use_yn)
SELECT 'MENU-ROLE','MENU-ROLE-AUTH','LEAF','역할 관리',1,'SCR-CMN-ROLE','/admin/roles','role','시스템','역할 관리','Y' WHERE NOT EXISTS (SELECT 1 FROM menu WHERE menu_id='MENU-ROLE');
INSERT INTO menu (menu_id, parent_menu_id, menu_level, menu_name, display_order, screen_id, url, icon, business_category, description, use_yn)
SELECT 'MENU-USER-ROLE','MENU-ROLE-AUTH','LEAF','사용자 역할 관리',2,'SCR-CMN-USER-ROLE','/admin/user-roles','role-user','시스템','사용자 역할 관리','Y' WHERE NOT EXISTS (SELECT 1 FROM menu WHERE menu_id='MENU-USER-ROLE');
INSERT INTO menu (menu_id, parent_menu_id, menu_level, menu_name, display_order, screen_id, url, icon, business_category, description, use_yn)
SELECT 'MENU-PERM','MENU-ROLE-AUTH','LEAF','메뉴 권한 관리',3,'SCR-CMN-MENU-PERM','/admin/menu-permissions','permission','시스템','메뉴 권한 관리','Y' WHERE NOT EXISTS (SELECT 1 FROM menu WHERE menu_id='MENU-PERM');
INSERT INTO menu (menu_id, parent_menu_id, menu_level, menu_name, display_order, screen_id, url, icon, business_category, description, use_yn)
SELECT 'MENU-STRUCT','MENU-MANAGE','LEAF','메뉴 구조 관리',1,'SCR-CMN-MENU-STRUCT','/admin/menu-structure','tree','시스템','메뉴 구조 관리','Y' WHERE NOT EXISTS (SELECT 1 FROM menu WHERE menu_id='MENU-STRUCT');
INSERT INTO menu (menu_id, parent_menu_id, menu_level, menu_name, display_order, screen_id, url, icon, business_category, description, use_yn)
SELECT 'MENU-INFO','MENU-MANAGE','LEAF','메뉴 정보 관리',2,'SCR-CMN-MENU-INFO','/admin/menu-info','info','시스템','메뉴 정보 관리','Y' WHERE NOT EXISTS (SELECT 1 FROM menu WHERE menu_id='MENU-INFO');
INSERT INTO menu (menu_id, parent_menu_id, menu_level, menu_name, display_order, screen_id, url, icon, business_category, description, use_yn)
SELECT 'MENU-CODE-GROUP','MENU-CODE','LEAF','코드그룹 관리',1,'SCR-CMN-CODE-GROUP','/admin/code-groups','folder','시스템','코드그룹 관리','Y' WHERE NOT EXISTS (SELECT 1 FROM menu WHERE menu_id='MENU-CODE-GROUP');
INSERT INTO menu (menu_id, parent_menu_id, menu_level, menu_name, display_order, screen_id, url, icon, business_category, description, use_yn)
SELECT 'MENU-CODE-DETAIL','MENU-CODE','LEAF','상세코드 관리',2,'SCR-CMN-CODE-DETAIL','/admin/code-groups/COMMON_YN/codes','list','시스템','상세코드 관리','Y' WHERE NOT EXISTS (SELECT 1 FROM menu WHERE menu_id='MENU-CODE-DETAIL');

INSERT INTO menu_permission (permission_id, target_type, target_id, menu_id, allowed, use_yn)
SELECT 'PERM-R09-' || menu_id, 'ROLE', 'R09', menu_id, TRUE, 'Y' FROM menu m WHERE menu_level='LEAF' AND NOT EXISTS (SELECT 1 FROM menu_permission p WHERE p.target_type='ROLE' AND p.target_id='R09' AND p.menu_id=m.menu_id);

INSERT INTO code_group (group_id, group_name, description, management_department, use_yn)
SELECT 'COMMON_YN','사용여부','Y/N 공통 사용 여부','시스템관리자','Y' WHERE NOT EXISTS (SELECT 1 FROM code_group WHERE group_id='COMMON_YN');
INSERT INTO code_group (group_id, group_name, description, management_department, use_yn)
SELECT 'ROLE_ASSIGNMENT_TYPE','역할부여구분','보직 기반과 수동 부여 구분','시스템관리자','Y' WHERE NOT EXISTS (SELECT 1 FROM code_group WHERE group_id='ROLE_ASSIGNMENT_TYPE');

INSERT INTO detail_code (group_id, code_value, code_name, parent_code_value, sort_order, extra_attributes, valid_from, valid_to, use_yn)
SELECT 'COMMON_YN','Y','사용',NULL,1,'{}','2020-01-01',NULL,'Y' WHERE NOT EXISTS (SELECT 1 FROM detail_code WHERE group_id='COMMON_YN' AND code_value='Y');
INSERT INTO detail_code (group_id, code_value, code_name, parent_code_value, sort_order, extra_attributes, valid_from, valid_to, use_yn)
SELECT 'COMMON_YN','N','미사용',NULL,2,'{}','2020-01-01',NULL,'Y' WHERE NOT EXISTS (SELECT 1 FROM detail_code WHERE group_id='COMMON_YN' AND code_value='N');
INSERT INTO detail_code (group_id, code_value, code_name, parent_code_value, sort_order, extra_attributes, valid_from, valid_to, use_yn)
SELECT 'ROLE_ASSIGNMENT_TYPE','POSITION_BASED','보직기반',NULL,1,'{}','2020-01-01',NULL,'Y' WHERE NOT EXISTS (SELECT 1 FROM detail_code WHERE group_id='ROLE_ASSIGNMENT_TYPE' AND code_value='POSITION_BASED');
INSERT INTO detail_code (group_id, code_value, code_name, parent_code_value, sort_order, extra_attributes, valid_from, valid_to, use_yn)
SELECT 'ROLE_ASSIGNMENT_TYPE','MANUAL','수동',NULL,2,'{}','2020-01-01',NULL,'Y' WHERE NOT EXISTS (SELECT 1 FROM detail_code WHERE group_id='ROLE_ASSIGNMENT_TYPE' AND code_value='MANUAL');
