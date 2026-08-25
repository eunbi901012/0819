import type React from "react";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { api, PageResult, query } from "./services/api/client";

type Row = Record<string, unknown>;
type FieldType =
  | "text"
  | "date"
  | "number"
  | "textarea"
  | "yn"
  | "boolean"
  | "roleCodes"
  | "assignmentType"
  | "targetType"
  | "json";

type FieldConfig = {
  key: string;
  label: string;
  readonly?: boolean;
  createReadonly?: boolean;
  type?: FieldType;
  required?: boolean;
  source?: "KORUS" | "LOCAL";
};

type ScreenConfig = {
  route: string;
  match?: (route: string) => boolean;
  title: string;
  eyebrow: string;
  description: string;
  menuPath: string;
  listPath: (route: string) => string;
  empty: string;
  archetype:
    | "search-list-detail"
    | "tree-editor"
    | "permission-matrix"
    | "effective-period";
  idKey?: string;
  createPath?: (route: string) => string;
  updatePath?: (row: Row, route: string) => string;
  updateMethod?: "POST" | "PATCH" | "PUT";
  fields: FieldConfig[];
  columns: { key: string; label: string; tone?: "status" | "mono" }[];
  filters: FieldConfig[];
};

type CurrentUser = {
  userId: string;
  loginId: string;
  name: string;
  roles: string[];
};

const fallbackMenuLinks = [
  ["사용자·조직 관리", "/admin/users", "사용자 관리"],
  ["사용자·조직 관리", "/admin/organizations", "조직 관리"],
  ["역할·권한 관리", "/admin/roles", "역할 관리"],
  ["역할·권한 관리", "/admin/user-roles", "사용자 역할 관리"],
  ["역할·권한 관리", "/admin/menu-permissions", "메뉴 권한 관리"],
  ["메뉴 관리", "/admin/menu-structure", "메뉴 구조 관리"],
  ["메뉴 관리", "/admin/menu-info", "메뉴 정보 관리"],
  ["공통코드 관리", "/admin/code-groups", "코드그룹 관리"],
  ["공통코드 관리", "/admin/code-groups/COMMON_YN/codes", "상세코드 관리"],
];

const roleOptions = [
  "R01",
  "R02",
  "R03",
  "R04",
  "R05",
  "R06",
  "R07",
  "R08",
  "R09",
];

