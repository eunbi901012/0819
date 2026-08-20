CREATE TABLE IF NOT EXISTS organization (
    org_code varchar(40) PRIMARY KEY,
    org_name varchar(120) NOT NULL,
    org_type varchar(20) NOT NULL,
    use_yn boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE organization IS '대학·대학원·단과대학·학과·부서 조직 기준정보.';
COMMENT ON COLUMN organization.org_type IS '대학:대학|대학원:대학원|단과대학:단과대학|학과:학과|부서:부서';

CREATE TABLE IF NOT EXISTS korus_staff_snapshot (
    employee_no varchar(40) PRIMARY KEY,
    name varchar(80) NOT NULL,
    organization_code varchar(40) NOT NULL REFERENCES organization(org_code),
    position varchar(80),
    rank varchar(80),
    employment_status varchar(20) NOT NULL,
    retirement_date date,
    last_synced_at timestamptz NOT NULL
);
COMMENT ON TABLE korus_staff_snapshot IS 'KORUS 원천 교직원 정보를 읽기 전용 Mock snapshot으로 보관한다.';
COMMENT ON COLUMN korus_staff_snapshot.employment_status IS '재직:재직|퇴직:퇴직';

CREATE TABLE IF NOT EXISTS user_account (
    user_id varchar(40) PRIMARY KEY,
    employee_no varchar(40) NOT NULL REFERENCES korus_staff_snapshot(employee_no),
    login_id varchar(80) NOT NULL UNIQUE,
    password_hash varchar(128) NOT NULL,
    system_enabled boolean NOT NULL DEFAULT true,
    status varchar(20) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE user_account IS '내부 시스템 로그인 계정과 시스템 사용여부를 관리한다.';
COMMENT ON COLUMN user_account.status IS 'ACTIVE:활성|DISABLED:비활성';

CREATE TABLE IF NOT EXISTS organization_relation (
    relation_id varchar(40) PRIMARY KEY,
    org_code varchar(40) NOT NULL REFERENCES organization(org_code),
    parent_org_code varchar(40) REFERENCES organization(org_code),
    effective_start_date date NOT NULL,
    effective_end_date date,
    status varchar(20) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE organization_relation IS '조직의 상위·하위 관계와 적용기간 이력을 보존한다.';
COMMENT ON COLUMN organization_relation.status IS 'ACTIVE:활성|ENDED:종료';

CREATE TABLE IF NOT EXISTS organization_user_mapping (
    mapping_id varchar(40) PRIMARY KEY,
    employee_no varchar(40) NOT NULL REFERENCES korus_staff_snapshot(employee_no),
    org_code varchar(40) NOT NULL REFERENCES organization(org_code),
    position varchar(80),
    use_yn boolean NOT NULL DEFAULT true
);
COMMENT ON TABLE organization_user_mapping IS '교직원의 조직 및 보직 매핑 정보를 조회용으로 제공한다.';

CREATE TABLE IF NOT EXISTS role (
    role_code varchar(3) PRIMARY KEY,
    role_name varchar(120) NOT NULL,
    purpose text NOT NULL,
    grant_criteria text,
    data_scope_default text,
    use_yn boolean NOT NULL DEFAULT true,
    updated_at timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE role IS 'R01~R09 업무 역할 기준정보와 부여 기준을 관리한다.';
COMMENT ON COLUMN role.role_code IS 'R01:교원|R02:학과장|R03:단과대학(원) 행정실|R04:교수지원과|R05:산학협력단|R06:입학인재관리과|R07:실적부서|R08:점수산출 감사자|R09:시스템관리자';

CREATE TABLE IF NOT EXISTS user_role_assignment (
    assignment_id varchar(40) PRIMARY KEY,
    user_id varchar(40) NOT NULL REFERENCES user_account(user_id),
    role_code varchar(3) NOT NULL REFERENCES role(role_code),
    assignment_type varchar(20) NOT NULL,
    effective_start_date date NOT NULL,
    effective_end_date date,
    approver_user_id varchar(40) REFERENCES user_account(user_id),
    status varchar(20) NOT NULL
);
COMMENT ON TABLE user_role_assignment IS '사용자별 역할 부여·회수, 승인자와 유효기간을 기록한다.';
COMMENT ON COLUMN user_role_assignment.assignment_type IS 'POSITION_BASED:보직기반|MANUAL:수동부여';
COMMENT ON COLUMN user_role_assignment.status IS 'ACTIVE:활성|REVOKED:회수|EXPIRED:만료';

CREATE TABLE IF NOT EXISTS menu (
    menu_id varchar(40) PRIMARY KEY,
    parent_menu_id varchar(40) REFERENCES menu(menu_id),
    menu_name varchar(120) NOT NULL,
    menu_level integer NOT NULL,
    sort_order integer NOT NULL,
    screen_id varchar(80),
    url varchar(200),
    icon varchar(80),
    business_category varchar(80),
    description text,
    use_yn boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE menu IS '시스템 관리 대·중·소 메뉴 구조와 실행 화면 연결 정보를 관리한다.';

CREATE TABLE IF NOT EXISTS menu_permission (
    permission_id varchar(40) PRIMARY KEY,
    target_type varchar(20) NOT NULL,
    target_id varchar(80) NOT NULL,
    menu_id varchar(40) NOT NULL REFERENCES menu(menu_id),
    allow_access boolean NOT NULL DEFAULT false,
    use_yn boolean NOT NULL DEFAULT true
);
COMMENT ON TABLE menu_permission IS '역할·조직·사용자 대상별 메뉴 접근 권한을 관리한다.';
COMMENT ON COLUMN menu_permission.target_type IS 'ROLE:역할|ORGANIZATION:조직|USER:사용자';
COMMENT ON COLUMN menu_permission.target_id IS 'role.role_code, organization.org_code 또는 user_account.user_id 참조 의도 (FK 미선언)';

CREATE TABLE IF NOT EXISTS code_group (
    group_id varchar(80) PRIMARY KEY,
    group_name varchar(120) NOT NULL,
    description text,
    managing_department varchar(120),
    use_yn boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE code_group IS '공통코드 그룹 기준정보와 관리부서를 관리한다.';

CREATE TABLE IF NOT EXISTS code_detail (
    detail_id varchar(40) PRIMARY KEY,
    group_id varchar(80) NOT NULL REFERENCES code_group(group_id),
    code_value varchar(80) NOT NULL,
    code_name varchar(120) NOT NULL,
    parent_code_value varchar(80),
    sort_order integer NOT NULL DEFAULT 0,
    extra_attributes jsonb,
    use_yn boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(group_id, code_value)
);
COMMENT ON TABLE code_detail IS '그룹별 상세코드, 상위코드 계층과 연계 속성을 관리한다.';
COMMENT ON COLUMN code_detail.extra_attributes IS 'CodeDetailController create/update 시 애플리케이션에서 JSON 속성으로 갱신';

CREATE TABLE IF NOT EXISTS session (
    session_id varchar(80) PRIMARY KEY,
    user_id varchar(40) NOT NULL REFERENCES user_account(user_id),
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    status varchar(20) NOT NULL
);
COMMENT ON TABLE session IS 'HttpOnly 세션 쿠키에 대응하는 서버 세션 상태를 관리한다.';
COMMENT ON COLUMN session.status IS 'ACTIVE:활성|EXPIRED:만료';

CREATE INDEX IF NOT EXISTS idx_korus_staff_snapshot_org ON korus_staff_snapshot(organization_code);
CREATE INDEX IF NOT EXISTS idx_user_role_assignment_user ON user_role_assignment(user_id);
CREATE INDEX IF NOT EXISTS idx_menu_parent ON menu(parent_menu_id);
CREATE INDEX IF NOT EXISTS idx_menu_permission_target ON menu_permission(target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_code_detail_group ON code_detail(group_id);
CREATE INDEX IF NOT EXISTS idx_session_user_status ON session(user_id, status);

INSERT INTO organization(org_code, org_name, org_type, use_yn) VALUES
('KNUE','한국교원대학교','대학',true),('EDU','교육학부','단과대학',true),('CSE','컴퓨터교육과','학과',true),('ADMIN','교수지원과','부서',true)
ON CONFLICT (org_code) DO UPDATE SET org_name=EXCLUDED.org_name, org_type=EXCLUDED.org_type, use_yn=EXCLUDED.use_yn;
INSERT INTO organization_relation(relation_id, org_code, parent_org_code, effective_start_date, effective_end_date, status) VALUES
('REL-KNUE','KNUE',null,'2026-01-01',null,'ACTIVE'),('REL-EDU','EDU','KNUE','2026-01-01',null,'ACTIVE'),('REL-CSE','CSE','EDU','2026-01-01',null,'ACTIVE'),('REL-ADMIN','ADMIN','KNUE','2026-01-01',null,'ACTIVE')
ON CONFLICT (relation_id) DO UPDATE SET parent_org_code=EXCLUDED.parent_org_code, effective_start_date=EXCLUDED.effective_start_date, effective_end_date=EXCLUDED.effective_end_date, status=EXCLUDED.status;
INSERT INTO korus_staff_snapshot(employee_no, name, organization_code, position, rank, employment_status, retirement_date, last_synced_at) VALUES
('ADMIN-001','시스템관리자','ADMIN','담당자','행정직','재직',null,now()),('PROF-001','홍길동','CSE','학과장','교수','재직',null,now())
ON CONFLICT (employee_no) DO UPDATE SET name=EXCLUDED.name, organization_code=EXCLUDED.organization_code, position=EXCLUDED.position, rank=EXCLUDED.rank, employment_status=EXCLUDED.employment_status, retirement_date=EXCLUDED.retirement_date, last_synced_at=EXCLUDED.last_synced_at;
INSERT INTO user_account(user_id, employee_no, login_id, password_hash, system_enabled, status) VALUES
('admin-user','ADMIN-001','admin','8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918',true,'ACTIVE'),('prof-user','PROF-001','professor','8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918',true,'ACTIVE')
ON CONFLICT (user_id) DO UPDATE SET employee_no=EXCLUDED.employee_no, login_id=EXCLUDED.login_id, password_hash=EXCLUDED.password_hash, system_enabled=EXCLUDED.system_enabled, status=EXCLUDED.status;
INSERT INTO role(role_code, role_name, purpose, grant_criteria, data_scope_default, use_yn) VALUES
('R01','교원','본인 관련 업무를 수행하는 일반 사용자 역할','재직 교원','본인',true),('R02','학과장','소속 학과 교원 관련 업무를 확인하는 역할','학과장 보직','소속 학과',true),('R03','단과대학(원) 행정실','단과대학 또는 대학원 행정 처리 역할','행정실 담당자','소속 대학',true),('R04','교수지원과','기준정보와 평가 관련 행정 관리 역할','교수지원과 담당자','전체',true),('R05','산학협력단','연구비·간접비·지식재산 관련 자료 관리 역할','산학협력단 담당자','연구 데이터',true),('R06','입학인재관리과','입학·취업률 관련 자료 관리 역할','입학인재관리과 담당자','입학/취업 데이터',true),('R07','실적부서','담당 실적 자료 관리 역할','실적 담당부서','담당 실적',true),('R08','점수산출 감사자','산출 과정과 근거를 조회하는 감사 역할','감사 권한 승인자','감사 대상',true),('R09','시스템관리자','사용자·조직·메뉴·권한·코드 관리를 수행하는 관리자 역할','시스템 관리자 지정','전체',true)
ON CONFLICT (role_code) DO UPDATE SET role_name=EXCLUDED.role_name, purpose=EXCLUDED.purpose, grant_criteria=EXCLUDED.grant_criteria, data_scope_default=EXCLUDED.data_scope_default, use_yn=EXCLUDED.use_yn;
INSERT INTO user_role_assignment(assignment_id,user_id,role_code,assignment_type,effective_start_date,effective_end_date,approver_user_id,status) VALUES
('URA-ADMIN-R09','admin-user','R09','MANUAL','2026-01-01',null,'admin-user','ACTIVE'),('URA-PROF-R01','prof-user','R01','POSITION_BASED','2026-01-01',null,'admin-user','ACTIVE')
ON CONFLICT (assignment_id) DO UPDATE SET role_code=EXCLUDED.role_code, assignment_type=EXCLUDED.assignment_type, status=EXCLUDED.status;
INSERT INTO organization_user_mapping(mapping_id, employee_no, org_code, position, use_yn) VALUES
('MAP-ADMIN','ADMIN-001','ADMIN','담당자',true),('MAP-PROF','PROF-001','CSE','학과장',true)
ON CONFLICT (mapping_id) DO UPDATE SET org_code=EXCLUDED.org_code, position=EXCLUDED.position, use_yn=EXCLUDED.use_yn;

INSERT INTO menu(menu_id,parent_menu_id,menu_name,menu_level,sort_order,screen_id,url,icon,business_category,description,use_yn) VALUES
('M-SYS',null,'시스템 관리',1,1,null,null,'settings','COMMON','시스템 관리 루트',true),
('M-USER-ORG','M-SYS','사용자·조직 관리',2,1,null,null,'users','COMMON','사용자와 조직 관리',true),
('M-ROLE-AUTH','M-SYS','역할·권한 관리',2,2,null,null,'shield','COMMON','역할과 권한 관리',true),
('M-MENU','M-SYS','메뉴 관리',2,3,null,null,'menu','COMMON','메뉴 관리',true),
('M-CODE','M-SYS','공통코드 관리',2,4,null,null,'code','COMMON','공통코드 관리',true),
('M-USERS','M-USER-ORG','사용자 관리',3,1,'SCR-USERS','/admin/users','user','COMMON','사용자 관리 화면',true),
('M-ORGS','M-USER-ORG','조직 관리',3,2,'SCR-ORGS','/admin/organizations','org','COMMON','조직 관리 화면',true),
('M-ROLES','M-ROLE-AUTH','역할 관리',3,1,'SCR-ROLES','/admin/roles','roles','COMMON','역할 관리 화면',true),
('M-USER-ROLES','M-ROLE-AUTH','사용자 역할 관리',3,2,'SCR-USER-ROLES','/admin/user-roles','assignment','COMMON','사용자 역할 관리 화면',true),
('M-MENU-PERM','M-ROLE-AUTH','메뉴 권한 관리',3,3,'SCR-MENU-PERMISSIONS','/admin/menu-permissions','permission','COMMON','메뉴 권한 관리 화면',true),
('M-MENU-STRUCT','M-MENU','메뉴 구조 관리',3,1,'SCR-MENU-STRUCTURE','/admin/menu-structure','tree','COMMON','메뉴 구조 관리 화면',true),
('M-MENU-INFO','M-MENU','메뉴 정보 관리',3,2,'SCR-MENU-INFO','/admin/menu-info','info','COMMON','메뉴 정보 관리 화면',true),
('M-CODE-GROUP','M-CODE','코드그룹 관리',3,1,'SCR-CODE-GROUPS','/admin/code-groups','group','COMMON','코드그룹 관리 화면',true),
('M-CODE-DETAIL','M-CODE','상세코드 관리',3,2,'SCR-CODE-DETAILS','/admin/code-details','detail','COMMON','상세코드 관리 화면',true)
ON CONFLICT (menu_id) DO UPDATE SET parent_menu_id=EXCLUDED.parent_menu_id, menu_name=EXCLUDED.menu_name, menu_level=EXCLUDED.menu_level, sort_order=EXCLUDED.sort_order, screen_id=EXCLUDED.screen_id, url=EXCLUDED.url, icon=EXCLUDED.icon, business_category=EXCLUDED.business_category, description=EXCLUDED.description, use_yn=EXCLUDED.use_yn;
INSERT INTO menu_permission(permission_id,target_type,target_id,menu_id,allow_access,use_yn)
SELECT 'PERM-R09-' || menu_id, 'ROLE', 'R09', menu_id, true, true FROM menu
ON CONFLICT (permission_id) DO UPDATE SET allow_access=true, use_yn=true;
INSERT INTO menu_permission(permission_id,target_type,target_id,menu_id,allow_access,use_yn)
SELECT 'PERM-R01-' || menu_id, 'ROLE', 'R01', menu_id, false, true FROM menu WHERE menu_level=3
ON CONFLICT (permission_id) DO UPDATE SET allow_access=false, use_yn=true;
INSERT INTO code_group(group_id, group_name, description, managing_department, use_yn) VALUES
('EMPLOYMENT_STATUS','재직상태','교직원 재직상태 코드','교수지원과',true),('USE_YN','사용여부','공통 사용 여부 코드','교수지원과',true)
ON CONFLICT (group_id) DO UPDATE SET group_name=EXCLUDED.group_name, description=EXCLUDED.description, managing_department=EXCLUDED.managing_department, use_yn=EXCLUDED.use_yn;
INSERT INTO code_detail(detail_id,group_id,code_value,code_name,parent_code_value,sort_order,extra_attributes,use_yn) VALUES
('CD-EMP-ACTIVE','EMPLOYMENT_STATUS','재직','재직',null,1,'{}',true),('CD-EMP-RETIRED','EMPLOYMENT_STATUS','퇴직','퇴직',null,2,'{}',true),('CD-USE-Y','USE_YN','Y','사용',null,1,'{"boolean":true}',true),('CD-USE-N','USE_YN','N','미사용',null,2,'{"boolean":false}',true)
ON CONFLICT (group_id, code_value) DO UPDATE SET code_name=EXCLUDED.code_name, sort_order=EXCLUDED.sort_order, extra_attributes=EXCLUDED.extra_attributes, use_yn=EXCLUDED.use_yn;