const screens: ScreenConfig[] = [
  {
    route: "/admin/users",
    title: "사용자 관리",
    eyebrow: "User & Organization",
    description:
      "KORUS 원천 사용자 정보를 읽기 전용으로 확인하고 로컬 사용여부와 업무 역할만 저장합니다.",
    menuPath: "시스템 관리 > 사용자·조직 관리 > 사용자 관리",
    listPath: () => "/api/users",
    empty: "검색 결과 없음",
    archetype: "search-list-detail",
    idKey: "userId",
    updatePath: (row) =>
      `/api/users/${encodeURIComponent(text(row.userId))}/usage`,
    updateMethod: "PATCH",
    filters: [
      { key: "filter", label: "교번·성명·소속·직급·재직상태" },
      { key: "roleCode", label: "역할", type: "roleCodes" },
      { key: "systemEnabled", label: "사용여부", type: "boolean" },
    ],
    fields: [
      { key: "staffNo", label: "교번", readonly: true, source: "KORUS" },
      { key: "name", label: "성명", readonly: true, source: "KORUS" },
      {
        key: "organizationName",
        label: "소속",
        readonly: true,
        source: "KORUS",
      },
      { key: "rank", label: "직급", readonly: true, source: "KORUS" },
      {
        key: "employmentStatus",
        label: "재직상태",
        readonly: true,
        source: "KORUS",
      },
      { key: "position", label: "보직", readonly: true, source: "KORUS" },
      {
        key: "retirementDate",
        label: "퇴직일자",
        readonly: true,
        source: "KORUS",
      },
      {
        key: "lastSyncedAt",
        label: "최종 동기화일시",
        readonly: true,
        source: "KORUS",
      },
      {
        key: "systemEnabled",
        label: "시스템 사용여부",
        type: "boolean",
        required: true,
        source: "LOCAL",
      },
      {
        key: "roleCodes",
        label: "업무 역할",
        type: "roleCodes",
        source: "LOCAL",
      },
      { key: "reason", label: "변경 사유", type: "textarea", source: "LOCAL" },
    ],
    columns: [
      { key: "staffNo", label: "교번", tone: "mono" },
      { key: "name", label: "성명" },
      { key: "organizationName", label: "소속" },
      { key: "rank", label: "직급" },
      { key: "employmentStatus", label: "재직상태", tone: "status" },
      { key: "roleCodes", label: "역할", tone: "status" },
      { key: "systemEnabled", label: "사용여부", tone: "status" },
      { key: "position", label: "보직" },
      { key: "retirementDate", label: "퇴직일자" },
      { key: "lastSyncedAt", label: "최종 동기화일시" },
    ],
  },
  {
    route: "/admin/organizations",
    title: "조직 관리",
    eyebrow: "Organization Tree",
    description:
      "조직코드와 계층을 조회하고 로컬 조직 관계와 적용기간을 저장합니다.",
    menuPath: "시스템 관리 > 사용자·조직 관리 > 조직 관리",
    listPath: () => "/api/organizations/tree",
    empty: "조직 없음",
    archetype: "tree-editor",
    idKey: "organizationCode",
    createPath: () => "/api/organization-relations",
    updateMethod: "POST",
    filters: [{ key: "filter", label: "조직코드·조직명·조직유형" }],
    fields: [
      {
        key: "organizationCode",
        label: "조직코드",
        readonly: true,
        required: true,
      },
      { key: "organizationName", label: "조직명", readonly: true },
      { key: "organizationType", label: "조직유형", readonly: true },
      { key: "parentOrganizationCode", label: "상위조직코드" },
      {
        key: "effectiveStartDate",
        label: "적용 시작일",
        type: "date",
        required: true,
      },
      { key: "effectiveEndDate", label: "적용 종료일", type: "date" },
      { key: "reason", label: "변경 사유", type: "textarea" },
    ],
    columns: [
      { key: "organizationCode", label: "조직코드", tone: "mono" },
      { key: "organizationName", label: "조직명" },
      { key: "organizationType", label: "조직유형", tone: "status" },
      { key: "parentOrganizationCode", label: "상위조직" },
      { key: "effectiveStartDate", label: "시작일" },
      { key: "effectiveEndDate", label: "종료일" },
    ],
  },
  {
    route: "/admin/roles",
    title: "역할 관리",
    eyebrow: "Role Policy",
    description:
      "R01~R09 역할의 목적, 부여 기준, 데이터 범위 기본값을 관리합니다.",
    menuPath: "시스템 관리 > 역할·권한 관리 > 역할 관리",
    listPath: () => "/api/roles",
    empty: "역할 seed 누락 오류 안내",
    archetype: "effective-period",
    idKey: "roleCode",
    createPath: () => "/api/roles",
    updatePath: (row) => `/api/roles/${encodeURIComponent(text(row.roleCode))}`,
    updateMethod: "PATCH",
    filters: [{ key: "filter", label: "역할코드·역할명" }],
    fields: [
      {
        key: "roleCode",
        label: "역할코드",
        createReadonly: true,
        required: true,
      },
      { key: "roleName", label: "역할명", required: true },
      { key: "purpose", label: "목적", type: "textarea", required: true },
      { key: "assignmentCriteria", label: "부여 기준" },
      { key: "defaultDataScope", label: "데이터 범위 기본값" },
      { key: "useYn", label: "사용여부", type: "yn" },
      { key: "reason", label: "변경 사유", type: "textarea" },
    ],
    columns: [
      { key: "roleCode", label: "역할코드", tone: "status" },
      { key: "roleName", label: "역할명" },
      { key: "purpose", label: "목적" },
      { key: "assignmentCriteria", label: "부여 기준" },
      { key: "defaultDataScope", label: "데이터 범위 기본값" },
      { key: "useYn", label: "사용여부", tone: "status" },
    ],
  },
  {
    route: "/admin/user-roles",
    title: "사용자 역할 관리",
    eyebrow: "User Role Assignment",
    description:
      "사용자별 역할 유효기간을 조회하고 부여·변경·회수 상태 전이를 수행합니다.",
    menuPath: "시스템 관리 > 역할·권한 관리 > 사용자 역할 관리",
    listPath: () => "/api/user-role-assignments",
    empty: "역할 없음",
    archetype: "effective-period",
    idKey: "assignmentId",
    createPath: () => "/api/user-role-assignments",
    updatePath: (row) =>
      `/api/user-role-assignments/${encodeURIComponent(text(row.assignmentId))}`,
    updateMethod: "PATCH",
    filters: [{ key: "filter", label: "사용자·역할·부여구분" }],
    fields: [
      { key: "assignmentId", label: "배정ID", readonly: true },
      { key: "userId", label: "사용자ID", required: true },
      { key: "roleCode", label: "역할", type: "roleCodes", required: true },
      {
        key: "assignmentType",
        label: "부여구분",
        type: "assignmentType",
        required: true,
      },
      { key: "validFrom", label: "유효시작", type: "date", required: true },
      { key: "validTo", label: "유효종료", type: "date" },
      { key: "approverUserId", label: "승인자" },
      { key: "status", label: "상태", readonly: true },
      { key: "reason", label: "변경 사유", type: "textarea" },
    ],
    columns: [
      { key: "userName", label: "사용자" },
      { key: "userId", label: "사용자ID", tone: "mono" },
      { key: "roleCode", label: "역할코드", tone: "status" },
      { key: "assignmentType", label: "부여구분", tone: "status" },
      { key: "validFrom", label: "유효시작" },
      { key: "validTo", label: "유효종료" },
      { key: "approverUserId", label: "승인자" },
      { key: "status", label: "상태", tone: "status" },
    ],
  },
  {
    route: "/admin/menu-permissions",
    title: "메뉴 권한 관리",
    eyebrow: "Permission Matrix",
    description:
      "역할·조직·사용자 대상별 메뉴 접근 허용 여부를 조회하고 matrix 단위로 저장합니다.",
    menuPath: "시스템 관리 > 역할·권한 관리 > 메뉴 권한 관리",
    listPath: () => "/api/menu-permissions",
    empty: "권한 설정 없음",
    archetype: "permission-matrix",
    idKey: "permissionId",
    createPath: () => "/api/menu-permissions",
    updateMethod: "PUT",
    filters: [
      { key: "targetType", label: "대상구분", type: "targetType" },
      { key: "targetId", label: "대상ID" },
    ],
    fields: [
      {
        key: "targetType",
        label: "대상구분",
        type: "targetType",
        required: true,
      },
      { key: "targetId", label: "대상ID", required: true },
      { key: "menuId", label: "메뉴ID", readonly: true },
      { key: "menuName", label: "화면", readonly: true },
      { key: "allowed", label: "접근 허용", type: "boolean", required: true },
      { key: "reason", label: "변경 사유", type: "textarea" },
    ],
    columns: [
      { key: "targetType", label: "대상구분", tone: "status" },
      { key: "targetId", label: "대상ID", tone: "mono" },
      { key: "menuLevel", label: "메뉴레벨", tone: "status" },
      { key: "menuName", label: "화면" },
      { key: "allowed", label: "접근 허용", tone: "status" },
    ],
  },
  {
    route: "/admin/menu-structure",
    title: "메뉴 구조 관리",
    eyebrow: "Menu Structure",
    description:
      "메뉴 계층을 조회하고 부모메뉴와 동일 계층 표시순서를 변경합니다.",
    menuPath: "시스템 관리 > 메뉴 관리 > 메뉴 구조 관리",
    listPath: () => "/api/menus/tree",
    empty: "메뉴 없음",
    archetype: "tree-editor",
    idKey: "menuId",
    updatePath: (row) =>
      `/api/menus/${encodeURIComponent(text(row.menuId))}/parent`,
    updateMethod: "PATCH",
    filters: [{ key: "filter", label: "메뉴명·화면ID·URL" }],
    fields: [
      { key: "menuId", label: "메뉴ID", readonly: true, required: true },
      { key: "menuName", label: "메뉴명", readonly: true },
      { key: "parentMenuId", label: "부모메뉴" },
      {
        key: "displayOrder",
        label: "표시순서",
        type: "number",
        required: true,
      },
      { key: "reason", label: "변경 사유", type: "textarea" },
    ],
    columns: [
      { key: "menuName", label: "메뉴명" },
      { key: "menuId", label: "메뉴ID", tone: "mono" },
      { key: "menuLevel", label: "레벨", tone: "status" },
      { key: "parentMenuId", label: "부모메뉴" },
      { key: "displayOrder", label: "표시순서" },
    ],
  },
  {
    route: "/admin/menu-info",
    title: "메뉴 정보 관리",
    eyebrow: "Menu Execution",
    description:
      "메뉴 실행정보를 조회·등록·수정하고 화면 연결 URL을 확인합니다.",
    menuPath: "시스템 관리 > 메뉴 관리 > 메뉴 정보 관리",
    listPath: () => "/api/menus",
    empty: "메뉴 실행정보 없음",
    archetype: "search-list-detail",
    idKey: "menuId",
    createPath: () => "/api/menus",
    updatePath: (row) => `/api/menus/${encodeURIComponent(text(row.menuId))}`,
    updateMethod: "PATCH",
    filters: [{ key: "filter", label: "메뉴명·화면ID·URL" }],
    fields: [
      { key: "menuId", label: "메뉴ID", createReadonly: true, required: true },
      { key: "parentMenuId", label: "부모메뉴" },
      { key: "menuLevel", label: "메뉴레벨" },
      { key: "menuName", label: "메뉴명", required: true },
      {
        key: "displayOrder",
        label: "표시순서",
        type: "number",
        required: true,
      },
      { key: "screenId", label: "화면ID" },
      { key: "url", label: "URL" },
      { key: "icon", label: "아이콘" },
      { key: "businessCategory", label: "업무구분" },
      { key: "description", label: "설명", type: "textarea" },
      { key: "useYn", label: "사용여부", type: "yn" },
      { key: "reason", label: "변경 사유", type: "textarea" },
    ],
    columns: [
      { key: "menuName", label: "메뉴명" },
      { key: "screenId", label: "화면ID", tone: "mono" },
      { key: "url", label: "URL", tone: "mono" },
      { key: "icon", label: "아이콘" },
      { key: "businessCategory", label: "업무구분" },
      { key: "description", label: "설명" },
      { key: "useYn", label: "사용여부", tone: "status" },
    ],
  },
  {
    route: "/admin/code-groups",
    title: "코드그룹 관리",
    eyebrow: "Common Code Group",
    description:
      "코드그룹을 조회·등록·수정하고 선택 그룹의 상세코드 화면으로 이동합니다.",
    menuPath: "시스템 관리 > 공통코드 관리 > 코드그룹 관리",
    listPath: () => "/api/code-groups",
    empty: "코드그룹 없음",
    archetype: "search-list-detail",
    idKey: "groupId",
    createPath: () => "/api/code-groups",
    updatePath: (row) =>
      `/api/code-groups/${encodeURIComponent(text(row.groupId))}`,
    updateMethod: "PATCH",
    filters: [{ key: "filter", label: "그룹ID·명칭·관리부서" }],
    fields: [
      { key: "groupId", label: "그룹ID", createReadonly: true, required: true },
      { key: "groupName", label: "명칭", required: true },
      { key: "description", label: "설명", type: "textarea" },
      { key: "managementDepartment", label: "관리부서" },
      { key: "useYn", label: "사용여부", type: "yn" },
      { key: "reason", label: "변경 사유", type: "textarea" },
    ],
    columns: [
      { key: "groupId", label: "그룹ID", tone: "mono" },
      { key: "groupName", label: "명칭" },
      { key: "description", label: "설명" },
      { key: "managementDepartment", label: "관리부서" },
      { key: "useYn", label: "사용여부", tone: "status" },
      { key: "detail", label: "상세코드 이동" },
    ],
  },
  {
    route: "/admin/code-groups/:groupId/codes",
    match: (route) => /^\/admin\/code-groups\/[^/]+\/codes$/.test(route),
    title: "상세코드 관리",
    eyebrow: "Detail Code",
    description:
      "선택 코드그룹의 상세코드를 조회·등록·수정하고 상위코드 관계와 추가속성을 확인합니다.",
    menuPath: "시스템 관리 > 공통코드 관리 > 상세코드 관리",
    listPath: (route) =>
      `/api/code-groups/${encodeURIComponent(routeGroupId(route))}/codes`,
    empty: "상세코드 없음",
    archetype: "tree-editor",
    idKey: "codeValue",
    createPath: (route) =>
      `/api/code-groups/${encodeURIComponent(routeGroupId(route))}/codes`,
    updatePath: (row, route) =>
      `/api/code-groups/${encodeURIComponent(routeGroupId(route))}/codes/${encodeURIComponent(text(row.codeValue))}`,
    updateMethod: "PATCH",
    filters: [{ key: "filter", label: "코드값·코드명" }],
    fields: [
      { key: "groupId", label: "그룹ID", readonly: true },
      {
        key: "codeValue",
        label: "코드값",
        createReadonly: true,
        required: true,
      },
      { key: "codeName", label: "코드명", required: true },
      { key: "parentCodeValue", label: "상위코드" },
      { key: "sortOrder", label: "정렬순서", type: "number", required: true },
      { key: "extraAttributes", label: "추가속성 JSON", type: "json" },
      { key: "validFrom", label: "유효시작", type: "date" },
      { key: "validTo", label: "유효종료", type: "date" },
      { key: "useYn", label: "사용여부", type: "yn" },
      { key: "reason", label: "변경 사유", type: "textarea" },
    ],
    columns: [
      { key: "codeValue", label: "코드값", tone: "mono" },
      { key: "codeName", label: "코드명" },
      { key: "parentCodeValue", label: "상위코드" },
      { key: "sortOrder", label: "정렬순서" },
      { key: "extraAttributes", label: "추가속성" },
      { key: "useYn", label: "사용여부", tone: "status" },
    ],
  },
];

function currentRoute() {
  return window.location.pathname === "/" ? "/login" : window.location.pathname;
}

function App() {
  const [route, setRoute] = useState(currentRoute());
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [authError, setAuthError] = useState("");
  const [navItems, setNavItems] = useState<Row[]>([]);

  useEffect(() => {
    const onPop = () => setRoute(currentRoute());
    window.addEventListener("popstate", onPop);
    return () => window.removeEventListener("popstate", onPop);
  }, []);

  useEffect(() => {
    if (route === "/login") return;
    api<CurrentUser>("/api/auth/me")
      .then((current) => {
        setUser(current);
        setAuthError("");
      })
      .catch((err) => {
        setAuthError(errorMessage(err, "권한 없음 또는 로그인이 필요합니다."));
        replaceRoute("/login", setRoute);
      });
  }, [route]);

  useEffect(() => {
    if (!user) return;
    api<PageResult<Row>>("/api/navigation/menus")
      .then((data) => setNavItems(data.items))
      .catch(() => setNavItems([]));
  }, [user]);

  if (route === "/login") {
    return (
      <LoginPage
        authError={authError}
        onSuccess={() => pushRoute("/admin/users", setRoute)}
      />
    );
  }

  const screen = resolveScreen(route);
  return (
    <AdminLayout
      route={route}
      setRoute={setRoute}
      user={user}
      navItems={navItems}
    >
      <ManagementScreen
        key={route}
        config={screen}
        route={route}
        setRoute={setRoute}
      />
    </AdminLayout>
  );
}

function LoginPage({
  authError,
  onSuccess,
}: {
  authError: string;
  onSuccess: () => void;
}) {
  const [loginId, setLoginId] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState(authError);
  const [loading, setLoading] = useState(false);

  useEffect(() => setError(authError), [authError]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!loginId || !password) {
      setError("loginId와 password를 입력하세요.");
      return;
    }
    setLoading(true);
    setError("");
    try {
      await api("/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ loginId, password }),
      });
      onSuccess();
    } catch (err) {
      setError(errorMessage(err, "계정 또는 비밀번호를 확인하세요."));
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="login-shell">
      <section className="login-card" aria-label="로그인">
        <div className="brand-mark">KNUE</div>
        <p className="eyebrow">Faculty Performance System</p>
        <h1>한국교원대학교 교수업적평가시스템</h1>
        <p className="muted">
          R09 시스템 관리자 세션을 생성해 공통기능 관리 화면으로 이동합니다.
        </p>
        <form onSubmit={submit} className="form-grid single">
          <FieldLabel label="loginId" required>
            <input
              value={loginId}
              onChange={(event) => setLoginId(event.target.value)}
              required
              autoComplete="username"
            />
          </FieldLabel>
          <FieldLabel label="password" required>
            <input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
              autoComplete="current-password"
            />
          </FieldLabel>
          <button className="primary full" disabled={loading}>
            {loading ? "로그인 중..." : "로그인"}
          </button>
        </form>
        {error && (
          <StateBanner type="error" title="로그인 오류" message={error} />
        )}
      </section>
      <aside className="login-hero" aria-hidden="true">
        <div>
          <p className="eyebrow">Source-backed Admin</p>
          <h2>시스템 관리 업무를 한 화면 흐름으로 정렬합니다.</h2>
          <p>
            사용자, 조직, 역할, 메뉴, 공통코드 관리가 backend API와 동일한 필드
            계약으로 연결됩니다.
          </p>
        </div>
      </aside>
    </main>
  );
}

function AdminLayout({
  route,
  setRoute,
  user,
  navItems,
  children,
}: {
  route: string;
  setRoute: (route: string) => void;
  user: CurrentUser | null;
  navItems: Row[];
  children: React.ReactNode;
}) {
  const links = useMemo(() => navigationLinks(navItems), [navItems]);
  const grouped = groupBy(links, (item) => item.group);

  async function logout() {
    await api("/api/auth/logout", {
      method: "POST",
      body: JSON.stringify({ reason: "logout" }),
    }).catch(() => undefined);
    replaceRoute("/login", setRoute);
  }

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <div className="brand-mark small">KNUE</div>
          <div>
            <h1>한국교원대학교 교수업적평가시스템</h1>
            <p>공통기능 1차</p>
          </div>
        </div>
        <nav className="nav-groups" aria-label="시스템 관리 메뉴">
          {Object.entries(grouped).map(([group, items]) => (
            <section key={group}>
              <h2>{group}</h2>
              {items.map((item) => (
                <a
                  key={item.path}
                  className={isActiveRoute(route, item.path) ? "active" : ""}
                  href={item.path}
                  onClick={(event) => {
                    event.preventDefault();
                    pushRoute(item.path, setRoute);
                  }}
                >
                  <span>{item.label} 화면</span>
                  <span className="nav-dot" />
                </a>
              ))}
            </section>
          ))}
        </nav>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <div>
            <p className="eyebrow">System Management</p>
            <strong>{user?.name ?? "관리자"}</strong>
            <span className="role-pill">
              {user?.roles?.join(", ") || "R09"}
            </span>
          </div>
          <button className="ghost" onClick={logout}>
            로그아웃
          </button>
        </header>
        <main className="content">{children}</main>
      </div>
    </div>
  );
}

function ManagementScreen({
  config,
  route,
  setRoute,
}: {
  config: ScreenConfig;
  route: string;
  setRoute: (route: string) => void;
}) {
  const [filters, setFilters] = useState<Row>(defaultFilters(config, route));
  const [rows, setRows] = useState<Row[]>([]);
  const [selected, setSelected] = useState<Row>({});
  const [original, setOriginal] = useState<Row>({});
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [creating, setCreating] = useState(false);
  const endpoint = useMemo(() => config.listPath(route), [config, route]);
  const canCreate =
    Boolean(config.createPath) &&
    config.title !== "조직 관리" &&
    config.title !== "메뉴 권한 관리";

  async function load(
    nextFilters = filters,
    preferredSelected: Row = selected,
  ) {
    setLoading(true);
    setError("");
    setFieldErrors({});
    try {
      const data = await api<PageResult<Row>>(
        query(endpoint, queryParams(config, nextFilters)),
      );
      const items = flattenTreeRows(data.items);
      setRows(items);
      const preferred = items.find((item) =>
        isSameRow(config, item, preferredSelected),
      );
      const first = preferred ?? items[0] ?? {};
      setSelected(first);
      setOriginal(first);
      setCreating(false);
      setMessage(
        items.length === 0 ? config.empty : `조회 완료 · ${items.length}건`,
      );
    } catch (err) {
      setError(errorMessage(err, "API 오류"));
      setFieldErrors(errorFields(err));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load(defaultFilters(config, route));
  }, [endpoint]);

  function updateSelected(key: string, value: unknown) {
    setSelected((prev) => ({ ...prev, [key]: value }));
  }

  async function save(mode: "default" | "reorder" = "default") {
    if (!config.createPath && !config.updatePath) return;
    const validation = validate(config, selected, creating);
    if (Object.keys(validation).length > 0) {
      setFieldErrors(validation);
      setError("필수값을 확인하세요.");
      return;
    }
    if (!window.confirm("변경 내용을 저장하시겠습니까?")) return;
    setError("");
    setFieldErrors({});
    try {
      if (config.title === "사용자 관리") {
        await saveUser(selected);
      } else if (config.title === "메뉴 권한 관리") {
        await savePermissionMatrix(selected, rows);
      } else if (config.title === "메뉴 구조 관리" && mode === "reorder") {
        await reorderMenus(config, rows, selected);
      } else {
        const payload = normalizePayload(config, selected, route, creating);
        const path =
          creating && config.createPath
            ? config.createPath(route)
            : (config.updatePath?.(selected, route) ??
              config.createPath?.(route));
        const method =
          creating && config.createPath
            ? "POST"
            : (config.updateMethod ?? "POST");
        await api(path ?? endpoint, { method, body: JSON.stringify(payload) });
      }
      setMessage("저장 완료 · 현재 화면을 재조회했습니다.");
      await load(filters, selected);
    } catch (err) {
      setError(errorMessage(err, "검증 오류"));
      setFieldErrors(errorFields(err));
    }
  }

  async function revoke() {
    if (config.title !== "사용자 역할 관리" || !selected.assignmentId) return;
    if (!window.confirm("선택한 사용자 역할을 회수하시겠습니까?")) return;
    try {
      await api(
        `/api/user-role-assignments/${encodeURIComponent(text(selected.assignmentId))}`,
        {
          method: "DELETE",
          body: JSON.stringify({ reason: selected.reason }),
        },
      );
      setMessage("회수 완료 · 목록을 재조회했습니다.");
      await load();
    } catch (err) {
      setError(errorMessage(err, "회수 오류"));
      setFieldErrors(errorFields(err));
    }
  }

  function resetForm() {
    setSelected(original);
    setCreating(false);
    setFieldErrors({});
    setError("");
    setMessage("선택/입력 값을 마지막 조회 상태로 복원했습니다.");
  }

  function startCreate() {
    const blank = blankRow(config, route);
    setSelected(blank);
    setOriginal(blank);
    setCreating(true);
    setFieldErrors({});
    setMessage("신규 등록 모드입니다. 서버 계약의 필수값만 입력하세요.");
  }

  const stats = summarize(rows, config);

  return (
    <section className="page-stack">
      <div className="page-header">
        <div>
          <p className="eyebrow">{config.eyebrow}</p>
          <h1>{config.title}</h1>
          <p>{config.description}</p>
          <span className="breadcrumb">{config.menuPath}</span>
        </div>
        {canCreate && (
          <button className="primary" onClick={startCreate}>
            신규 등록
          </button>
        )}
      </div>

      <section className="summary-grid" aria-label="조회 요약">
        {stats.map((item) => (
          <div className="metric-card" key={item.label}>
            <span>{item.icon}</span>
            <div>
              <strong>{item.value}</strong>
              <p>{item.label}</p>
            </div>
          </div>
        ))}
      </section>

      <section className="card filter-card">
        <div>
          <h2>검색조건</h2>
          <p className="muted">
            값이 있는 조건만 query parameter로 전송합니다.
          </p>
        </div>
        <div className="form-grid filters">
          {config.filters.map((field) => (
            <FieldLabel key={field.key} label={field.label}>
              {renderInput(
                field,
                filters[field.key],
                (value) =>
                  setFilters((prev) => ({ ...prev, [field.key]: value })),
                false,
              )}
            </FieldLabel>
          ))}
        </div>
        <div className="action-row">
          <button
            className="primary"
            onClick={() => load(filters)}
            disabled={loading}
          >
            {loading ? "조회 중..." : "검색"}
          </button>
          <button
            className="secondary"
            onClick={() => {
              const next = defaultFilters(config, route);
              setFilters(next);
              void load(next);
            }}
          >
            초기화
          </button>
        </div>
      </section>

      {loading && <SkeletonTable columns={config.columns.length} />}
      {!loading && error && (
        <StateBanner
          type={
            error.includes("권한") || error.includes("R09")
              ? "permission"
              : "error"
          }
          title={
            error.includes("권한") || error.includes("R09")
              ? "권한 없음"
              : "API 오류"
          }
          message={error}
        />
      )}
      {!loading && message && !error && (
        <StateBanner type="success" title="상태" message={message} />
      )}

      <div className="split-layout">
        <section className="card table-card">
          <div className="section-heading">
            <div>
              <h2>
                {config.archetype === "tree-editor"
                  ? "계층/목록"
                  : config.archetype === "permission-matrix"
                    ? "권한 matrix"
                    : "목록"}
              </h2>
              <p className="muted">
                행을 선택하면 오른쪽 상세/편집 영역에 API 응답 필드가
                표시됩니다.
              </p>
            </div>
          </div>
          {rows.length === 0 && !loading ? (
            <EmptyState
              title={config.empty}
              message="source-backed 조회 결과가 없습니다. 검색 조건을 조정하거나 신규 등록 가능 화면은 등록을 진행하세요."
            />
          ) : (
            <DataTable
              config={config}
              rows={rows}
              selected={selected}
              onSelect={(row) => {
                setSelected(row);
                setOriginal(row);
                setCreating(false);
              }}
              onNavigate={setRoute}
              onPatchRow={(index, key, value) =>
                setRows((prev) =>
                  prev.map((row, rowIndex) =>
                    rowIndex === index ? { ...row, [key]: value } : row,
                  ),
                )
              }
            />
          )}
        </section>

        <section className="card detail-card">
          <div className="section-heading">
            <div>
              <h2>{creating ? "등록" : "상세/편집"}</h2>
              <p className="muted">
                KORUS/source-of-truth 필드는 readonly이며 저장 payload에서
                제외됩니다.
              </p>
            </div>
            {Boolean(selected[config.idKey ?? ""]) && (
              <span className="badge neutral">
                {text(selected[config.idKey ?? ""])}
              </span>
            )}
          </div>
          <div className="form-grid detail">
            {config.fields.map((field) => {
              const readonly =
                field.readonly || (!creating && field.createReadonly);
              return (
                <FieldLabel
                  key={field.key}
                  label={field.label}
                  required={field.required}
                  badge={field.source}
                  error={fieldErrors[field.key]}
                >
                  {renderInput(
                    field,
                    selected[field.key],
                    (value) => updateSelected(field.key, value),
                    Boolean(readonly),
                  )}
                </FieldLabel>
              );
            })}
          </div>
          <div className="sticky-actions">
            {config.title === "메뉴 구조 관리" ? (
              <>
                <button className="primary" onClick={() => save("default")}>
                  부모 변경 저장
                </button>
                <button className="primary" onClick={() => save("reorder")}>
                  순서 재정렬 저장
                </button>
              </>
            ) : (
              <button className="primary" onClick={() => save()}>
                저장
              </button>
            )}
            {config.title === "사용자 역할 관리" &&
              Boolean(selected.assignmentId) && (
                <button className="danger" onClick={revoke}>
                  회수
                </button>
              )}
            <button className="secondary" onClick={resetForm}>
              취소
            </button>
          </div>
        </section>
      </div>
    </section>
  );
}

function FieldLabel({
  label,
  required,
  badge,
  error,
  children,
}: {
  label: string;
  required?: boolean;
  badge?: string;
  error?: string;
  children: React.ReactNode;
}) {
  return (
    <label className="field">
      <span>
        {label}
        {required && <em>*</em>}
        {badge && <b>{badge}</b>}
      </span>
      {children}
      {error && <small className="field-error">{error}</small>}
    </label>
  );
}

function DataTable({
  config,
  rows,
  selected,
  onSelect,
  onNavigate,
  onPatchRow,
}: {
  config: ScreenConfig;
  rows: Row[];
  selected: Row;
  onSelect: (row: Row) => void;
  onNavigate: (route: string) => void;
  onPatchRow: (index: number, key: string, value: unknown) => void;
}) {
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            {config.columns.map((column) => (
              <th key={column.key}>{column.label}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => (
            <tr
              key={`${text(row[config.idKey ?? "id"])}-${index}`}
              className={isSameRow(config, row, selected) ? "selected" : ""}
              onClick={() => onSelect(row)}
            >
              {config.columns.map((column) => (
                <td key={column.key}>
                  {column.key === "detail" ? (
                    <button
                      className="link-button"
                      disabled={!row.groupId}
                      onClick={(event) => {
                        event.stopPropagation();
                        const path = `/admin/code-groups/${encodeURIComponent(text(row.groupId))}/codes`;
                        window.history.pushState({}, "", path);
                        onNavigate(path);
                      }}
                    >
                      상세코드
                    </button>
                  ) : config.title === "메뉴 권한 관리" &&
                    column.key === "allowed" ? (
                    <input
                      className="cell-check"
                      type="checkbox"
                      checked={toBool(row.allowed)}
                      onChange={(event) =>
                        onPatchRow(index, "allowed", event.target.checked)
                      }
                      onClick={(event) => event.stopPropagation()}
                    />
                  ) : column.tone === "status" ? (
                    <span className={`badge ${statusClass(row[column.key])}`}>
                      {displayValue(row[column.key])}
                    </span>
                  ) : column.tone === "mono" ? (
                    <code>{displayValue(row[column.key])}</code>
                  ) : (
                    displayValue(row[column.key])
                  )}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function SkeletonTable({ columns }: { columns: number }) {
  return (
    <section className="card skeleton-card" aria-label="loading">
      {Array.from({ length: 4 }).map((_, row) => (
        <div className="skeleton-row" key={row}>
          {Array.from({ length: Math.min(columns, 6) }).map((__, col) => (
            <span key={col} />
          ))}
        </div>
      ))}
    </section>
  );
}

function EmptyState({ title, message }: { title: string; message: string }) {
  return (
    <div className="empty-state">
      <div className="empty-icon">∅</div>
      <h3>{title}</h3>
      <p>{message}</p>
    </div>
  );
}

function StateBanner({
  type,
  title,
  message,
}: {
  type: "success" | "error" | "permission";
  title: string;
  message: string;
}) {
  return (
    <div className={`state-banner ${type}`}>
      <strong>{title}</strong>
      <span>{message}</span>
    </div>
  );
}

function renderInput(
  field: FieldConfig,
  value: unknown,
  onChange: (value: unknown) => void,
  readonly: boolean,
) {
  const common = {
    value: field.type === "roleCodes" ? roleText(value) : text(value),
    readOnly: readonly,
    required: field.required,
    onChange: (
      event: React.ChangeEvent<
        HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement
      >,
    ) =>
      onChange(
        field.type === "number"
          ? Number(event.target.value)
          : event.target.value,
      ),
  };
  if (field.type === "textarea" || field.type === "json")
    return (
      <textarea
        {...common}
        rows={field.type === "json" ? 5 : 3}
        placeholder={field.type === "json" ? "{}" : undefined}
      />
    );
  if (field.type === "date") return <input {...common} type="date" />;
  if (field.type === "number")
    return <input {...common} type="number" min={0} />;
  if (field.type === "boolean") {
    return (
      <select
        value={value === undefined || value === "" ? "" : String(toBool(value))}
        disabled={readonly}
        required={field.required}
        onChange={(event) => onChange(event.target.value === "true")}
      >
        <option value="">전체/선택</option>
        <option value="true">Y / 허용</option>
        <option value="false">N / 차단</option>
      </select>
    );
  }
  if (field.type === "yn") {
    return (
      <select
        value={text(value) || "Y"}
        disabled={readonly}
        required={field.required}
        onChange={(event) => onChange(event.target.value)}
      >
        <option value="Y">Y</option>
        <option value="N">N</option>
      </select>
    );
  }
  if (field.type === "assignmentType") {
    return (
      <select
        value={text(value)}
        disabled={readonly}
        required={field.required}
        onChange={(event) => onChange(event.target.value)}
      >
        <option value="">선택</option>
        <option value="POSITION_BASED">POSITION_BASED</option>
        <option value="MANUAL">MANUAL</option>
      </select>
    );
  }
  if (field.type === "targetType") {
    return (
      <select
        value={text(value)}
        disabled={readonly}
        required={field.required}
        onChange={(event) => onChange(event.target.value)}
      >
        <option value="">선택</option>
        <option value="ROLE">ROLE</option>
        <option value="ORG">ORG</option>
        <option value="USER">USER</option>
      </select>
    );
  }
  if (field.type === "roleCodes" && field.key === "roleCode") {
    return (
      <select
        value={text(value)}
        disabled={readonly}
        required={field.required}
        onChange={(event) => onChange(event.target.value)}
      >
        <option value="">선택</option>
        {roleOptions.map((role) => (
          <option value={role} key={role}>
            {role}
          </option>
        ))}
      </select>
    );
  }
  return (
    <input
      {...common}
      placeholder={field.type === "roleCodes" ? "R01,R09" : undefined}
    />
  );
}

function resolveScreen(route: string) {
  return (
    screens.find((item) => item.route === route || item.match?.(route)) ??
    screens[0]
  );
}

function pushRoute(path: string, setRoute: (route: string) => void) {
  window.history.pushState({}, "", path);
  setRoute(path);
}

function replaceRoute(path: string, setRoute: (route: string) => void) {
  window.history.replaceState({}, "", path);
  setRoute(path);
}

function isActiveRoute(route: string, path: string) {
  if (
    path.includes("/code-groups/") &&
    route.startsWith("/admin/code-groups/") &&
    route.endsWith("/codes")
  )
    return true;
  return route === path;
}

function navigationLinks(navItems: Row[]) {
  const source =
    navItems.length > 0
      ? navItems.map(
          (row) =>
            [
              menuGroup(text(row.url)),
              text(row.url),
              text(row.menuName),
            ] as string[],
        )
      : fallbackMenuLinks;
  const known = new Map(
    fallbackMenuLinks.map(([group, path, label]) => [
      path,
      { group, path, label },
    ]),
  );
  source.forEach(([group, path, label]) => {
    if (path) known.set(path, { group: group || menuGroup(path), path, label });
  });
  return Array.from(known.values());
}

function menuGroup(path: string) {
  if (path.includes("organizations") || path.includes("users"))
    return "사용자·조직 관리";
  if (path.includes("roles") || path.includes("permissions"))
    return "역할·권한 관리";
  if (path.includes("menu")) return "메뉴 관리";
  return "공통코드 관리";
}

function groupBy<T>(items: T[], keyFn: (item: T) => string) {
  return items.reduce<Record<string, T[]>>((acc, item) => {
    const key = keyFn(item);
    acc[key] = [...(acc[key] ?? []), item];
    return acc;
  }, {});
}

function defaultFilters(config: ScreenConfig, route: string): Row {
  const result: Row = {};
  config.filters.forEach((field) => {
    result[field.key] = "";
  });
  if (config.title === "메뉴 권한 관리") {
    result.targetType = "ROLE";
    result.targetId = "R09";
  }
  if (config.title === "상세코드 관리") result.groupId = routeGroupId(route);
  return result;
}

function queryParams(config: ScreenConfig, filters: Row) {
  const params: Record<string, string | undefined> = {};
  config.filters.forEach((field) => {
    const value = filters[field.key];
    if (value !== undefined && value !== "") params[field.key] = String(value);
  });
  return params;
}

function routeGroupId(route: string) {
  return decodeURIComponent(route.split("/")[3] ?? "COMMON_YN");
}

function flattenTreeRows(items: Row[]): Row[] {
  const result: Row[] = [];
  const visit = (row: Row, depth: number) => {
    const { children, ...rest } = row;
    result.push({ ...rest, depth });
    if (Array.isArray(children))
      children.forEach((child) => visit(child as Row, depth + 1));
  };
  items.forEach((item) => visit(item, 0));
  return result;
}

function blankRow(config: ScreenConfig, route: string): Row {
  const row: Row = {};
  config.fields.forEach((field) => {
    row[field.key] =
      field.type === "yn"
        ? "Y"
        : field.type === "boolean"
          ? true
          : field.type === "assignmentType"
            ? "MANUAL"
            : "";
  });
  if (config.title === "상세코드 관리") row.groupId = routeGroupId(route);
  return row;
}

function validate(config: ScreenConfig, row: Row, creating: boolean) {
  const errors: Record<string, string> = {};
  config.fields.forEach((field) => {
    if (
      field.required &&
      !field.readonly &&
      (creating || !field.createReadonly) &&
      text(row[field.key]).trim() === ""
    )
      errors[field.key] = "필수값입니다.";
  });
  if (config.title === "상세코드 관리" && text(row.extraAttributes).trim()) {
    try {
      JSON.parse(text(row.extraAttributes));
    } catch {
      errors.extraAttributes = "JSON 형식이어야 합니다.";
    }
  }
  return errors;
}

async function saveUser(row: Row) {
  const userId = encodeURIComponent(text(row.userId));
  await api(`/api/users/${userId}/usage`, {
    method: "PATCH",
    body: JSON.stringify({
      systemEnabled: toBool(row.systemEnabled),
      reason: row.reason,
    }),
  });
  const roles = roleArray(row.roleCodes);
  if (roles.length > 0) {
    await api(`/api/users/${userId}/business-roles`, {
      method: "PATCH",
      body: JSON.stringify({ roleCodes: roles, reason: row.reason }),
    });
  }
}

async function savePermissionMatrix(selected: Row, rows: Row[]) {
  const targetType = text(selected.targetType || rows[0]?.targetType);
  const targetId = text(selected.targetId || rows[0]?.targetId);
  await api("/api/menu-permissions", {
    method: "PUT",
    body: JSON.stringify({
      targetType,
      targetId,
      permissions: rows
        .filter((row) => row.menuId)
        .map((row) => ({ menuId: row.menuId, allowed: toBool(row.allowed) })),
      reason: selected.reason,
    }),
  });
}

async function reorderMenus(config: ScreenConfig, rows: Row[], selected: Row) {
  await api("/api/menus/reorder", {
    method: "PATCH",
    body: JSON.stringify({
      items: rows
        .filter((row) => row.menuId)
        .map((row) => {
          const source = isSameRow(config, row, selected) ? selected : row;
          return {
            menuId: source.menuId,
            displayOrder: Number(source.displayOrder),
          };
        }),
      reason: selected.reason,
    }),
  });
}

function normalizePayload(
  config: ScreenConfig,
  row: Row,
  route: string,
  creating: boolean,
) {
  const allowed = new Set(
    config.fields
      .filter(
        (field) =>
          !field.readonly &&
          field.source !== "KORUS" &&
          (creating || !field.createReadonly),
      )
      .map((field) => field.key),
  );
  const payload: Row = {};
  allowed.forEach((key) => {
    if (key === "reason") return;
    payload[key] = row[key];
  });
  payload.reason = row.reason;
  if (config.title === "상세코드 관리") {
    payload.groupId = undefined;
    payload.sortOrder = Number(row.sortOrder);
    payload.extraAttributes = text(row.extraAttributes).trim()
      ? JSON.parse(text(row.extraAttributes))
      : {};
  }
  if (config.title === "메뉴 구조 관리")
    payload.displayOrder = Number(row.displayOrder);
  if (config.title === "메뉴 정보 관리")
    payload.displayOrder = Number(row.displayOrder);
  if (config.title === "조직 관리")
    payload.organizationCode = row.organizationCode;
  if (config.title === "사용자 역할 관리")
    payload.roleCode = Array.isArray(row.roleCode)
      ? row.roleCode[0]
      : row.roleCode;
  if (
    config.title === "역할 관리" ||
    config.title === "메뉴 정보 관리" ||
    config.title === "코드그룹 관리"
  )
    payload.useYn = text(row.useYn) || "Y";
  if (config.title === "상세코드 관리") payload.useYn = text(row.useYn) || "Y";
  void route;
  return payload;
}

function summarize(rows: Row[], config: ScreenConfig) {
  const active = rows.filter(
    (row) =>
      ["Y", true, "ACTIVE"].includes(row.useYn as string | boolean) ||
      row.status === "ACTIVE" ||
      row.allowed === true,
  ).length;
  return [
    { label: "조회 결과", value: String(rows.length), icon: "▦" },
    {
      label:
        config.archetype === "permission-matrix" ? "허용/활성" : "활성 상태",
      value: String(active),
      icon: "✓",
    },
    {
      label: "API-backed route",
      value: config.route.includes(":")
        ? "/admin/code-groups/{groupId}/codes"
        : config.route,
      icon: "↗",
    },
  ];
}

function isSameRow(config: ScreenConfig, a: Row, b: Row) {
  const key = config.idKey;
  return key ? text(a[key]) !== "" && text(a[key]) === text(b[key]) : a === b;
}

function statusClass(value: unknown) {
  const v = displayValue(value);
  if (["Y", "true", "허용", "ACTIVE"].includes(v)) return "success";
  if (["N", "false", "차단", "REVOKED"].includes(v)) return "danger";
  return "neutral";
}

function displayValue(value: unknown) {
  if (Array.isArray(value)) return value.join(", ");
  if (typeof value === "boolean") return value ? "Y" : "N";
  if (value === null || value === undefined) return "-";
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}

function text(value: unknown) {
  if (Array.isArray(value)) return value.join(",");
  if (value === null || value === undefined) return "";
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}

function roleText(value: unknown) {
  return roleArray(value).join(",");
}

function roleArray(value: unknown) {
  if (Array.isArray(value)) return value.map(String).filter(Boolean);
  return text(value)
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

function toBool(value: unknown) {
  return value === true || value === "true" || value === "Y" || value === "1";
}

function errorMessage(err: unknown, fallback: string) {
  if (err && typeof err === "object" && "message" in err)
    return String((err as { message?: unknown }).message ?? fallback);
  return fallback;
}

function errorFields(err: unknown) {
  if (!err || typeof err !== "object" || !("meta" in err)) return {};
  const meta = (err as { meta?: Record<string, unknown> }).meta ?? {};
  return Object.fromEntries(
    Object.entries(meta).map(([key, value]) => [key, String(value)]),
  );
}

export default App;
