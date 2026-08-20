import React, { useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  AlertCircle,
  BadgeCheck,
  Building2,
  CheckCircle2,
  ChevronRight,
  Code2,
  FolderTree,
  KeyRound,
  Loader2,
  LockKeyhole,
  LogIn,
  Menu,
  Network,
  RefreshCw,
  Route,
  Save,
  Search,
  ShieldCheck,
  Sparkles,
  Users,
} from "lucide-react";
import "./index.css";
import { ApiRequestError, api } from "./api";

type Row = Record<string, any>;
type ScreenKind =
  | "users"
  | "orgs"
  | "roles"
  | "userRoles"
  | "permissions"
  | "menuStructure"
  | "menus"
  | "codeGroups"
  | "codeDetails";
type Screen = {
  id: string;
  title: string;
  route: string;
  group: string;
  kind: ScreenKind;
  icon: React.ComponentType<{ className?: string }>;
  purpose: string;
  contract: string;
  empty: string;
  primaryAction: string;
};
type FieldConfig = {
  key: string;
  label: string;
  type?: "text" | "textarea" | "select" | "date" | "number";
  options?: string[];
  readonly?: boolean;
  required?: boolean;
  createOnly?: boolean;
  helper?: string;
};
type FormMode = "edit" | "create";

type AppState = {
  rows: Row[];
  selected: Row | null;
  form: Row;
  mode: FormMode;
  loading: boolean;
  saving: boolean;
  error: string;
  fieldErrors: Record<string, string>;
  success: string;
  permissionDenied: boolean;
};

const yn = ["Y", "N"];
const roleCodes = [
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
const screens: Screen[] = [
  {
    id: "SCR-USERS",
    title: "사용자 관리",
    route: "/admin/users",
    group: "사용자·조직 관리",
    kind: "users",
    icon: Users,
    purpose:
      "KORUS 원천 인사 정보는 읽기 전용으로 확인하고 내부 사용여부와 업무 역할만 저장합니다.",
    contract:
      "조회 → 사용자 선택 → 시스템 사용여부/업무 역할 변경 → 저장 확인 → 같은 조건 재조회",
    empty: "조건에 맞는 사용자가 없습니다. 검색 조건을 조정해 주세요.",
    primaryAction: "사용자 접근값 저장",
  },
  {
    id: "SCR-ORGS",
    title: "조직 관리",
    route: "/admin/organizations",
    group: "사용자·조직 관리",
    kind: "orgs",
    icon: Building2,
    purpose: "조직 코드를 조회하고 로컬 상하위 관계와 적용기간을 저장합니다.",
    contract:
      "조직 목록/계층 조회 → 조직 선택 → 상위조직·기간 저장 → tree/detail refresh",
    empty: "조직 데이터가 없습니다. 관계 저장은 대상 선택 후 가능합니다.",
    primaryAction: "관계 저장",
  },
  {
    id: "SCR-ROLES",
    title: "역할 관리",
    route: "/admin/roles",
    group: "역할·권한 관리",
    kind: "roles",
    icon: ShieldCheck,
    purpose: "R01~R09 역할의 목적, 부여 기준, 데이터 범위 기본값을 관리합니다.",
    contract: "역할 조회 → role_code 불변 확인 → 기준/범위 저장 → 역할 재조회",
    empty:
      "R01~R09 역할 seed가 조회되지 않았습니다. 백엔드 seed 상태를 확인해 주세요.",
    primaryAction: "역할 기준 저장",
  },
  {
    id: "SCR-USER-ROLES",
    title: "사용자 역할 관리",
    route: "/admin/user-roles",
    group: "역할·권한 관리",
    kind: "userRoles",
    icon: KeyRound,
    purpose: "사용자별 역할 부여·변경·회수와 승인자, 유효기간을 기록합니다.",
    contract:
      "역할 assignment 조회 → 역할/기간/승인자/상태 변경 → 저장 확인 → 재조회",
    empty: "현재 역할 assignment가 없습니다.",
    primaryAction: "역할 assignment 저장",
  },
  {
    id: "SCR-MENU-PERMISSIONS",
    title: "메뉴 권한 관리",
    route: "/admin/menu-permissions",
    group: "역할·권한 관리",
    kind: "permissions",
    icon: LockKeyhole,
    purpose: "역할·조직·사용자별 메뉴 접근 허용 matrix를 저장합니다.",
    contract:
      "대상유형/대상ID 조회 → 접근 허용 Y/N 변경 → 권한 저장 → navigation 반영 안내",
    empty:
      "대상 기준 권한 matrix가 없습니다. 대상유형과 대상ID를 확인해 주세요.",
    primaryAction: "권한 저장",
  },
  {
    id: "SCR-MENU-STRUCTURE",
    title: "메뉴 구조 관리",
    route: "/admin/menu-structure",
    group: "메뉴 관리",
    kind: "menuStructure",
    icon: FolderTree,
    purpose: "대·중·소 3단계 메뉴의 부모와 동일 계층 표시순서를 관리합니다.",
    contract: "메뉴 tree 조회 → 노드 선택 → 부모/순서 저장 → tree refresh",
    empty: "메뉴 seed가 조회되지 않았습니다. 메뉴 구조를 저장할 수 없습니다.",
    primaryAction: "구조/순서 저장",
  },
  {
    id: "SCR-MENU-INFO",
    title: "메뉴 정보 관리",
    route: "/admin/menu-info",
    group: "메뉴 관리",
    kind: "menus",
    icon: Route,
    purpose: "메뉴 실행정보와 screenId/url 연결을 등록·수정합니다.",
    contract: "실행정보 조회 → 등록/수정 form → 저장 확인 → 목록 refresh",
    empty:
      "실행정보 메뉴가 없습니다. 등록 버튼으로 새 메뉴 연결을 만들 수 있습니다.",
    primaryAction: "메뉴 실행정보 저장",
  },
  {
    id: "SCR-CODE-GROUPS",
    title: "코드그룹 관리",
    route: "/admin/code-groups",
    group: "공통코드 관리",
    kind: "codeGroups",
    icon: Network,
    purpose:
      "코드그룹 기준정보를 조회·등록·수정하고 선택 groupId로 상세코드 화면에 이동합니다.",
    contract:
      "코드그룹 조회 → 등록/수정 저장 → 재조회 또는 상세코드 local navigation",
    empty: "코드그룹이 없습니다. 등록 form으로 그룹을 생성할 수 있습니다.",
    primaryAction: "코드그룹 저장",
  },
  {
    id: "SCR-CODE-DETAILS",
    title: "상세코드 관리",
    route: "/admin/code-details",
    group: "공통코드 관리",
    kind: "codeDetails",
    icon: Code2,
    purpose:
      "groupId별 상세코드, 상위코드 계층, 정렬순서, 추가속성을 관리합니다.",
    contract:
      "groupId 기준 조회 → 상세코드 등록/수정 → 같은 groupId 목록 refresh",
    empty:
      "선택 groupId에 상세코드가 없습니다. 등록 form으로 상세코드를 생성할 수 있습니다.",
    primaryAction: "상세코드 저장",
  },
];

const listColumns: Record<ScreenKind, FieldConfig[]> = {
  users: [
    { key: "employeeNo", label: "교번" },
    { key: "name", label: "성명" },
    { key: "organizationName", label: "소속" },
    { key: "rank", label: "직급" },
    { key: "employmentStatus", label: "재직상태" },
    { key: "roleCodes", label: "역할" },
    { key: "systemEnabled", label: "사용여부" },
    { key: "position", label: "보직" },
    { key: "retirementDate", label: "퇴직일자" },
    { key: "lastSyncedAt", label: "최종 동기화" },
  ],
  orgs: [
    { key: "orgCode", label: "조직코드" },
    { key: "orgName", label: "조직명" },
    { key: "orgType", label: "조직유형" },
    { key: "useYn", label: "사용여부" },
  ],
  roles: [
    { key: "roleCode", label: "역할코드" },
    { key: "roleName", label: "역할명" },
    { key: "purpose", label: "목적" },
    { key: "grantCriteria", label: "부여 기준" },
    { key: "dataScopeDefault", label: "데이터 범위 기본값" },
    { key: "useYn", label: "사용여부" },
  ],
  userRoles: [
    { key: "employeeNo", label: "교번" },
    { key: "name", label: "성명" },
    { key: "roleCode", label: "역할코드" },
    { key: "roleName", label: "역할명" },
    { key: "effectiveStartDate", label: "유효 시작일" },
    { key: "effectiveEndDate", label: "유효 종료일" },
    { key: "approverUserId", label: "승인자" },
    { key: "assignmentType", label: "부여유형" },
    { key: "status", label: "상태" },
  ],
  permissions: [
    { key: "targetType", label: "대상유형" },
    { key: "targetId", label: "대상ID" },
    { key: "topMenuName", label: "대메뉴" },
    { key: "middleMenuName", label: "중메뉴" },
    { key: "menuName", label: "화면" },
    { key: "allowAccess", label: "접근 허용" },
  ],
  menuStructure: [
    { key: "menuName", label: "메뉴명" },
    { key: "menuLevel", label: "레벨" },
    { key: "sortOrder", label: "표시순서" },
    { key: "screenId", label: "화면ID" },
    { key: "url", label: "URL" },
  ],
  menus: [
    { key: "menuName", label: "메뉴명" },
    { key: "screenId", label: "화면ID" },
    { key: "url", label: "URL" },
    { key: "icon", label: "아이콘" },
    { key: "businessCategory", label: "업무구분" },
    { key: "description", label: "설명" },
    { key: "useYn", label: "사용여부" },
  ],
  codeGroups: [
    { key: "groupId", label: "그룹ID" },
    { key: "groupName", label: "명칭" },
    { key: "description", label: "설명" },
    { key: "managingDepartment", label: "관리부서" },
    { key: "useYn", label: "사용여부" },
  ],
  codeDetails: [
    { key: "groupId", label: "그룹ID" },
    { key: "codeValue", label: "코드값" },
    { key: "codeName", label: "코드명" },
    { key: "parentCodeValue", label: "상위코드" },
    { key: "sortOrder", label: "정렬순서" },
    { key: "extraAttributes", label: "추가속성" },
    { key: "useYn", label: "사용여부" },
  ],
};

const formFields: Record<ScreenKind, FieldConfig[]> = {
  users: [
    {
      key: "employeeNo",
      label: "교번",
      readonly: true,
      helper: "KORUS 원천 readonly",
    },
    {
      key: "name",
      label: "성명",
      readonly: true,
      helper: "KORUS 원천 readonly",
    },
    { key: "organizationName", label: "소속", readonly: true },
    { key: "rank", label: "직급", readonly: true },
    { key: "employmentStatus", label: "재직상태", readonly: true },
    { key: "position", label: "보직", readonly: true },
    { key: "retirementDate", label: "퇴직일자", readonly: true },
    { key: "lastSyncedAt", label: "최종 동기화일시", readonly: true },
    {
      key: "systemEnabled",
      label: "시스템 사용여부",
      type: "select",
      options: yn,
      required: true,
    },
    {
      key: "roleCodes",
      label: "업무 역할",
      helper: "쉼표로 여러 roleCode 입력: R01,R09",
    },
  ],
  orgs: [
    { key: "orgCode", label: "조직코드", readonly: true },
    { key: "orgName", label: "조직명", readonly: true },
    { key: "orgType", label: "조직유형", readonly: true },
    { key: "parentOrgCode", label: "상위조직 코드" },
    {
      key: "effectiveStartDate",
      label: "적용 시작일",
      type: "date",
      required: true,
    },
    { key: "effectiveEndDate", label: "적용 종료일", type: "date" },
  ],
  roles: [
    {
      key: "roleCode",
      label: "역할코드",
      readonly: true,
      helper: "R01~R09 불변 식별자",
    },
    { key: "roleName", label: "역할명", required: true },
    { key: "purpose", label: "목적", type: "textarea", required: true },
    { key: "grantCriteria", label: "부여 기준", type: "textarea" },
    { key: "dataScopeDefault", label: "데이터 범위 기본값", type: "textarea" },
    {
      key: "useYn",
      label: "사용여부",
      type: "select",
      options: yn,
      required: true,
    },
  ],
  userRoles: [
    { key: "employeeNo", label: "교번", readonly: true },
    { key: "name", label: "성명", readonly: true },
    {
      key: "roleCode",
      label: "역할",
      type: "select",
      options: roleCodes,
      required: true,
    },
    {
      key: "effectiveStartDate",
      label: "유효 시작일",
      type: "date",
      required: true,
    },
    { key: "effectiveEndDate", label: "유효 종료일", type: "date" },
    { key: "approverUserId", label: "승인자 userId" },
    {
      key: "assignmentType",
      label: "부여유형",
      type: "select",
      options: ["POSITION_BASED", "MANUAL"],
    },
    {
      key: "status",
      label: "상태",
      type: "select",
      options: ["ACTIVE", "REVOKED", "EXPIRED"],
    },
  ],
  permissions: [
    {
      key: "targetType",
      label: "대상유형",
      type: "select",
      options: ["ROLE", "ORGANIZATION", "USER"],
      required: true,
    },
    { key: "targetId", label: "대상ID", required: true },
    { key: "menuName", label: "화면", readonly: true },
    {
      key: "allowAccess",
      label: "접근 허용",
      type: "select",
      options: yn,
      required: true,
    },
  ],
  menuStructure: [
    { key: "menuId", label: "메뉴ID", readonly: true },
    { key: "menuName", label: "메뉴명", readonly: true },
    { key: "parentMenuId", label: "부모메뉴ID" },
    { key: "menuLevel", label: "메뉴 레벨", type: "number", required: true },
    {
      key: "sortOrder",
      label: "동일계층 표시순서",
      type: "number",
      required: true,
    },
  ],
  menus: [
    { key: "menuName", label: "메뉴명", required: true },
    { key: "screenId", label: "화면ID", required: true },
    { key: "url", label: "URL", required: true },
    { key: "icon", label: "아이콘" },
    { key: "businessCategory", label: "업무구분", required: true },
    { key: "description", label: "설명", type: "textarea" },
    {
      key: "useYn",
      label: "사용여부",
      type: "select",
      options: yn,
      required: true,
    },
  ],
  codeGroups: [
    {
      key: "groupId",
      label: "그룹ID",
      required: true,
      createOnly: true,
      helper: "등록 후 식별자는 유지됩니다.",
    },
    { key: "groupName", label: "명칭", required: true },
    { key: "description", label: "설명", type: "textarea" },
    { key: "managingDepartment", label: "관리부서" },
    {
      key: "useYn",
      label: "사용여부",
      type: "select",
      options: yn,
      required: true,
    },
  ],
  codeDetails: [
    { key: "groupId", label: "그룹ID", required: true, createOnly: true },
    { key: "codeValue", label: "코드값", required: true, createOnly: true },
    { key: "codeName", label: "코드명", required: true },
    { key: "parentCodeValue", label: "상위코드" },
    { key: "sortOrder", label: "정렬순서", type: "number" },
    { key: "extraAttributes", label: "추가속성 JSON", type: "textarea" },
    {
      key: "useYn",
      label: "사용여부",
      type: "select",
      options: yn,
      required: true,
    },
  ],
};

const createCapable = new Set<ScreenKind>([
  "menus",
  "codeGroups",
  "codeDetails",
]);
const treeKinds = new Set<ScreenKind>(["orgs", "menuStructure", "codeDetails"]);

function currentScreen() {
  return screens.find((s) => location.pathname === s.route) ?? screens[0];
}

function groupScreens(): Array<[string, Screen[]]> {
  const groups = new Map<string, Screen[]>();
  for (const screen of screens) {
    groups.set(screen.group, [...(groups.get(screen.group) ?? []), screen]);
  }
  return [...groups.entries()];
}

function flatten(rows: Row[], depth = 0): Row[] {
  return rows.flatMap((row) => [
    { ...row, __depth: depth },
    ...flatten(row.children ?? [], depth + 1),
  ]);
}

function endpoint(kind: ScreenKind, filter: string, form: Row) {
  const params = new URLSearchParams();
  if (filter.trim()) params.set("filter", filter.trim());
  if (kind === "permissions") {
    params.set("targetType", String(form.targetType || "ROLE"));
    params.set("targetId", String(form.targetId || "R09"));
    return `/api/admin/menu-permissions?${params.toString()}`;
  }
  if (kind === "codeDetails") {
    const groupId = String(
      form.groupId || new URLSearchParams(location.search).get("groupId") || "",
    );
    if (groupId) params.set("groupId", groupId);
  }
  const base: Record<ScreenKind, string> = {
    users: "/api/admin/users",
    orgs: "/api/admin/organizations",
    roles: "/api/admin/roles",
    userRoles: "/api/admin/user-roles",
    permissions: "/api/admin/menu-permissions",
    menuStructure: "/api/admin/menus/tree",
    menus: "/api/admin/menus",
    codeGroups: "/api/admin/code-groups",
    codeDetails: "/api/admin/code-details",
  };
  const query = params.toString();
  return query ? `${base[kind]}?${query}` : base[kind];
}

function defaultForm(kind: ScreenKind): Row {
  const query = new URLSearchParams(location.search);
  const defaults: Record<ScreenKind, Row> = {
    users: { systemEnabled: "Y", roleCodes: "" },
    orgs: { effectiveStartDate: "", effectiveEndDate: "" },
    roles: { useYn: "Y" },
    userRoles: {
      roleCode: "R09",
      assignmentType: "MANUAL",
      status: "ACTIVE",
      effectiveStartDate: "",
    },
    permissions: { targetType: "ROLE", targetId: "R09", allowAccess: "Y" },
    menuStructure: { menuLevel: 3, sortOrder: 0 },
    menus: {
      menuName: "",
      screenId: "",
      url: "",
      icon: "",
      businessCategory: "",
      description: "",
      useYn: "Y",
    },
    codeGroups: {
      groupId: "",
      groupName: "",
      description: "",
      managingDepartment: "",
      useYn: "Y",
    },
    codeDetails: {
      groupId: query.get("groupId") ?? "",
      codeValue: "",
      codeName: "",
      parentCodeValue: "",
      sortOrder: 0,
      extraAttributes: "{}",
      useYn: "Y",
    },
  };
  return defaults[kind];
}

function badgeClass(value: unknown) {
  const text = String(value ?? "");
  if (["Y", "ACTIVE", "R09"].includes(text))
    return "border-emerald-200 bg-emerald-50 text-emerald-700";
  if (["N", "REVOKED", "DISABLED"].includes(text))
    return "border-rose-200 bg-rose-50 text-rose-700";
  if (["POSITION_BASED", "MANUAL"].includes(text) || /^R0\d$/.test(text))
    return "border-blue-200 bg-blue-50 text-blue-700";
  return "border-slate-200 bg-slate-50 text-slate-700";
}

function displayValue(row: Row, key: string) {
  const value = row[key];
  if (value == null) return "";
  if (Array.isArray(value)) return value.join(", ");
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}

function navigate(route: string) {
  history.pushState(null, "", route);
  dispatchEvent(new PopStateEvent("popstate"));
}

function Login() {
  const [loginId, setLoginId] = useState("admin");
  const [password, setPassword] = useState("admin");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError("");
    try {
      await api("/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ loginId, password }),
      });
      navigate("/admin/users");
    } catch (err) {
      setError(err instanceof Error ? err.message : "credential 오류");
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="grid min-h-svh bg-[radial-gradient(circle_at_top_left,_rgba(37,99,235,0.16),_transparent_32%),linear-gradient(135deg,#f8fbff,#eef4ff_55%,#f8fafc)] lg:grid-cols-2">
      <section className="flex items-center justify-center px-6 py-10">
        <form
          onSubmit={submit}
          className="w-full max-w-sm rounded-3xl border border-white/70 bg-white/90 p-8 shadow-2xl shadow-blue-950/10 backdrop-blur"
        >
          <div className="mb-8 flex items-center gap-3">
            <span className="inline-flex size-11 items-center justify-center rounded-2xl bg-blue-700 text-white shadow-lg shadow-blue-700/25">
              <Sparkles className="size-5" />
            </span>
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-blue-700">
                KNUE CMS
              </p>
              <h1 className="text-xl font-semibold tracking-tight">
                교수업적평가시스템
              </h1>
            </div>
          </div>
          <div className="mb-6 space-y-2">
            <h2 className="text-2xl font-semibold tracking-tight">
              관리자 로그인
            </h2>
            <p className="text-sm leading-6 text-slate-500">
              R09 시스템 관리 메뉴에 접근하려면 세션을 생성해 주세요.
            </p>
          </div>
          <label className="mb-4 block text-sm font-medium">
            loginId
            <input
              className="mt-2 h-10 w-full rounded-xl border border-slate-200 bg-white px-3 outline-none transition focus:border-blue-500 focus:ring-4 focus:ring-blue-100"
              value={loginId}
              onChange={(e) => setLoginId(e.target.value)}
              required
            />
          </label>
          <label className="mb-4 block text-sm font-medium">
            password
            <input
              className="mt-2 h-10 w-full rounded-xl border border-slate-200 bg-white px-3 outline-none transition focus:border-blue-500 focus:ring-4 focus:ring-blue-100"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </label>
          {error && (
            <div
              role="alert"
              className="mb-4 rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700"
            >
              오류: {error}
            </div>
          )}
          <button
            disabled={loading}
            className="inline-flex h-10 w-full items-center justify-center gap-2 rounded-xl bg-blue-700 px-4 text-sm font-semibold text-white shadow-lg shadow-blue-700/20 transition hover:bg-blue-800 disabled:opacity-60"
          >
            {loading ? (
              <Loader2 className="size-4 animate-spin" />
            ) : (
              <LogIn className="size-4" />
            )}
            로그인
          </button>
        </form>
      </section>
      <section className="relative hidden overflow-hidden border-l bg-slate-950 p-10 text-white lg:block">
        <div className="absolute -right-24 -top-24 size-72 rounded-full bg-blue-500/20 blur-3xl" />
        <div className="absolute bottom-20 left-12 size-48 rounded-full bg-cyan-400/10 blur-3xl" />
        <div className="relative flex h-full flex-col justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.2em] text-blue-200">
              Public institution admin
            </p>
            <h2 className="mt-4 max-w-xl text-4xl font-semibold tracking-[-0.04em]">
              사용자·조직·역할·메뉴 권한을 API 기반으로 운영합니다.
            </h2>
          </div>
          <div className="grid gap-4 rounded-3xl border border-white/10 bg-white/10 p-5 shadow-2xl backdrop-blur">
            {[
              "KORUS 원천 정보 readonly",
              "R09 권한 기반 navigation",
              "저장 후 owning list/tree refresh",
            ].map((text) => (
              <div
                key={text}
                className="flex items-center gap-3 rounded-2xl bg-white/10 p-4"
              >
                <BadgeCheck className="size-5 text-cyan-200" />
                <span className="text-sm text-slate-100">{text}</span>
              </div>
            ))}
          </div>
        </div>
      </section>
    </main>
  );
}

function App() {
  const [routeKey, setRouteKey] = useState(
    `${location.pathname}${location.search}`,
  );
  useEffect(() => {
    const handler = () => setRouteKey(`${location.pathname}${location.search}`);
    addEventListener("popstate", handler);
    return () => removeEventListener("popstate", handler);
  }, []);
  if (location.pathname === "/login") return <Login />;
  return <Admin key={routeKey} screen={currentScreen()} />;
}

function Admin({ screen }: { screen: Screen }) {
  const [state, setState] = useState<AppState>({
    rows: [],
    selected: null,
    form: defaultForm(screen.kind),
    mode: "edit",
    loading: false,
    saving: false,
    error: "",
    fieldErrors: {},
    success: "",
    permissionDenied: false,
  });
  const [filter, setFilter] = useState("");
  const [me, setMe] = useState<Row | null>(null);
  const flatRows = useMemo(() => flatten(state.rows), [state.rows]);

  async function load(overrides: Row = {}) {
    const previousKey = rowKey(state.selected);
    setState((s) => ({
      ...s,
      loading: true,
      error: "",
      fieldErrors: {},
      permissionDenied: false,
      form: { ...s.form, ...overrides },
    }));
    try {
      const form = { ...state.form, ...overrides };
      const [data, currentUser] = await Promise.all([
        api<Row[]>(endpoint(screen.kind, filter, form)),
        api<Row>("/api/auth/me"),
      ]);
      const flattenedData = flatten(data);
      const first = flattenedData[0] ?? null;
      const nextSelected =
        flattenedData.find((row) => rowKey(row) === previousKey) ?? first;
      setMe(currentUser);
      setState((s) => ({
        ...s,
        rows: data,
        selected: nextSelected,
        form: nextSelected
          ? { ...defaultForm(screen.kind), ...nextSelected, ...overrides }
          : { ...defaultForm(screen.kind), ...overrides },
        mode: nextSelected ? "edit" : s.mode,
        loading: false,
      }));
    } catch (err) {
      handleError(err, false);
    }
  }

  useEffect(() => {
    load();
  }, [screen.kind]);

  function handleError(err: unknown, saving: boolean) {
    const apiError = err instanceof ApiRequestError ? err : null;
    setState((s) => ({
      ...s,
      loading: false,
      saving,
      error:
        err instanceof Error
          ? err.message
          : "요청 처리 중 오류가 발생했습니다.",
      fieldErrors: apiError?.fieldErrors ?? {},
      permissionDenied: apiError?.status === 403 || apiError?.status === 401,
    }));
  }

  function selectRow(row: Row) {
    setState((s) => ({
      ...s,
      selected: row,
      form: { ...defaultForm(screen.kind), ...row },
      mode: "edit",
      success: "",
      fieldErrors: {},
    }));
  }

  function startCreate() {
    setState((s) => ({
      ...s,
      selected: null,
      mode: "create",
      form: defaultForm(screen.kind),
      success: "",
      error: "",
      fieldErrors: {},
    }));
  }

  function cancelEdit() {
    setState((s) => ({
      ...s,
      mode: s.selected ? "edit" : "create",
      form: s.selected
        ? { ...defaultForm(screen.kind), ...s.selected }
        : defaultForm(screen.kind),
      fieldErrors: {},
      success: "",
    }));
  }

  async function save() {
    if (state.mode === "edit" && !state.selected) return;
    if (!confirm(`${screen.primaryAction} 내역을 저장하시겠습니까?`)) return;
    setState((s) => ({
      ...s,
      saving: true,
      error: "",
      success: "",
      fieldErrors: {},
    }));
    try {
      await saveForm(screen.kind, state.form, state.mode);
      if (screen.kind === "permissions") {
        await api("/api/admin/navigation");
      }
      setState((s) => ({
        ...s,
        saving: false,
        success: `${screen.primaryAction} 완료: 재조회된 값으로 갱신했습니다.`,
      }));
      await load(
        screen.kind === "permissions"
          ? { targetType: state.form.targetType, targetId: state.form.targetId }
          : {},
      );
    } catch (err) {
      handleError(err, false);
    }
  }

  const ScreenIcon = screen.icon;
  return (
    <div className="min-h-screen bg-slate-50 text-slate-950">
      <aside className="fixed inset-y-0 left-0 z-20 hidden w-72 border-r border-slate-200 bg-slate-950 p-4 text-white shadow-xl lg:block">
        <div className="mb-6 flex items-center gap-3 rounded-2xl bg-white/10 p-3">
          <span className="inline-flex size-10 items-center justify-center rounded-xl bg-blue-500 text-white">
            <Menu className="size-5" />
          </span>
          <div>
            <h1 className="font-semibold">교수업적평가시스템</h1>
            <p className="text-xs text-slate-300">R09 시스템관리자</p>
          </div>
        </div>
        {groupScreens().map(([group, items]) => (
          <section key={group} className="mb-5">
            <h2 className="mb-2 px-2 text-xs font-semibold uppercase tracking-[0.16em] text-slate-400">
              {group}
            </h2>
            <div className="space-y-1">
              {items.map((item) => {
                const Icon = item.icon;
                const active = item.route === screen.route;
                return (
                  <button
                    key={item.route}
                    onClick={() => navigate(item.route)}
                    className={`group flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left text-sm transition ${active ? "bg-blue-600 text-white shadow-lg shadow-blue-900/30" : "text-slate-300 hover:bg-white/10 hover:text-white"}`}
                  >
                    <Icon className="size-4" />
                    <span className="flex-1">{item.title}</span>
                    <ChevronRight
                      className={`size-4 transition ${active ? "translate-x-0 opacity-100" : "opacity-0 group-hover:translate-x-0.5 group-hover:opacity-70"}`}
                    />
                  </button>
                );
              })}
            </div>
          </section>
        ))}
      </aside>
      <main className="lg:pl-72">
        <div className="sticky top-0 z-10 border-b border-slate-200 bg-white/90 px-4 py-3 backdrop-blur lg:px-8">
          <div className="flex items-center justify-between gap-4">
            <div>
              <p className="text-xs font-medium text-slate-500">
                시스템 관리 &gt; {screen.group} &gt; {screen.title}
              </p>
              <p className="text-sm text-slate-500">
                {me
                  ? `${me.loginId ?? "admin"} · ${me.roleCodes ?? "R09"}`
                  : "세션 확인 중"}
              </p>
            </div>
            <button
              onClick={() => load()}
              disabled={state.loading}
              className="inline-flex h-9 items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 text-sm font-medium shadow-sm transition hover:bg-slate-50 disabled:opacity-60"
            >
              {state.loading ? (
                <Loader2 className="size-4 animate-spin" />
              ) : (
                <RefreshCw className="size-4" />
              )}
              새로고침
            </button>
          </div>
        </div>
        <div className="space-y-6 p-4 lg:p-8">
          <section className="relative overflow-hidden rounded-3xl border border-blue-100 bg-gradient-to-br from-blue-50 via-white to-cyan-50 p-6 shadow-sm">
            <div className="absolute -right-16 -top-16 size-44 rounded-full bg-blue-200/40 blur-3xl" />
            <div className="relative flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
              <div className="max-w-3xl">
                <p className="mb-3 inline-flex items-center gap-2 rounded-full border border-blue-200 bg-white/80 px-3 py-1 text-xs font-semibold uppercase tracking-[0.16em] text-blue-700">
                  <ScreenIcon className="size-3.5" /> {screen.id}
                </p>
                <h1 className="text-3xl font-semibold tracking-tight">
                  {screen.title}
                </h1>
                <p className="mt-3 text-sm leading-6 text-slate-600">
                  {screen.purpose}
                </p>
                <p className="mt-2 text-xs text-slate-500">
                  UI Contract: {screen.contract}
                </p>
              </div>
              <div className="flex flex-wrap gap-2">
                {createCapable.has(screen.kind) && (
                  <button
                    onClick={startCreate}
                    className="inline-flex h-10 items-center gap-2 rounded-xl bg-slate-900 px-4 text-sm font-semibold text-white shadow-sm transition hover:-translate-y-0.5 hover:shadow-md"
                  >
                    등록 모드
                  </button>
                )}
                <button
                  onClick={save}
                  disabled={
                    state.saving || (state.mode === "edit" && !state.selected)
                  }
                  className="inline-flex h-10 items-center gap-2 rounded-xl bg-blue-700 px-4 text-sm font-semibold text-white shadow-lg shadow-blue-700/20 transition hover:-translate-y-0.5 hover:bg-blue-800 disabled:pointer-events-none disabled:opacity-50"
                >
                  {state.saving ? (
                    <Loader2 className="size-4 animate-spin" />
                  ) : (
                    <Save className="size-4" />
                  )}
                  {screen.primaryAction}
                </button>
              </div>
            </div>
          </section>

          {state.permissionDenied ? (
            <PermissionState message={state.error} />
          ) : (
            <>
              <FilterPanel
                screen={screen}
                filter={filter}
                setFilter={setFilter}
                form={state.form}
                setForm={(form) => setState((s) => ({ ...s, form }))}
                load={load}
                loading={state.loading}
              />
              <Messages state={state} />
              <section
                className={`grid gap-6 ${treeKinds.has(screen.kind) ? "xl:grid-cols-[minmax(0,1.3fr)_minmax(380px,0.7fr)]" : "xl:grid-cols-[minmax(0,1.45fr)_minmax(380px,0.55fr)]"}`}
              >
                <DataRegion
                  screen={screen}
                  rows={flatRows}
                  selected={state.selected}
                  loading={state.loading}
                  onSelect={selectRow}
                />
                <DetailForm
                  screen={screen}
                  mode={state.mode}
                  form={state.form}
                  fieldErrors={state.fieldErrors}
                  setForm={(form) => setState((s) => ({ ...s, form }))}
                  save={save}
                  cancel={cancelEdit}
                  selected={state.selected}
                />
              </section>
              {screen.kind === "codeGroups" && (
                <CodeGroupAction selected={state.selected} />
              )}
              {screen.kind === "permissions" && <NavigationNotice />}
            </>
          )}
        </div>
      </main>
    </div>
  );
}

function FilterPanel({
  screen,
  filter,
  setFilter,
  form,
  setForm,
  load,
  loading,
}: {
  screen: Screen;
  filter: string;
  setFilter: (value: string) => void;
  form: Row;
  setForm: (value: Row) => void;
  load: (overrides?: Row) => void;
  loading: boolean;
}) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="mb-3 flex items-center gap-2 text-sm font-semibold">
        <Search className="size-4 text-blue-700" /> 조회 조건
      </div>
      <div className="grid gap-3 md:grid-cols-[minmax(0,1fr)_auto] md:items-end">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {screen.kind === "permissions" && (
            <>
              <SelectLike
                label="대상유형"
                value={String(form.targetType ?? "ROLE")}
                options={["ROLE", "ORGANIZATION", "USER"]}
                onChange={(value) => setForm({ ...form, targetType: value })}
              />
              <InputLike
                label="대상ID"
                value={String(form.targetId ?? "R09")}
                onChange={(value) => setForm({ ...form, targetId: value })}
              />
            </>
          )}
          {screen.kind === "codeDetails" && (
            <InputLike
              label="그룹ID"
              value={String(form.groupId ?? "")}
              onChange={(value) => setForm({ ...form, groupId: value })}
            />
          )}
          <InputLike
            label={filterLabel(screen.kind)}
            value={filter}
            onChange={setFilter}
            placeholder="API filter parameter"
          />
        </div>
        <button
          onClick={() =>
            load(
              screen.kind === "permissions" || screen.kind === "codeDetails"
                ? form
                : {},
            )
          }
          disabled={loading}
          className="inline-flex h-10 items-center justify-center gap-2 rounded-xl bg-slate-900 px-4 text-sm font-semibold text-white transition hover:bg-slate-800 disabled:opacity-60"
        >
          {loading ? (
            <Loader2 className="size-4 animate-spin" />
          ) : (
            <Search className="size-4" />
          )}
          조회
        </button>
      </div>
    </section>
  );
}

function filterLabel(kind: ScreenKind) {
  const labels: Record<ScreenKind, string> = {
    users: "교번·성명·소속",
    orgs: "조직코드·조직명",
    roles: "역할 조회는 전체 seed 기준",
    userRoles: "교번·성명·역할",
    permissions: "화면명 보조 검색",
    menuStructure: "tree는 전체 조회",
    menus: "메뉴명·업무구분",
    codeGroups: "그룹ID·명칭·관리부서",
    codeDetails: "코드값·코드명",
  };
  return labels[kind];
}

function Messages({ state }: { state: AppState }) {
  return (
    <>
      {state.error && (
        <div
          role="alert"
          className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700"
        >
          <AlertCircle className="mr-2 inline size-4" />
          {state.error}
        </div>
      )}
      {state.success && (
        <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-700">
          <CheckCircle2 className="mr-2 inline size-4" />
          {state.success}
        </div>
      )}
    </>
  );
}

function PermissionState({ message }: { message: string }) {
  return (
    <section className="rounded-3xl border border-amber-200 bg-amber-50 p-10 text-center text-amber-900 shadow-sm">
      <LockKeyhole className="mx-auto mb-4 size-12" />
      <h2 className="text-xl font-semibold">permission-denied</h2>
      <p className="mt-2 text-sm">
        {message || "R09 권한이 필요하거나 세션이 만료되었습니다."}
      </p>
      <button
        onClick={() => navigate("/login")}
        className="mt-6 rounded-xl bg-amber-900 px-4 py-2 text-sm font-semibold text-white"
      >
        로그인 화면으로 이동
      </button>
    </section>
  );
}

function DataRegion({
  screen,
  rows,
  selected,
  loading,
  onSelect,
}: {
  screen: Screen;
  rows: Row[];
  selected: Row | null;
  loading: boolean;
  onSelect: (row: Row) => void;
}) {
  return (
    <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
      <div className="flex items-center justify-between border-b border-slate-100 px-5 py-4">
        <div>
          <h2 className="font-semibold">
            {treeKinds.has(screen.kind) ? "계층/목록" : "조회 목록"}
          </h2>
          <p className="text-xs text-slate-500">API 응답 행 {rows.length}건</p>
        </div>
      </div>
      {loading ? (
        <SkeletonTable columns={listColumns[screen.kind].length} />
      ) : rows.length === 0 ? (
        <EmptyState text={screen.empty} />
      ) : (
        <div className="overflow-auto">
          <table className="w-full min-w-[920px] text-sm">
            <thead className="bg-slate-50 text-xs text-slate-500">
              <tr>
                {listColumns[screen.kind].map((c) => (
                  <th
                    key={c.key}
                    className="border-b border-slate-200 px-3 py-3 text-left font-semibold"
                  >
                    {c.label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((row, index) => {
                const active = selected && rowKey(row) === rowKey(selected);
                return (
                  <tr
                    key={`${rowKey(row)}-${index}`}
                    onClick={() => onSelect(row)}
                    className={`cursor-pointer border-b border-slate-100 transition hover:bg-blue-50/70 ${active ? "bg-blue-50" : ""}`}
                  >
                    {listColumns[screen.kind].map((c) => (
                      <td key={c.key} className="px-3 py-3 align-top">
                        <Cell row={row} field={c.key} />
                      </td>
                    ))}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function rowKey(row: Row | null) {
  if (!row) return "";
  return String(
    row.userId ??
      row.orgCode ??
      row.roleCode ??
      row.assignmentId ??
      row.permissionId ??
      row.menuId ??
      row.groupId ??
      row.detailId ??
      row.codeValue ??
      "",
  );
}

function Cell({ row, field }: { row: Row; field: string }) {
  const value = displayValue(row, field);
  const depth = Number(row.__depth ?? 0);
  if (
    [
      "useYn",
      "systemEnabled",
      "allowAccess",
      "status",
      "assignmentType",
      "roleCode",
      "roleCodes",
    ].includes(field)
  ) {
    return (
      <span
        className={`inline-flex rounded-full border px-2 py-0.5 text-xs font-medium ${badgeClass(value)}`}
      >
        {value}
      </span>
    );
  }
  if (field === "menuName" || field === "orgName" || field === "codeName") {
    return (
      <span
        style={{ paddingLeft: depth * 18 }}
        className="font-medium text-slate-900"
      >
        {depth > 0 ? "↳ " : ""}
        {value}
      </span>
    );
  }
  if (
    [
      "employeeNo",
      "orgCode",
      "groupId",
      "codeValue",
      "screenId",
      "url",
    ].includes(field)
  ) {
    return <span className="font-mono text-xs text-slate-700">{value}</span>;
  }
  return <span className="text-slate-700">{value}</span>;
}

function SkeletonTable({ columns }: { columns: number }) {
  return (
    <div className="space-y-3 p-5">
      {Array.from({ length: 6 }).map((_, row) => (
        <div
          key={row}
          className="grid animate-pulse gap-3"
          style={{
            gridTemplateColumns: `repeat(${columns}, minmax(80px, 1fr))`,
          }}
        >
          {Array.from({ length: columns }).map((__, col) => (
            <span key={col} className="h-8 rounded-lg bg-slate-100" />
          ))}
        </div>
      ))}
    </div>
  );
}

function EmptyState({ text }: { text: string }) {
  return (
    <div className="m-5 flex min-h-64 flex-col items-center justify-center rounded-2xl border border-dashed border-slate-300 bg-slate-50/70 p-10 text-center">
      <Search className="mb-4 size-10 text-slate-400" />
      <h3 className="font-semibold text-slate-900">empty</h3>
      <p className="mt-2 max-w-md text-sm leading-6 text-slate-500">{text}</p>
    </div>
  );
}

function DetailForm({
  screen,
  mode,
  form,
  fieldErrors,
  setForm,
  save,
  cancel,
  selected,
}: {
  screen: Screen;
  mode: FormMode;
  form: Row;
  fieldErrors: Record<string, string>;
  setForm: (value: Row) => void;
  save: () => void;
  cancel: () => void;
  selected: Row | null;
}) {
  const fields = formFields[screen.kind];
  const title =
    mode === "create" ? `${screen.title} 등록` : `${screen.title} 상세/수정`;
  return (
    <section className="rounded-2xl border border-slate-200 bg-white shadow-sm">
      <div className="border-b border-slate-100 px-5 py-4">
        <h2 className="font-semibold">{title}</h2>
        <p className="mt-1 text-xs text-slate-500">
          readonly/source-of-truth 필드는 저장 payload에서 변경하지 않습니다.
        </p>
      </div>
      {!selected && mode === "edit" ? (
        <EmptyState text="목록에서 행을 선택하면 상세와 저장 가능한 필드가 표시됩니다." />
      ) : (
        <div className="grid gap-4 p-5">
          {fields.map((field) => {
            const readonly =
              field.readonly || (field.createOnly && mode === "edit");
            return (
              <FieldInput
                key={field.key}
                field={field}
                value={displayValue(form, field.key)}
                readonly={readonly}
                error={fieldErrors[field.key]}
                onChange={(value) => setForm({ ...form, [field.key]: value })}
              />
            );
          })}
          <div className="sticky bottom-0 -mx-5 -mb-5 mt-2 flex flex-wrap gap-2 border-t border-slate-100 bg-white/90 p-5 backdrop-blur">
            <button
              onClick={save}
              className="inline-flex h-10 items-center gap-2 rounded-xl bg-blue-700 px-4 text-sm font-semibold text-white transition hover:bg-blue-800"
            >
              <Save className="size-4" /> 저장
            </button>
            <button
              onClick={cancel}
              className="h-10 rounded-xl border border-slate-200 px-4 text-sm font-medium transition hover:bg-slate-50"
            >
              취소
            </button>
          </div>
        </div>
      )}
    </section>
  );
}

function FieldInput({
  field,
  value,
  readonly,
  error,
  onChange,
}: {
  field: FieldConfig;
  value: string;
  readonly?: boolean;
  error?: string;
  onChange: (value: string) => void;
}) {
  const common = `mt-2 w-full rounded-xl border px-3 text-sm outline-none transition focus:border-blue-500 focus:ring-4 focus:ring-blue-100 ${readonly ? "border-slate-100 bg-slate-50 text-slate-500" : "border-slate-200 bg-white"}`;
  return (
    <label className="block text-sm font-medium text-slate-700">
      {field.label}
      {field.required && <span className="text-rose-600"> *</span>}
      {field.type === "textarea" ? (
        <textarea
          rows={3}
          readOnly={readonly}
          className={`${common} py-2`}
          value={value}
          onChange={(e) => onChange(e.target.value)}
        />
      ) : field.type === "select" ? (
        <select
          disabled={readonly}
          className={`${common} h-10`}
          value={value}
          onChange={(e) => onChange(e.target.value)}
        >
          {(field.options ?? []).map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
      ) : (
        <input
          readOnly={readonly}
          type={field.type ?? "text"}
          className={`${common} h-10`}
          value={value}
          onChange={(e) => onChange(e.target.value)}
        />
      )}
      {field.helper && (
        <span className="mt-1 block text-xs font-normal text-slate-500">
          {field.helper}
        </span>
      )}
      {error && (
        <span className="mt-1 block text-xs font-normal text-rose-600">
          {error}
        </span>
      )}
    </label>
  );
}

function InputLike({
  label,
  value,
  onChange,
  placeholder,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
}) {
  return (
    <label className="text-sm font-medium text-slate-700">
      {label}
      <input
        className="mt-2 h-10 w-full rounded-xl border border-slate-200 px-3 text-sm outline-none transition focus:border-blue-500 focus:ring-4 focus:ring-blue-100"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
      />
    </label>
  );
}

function SelectLike({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: string;
  options: string[];
  onChange: (value: string) => void;
}) {
  return (
    <label className="text-sm font-medium text-slate-700">
      {label}
      <select
        className="mt-2 h-10 w-full rounded-xl border border-slate-200 px-3 text-sm outline-none transition focus:border-blue-500 focus:ring-4 focus:ring-blue-100"
        value={value}
        onChange={(e) => onChange(e.target.value)}
      >
        {options.map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </select>
    </label>
  );
}

function CodeGroupAction({ selected }: { selected: Row | null }) {
  return (
    <section className="rounded-2xl border border-blue-100 bg-blue-50 p-4 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h3 className="font-semibold text-blue-950">상세코드 이동</h3>
          <p className="text-sm text-blue-700">
            선택한 groupId를 유지해 /admin/code-details?groupId=&lt;selected&gt;
            로 이동합니다. API 호출이 아닌 local navigation입니다.
          </p>
        </div>
        <button
          disabled={!selected?.groupId}
          onClick={() =>
            navigate(
              `/admin/code-details?groupId=${encodeURIComponent(selected?.groupId)}`,
            )
          }
          className="rounded-xl bg-blue-700 px-4 py-2 text-sm font-semibold text-white transition hover:bg-blue-800 disabled:opacity-50"
        >
          상세코드 이동
        </button>
      </div>
    </section>
  );
}

function NavigationNotice() {
  return (
    <section className="rounded-2xl border border-emerald-100 bg-emerald-50 p-4 text-sm text-emerald-800">
      권한 저장 성공 시 GET /api/admin/navigation을 재호출해 navigation 반영
      여부를 확인합니다. 미허용 메뉴는 서버 권한 정책에 따라 직접 API 요청도
      403으로 차단됩니다.
    </section>
  );
}

function payload(kind: ScreenKind, form: Row, mode: FormMode): Row {
  if (kind === "users")
    return {
      systemEnabled: form.systemEnabled,
      roleCodes: String(form.roleCodes ?? "")
        .split(",")
        .map((r) => r.trim())
        .filter(Boolean),
    };
  if (kind === "orgs")
    return {
      parentOrgCode: form.parentOrgCode || null,
      effectiveStartDate: form.effectiveStartDate,
      effectiveEndDate: form.effectiveEndDate || null,
    };
  if (kind === "roles")
    return {
      roleName: form.roleName,
      purpose: form.purpose,
      grantCriteria: form.grantCriteria,
      dataScopeDefault: form.dataScopeDefault,
      useYn: form.useYn,
    };
  if (kind === "permissions")
    return {
      targetType: form.targetType,
      targetId: form.targetId,
      permissions: [{ menuId: form.menuId, allowAccess: form.allowAccess }],
    };
  if (kind === "userRoles")
    return {
      assignments: [
        {
          roleCode: form.roleCode,
          effectiveStartDate: form.effectiveStartDate,
          effectiveEndDate: form.effectiveEndDate || null,
          approverUserId: form.approverUserId || null,
          assignmentType: form.assignmentType || "MANUAL",
          status: form.status || "ACTIVE",
        },
      ],
    };
  if (kind === "menuStructure")
    return {
      parentMenuId: form.parentMenuId || null,
      menuLevel: Number(form.menuLevel),
      sortOrder: Number(form.sortOrder),
      menuId: form.menuId,
    };
  if (kind === "menus")
    return {
      menuName: form.menuName,
      screenId: form.screenId,
      url: form.url,
      icon: form.icon ?? "",
      businessCategory: form.businessCategory,
      description: form.description ?? "",
      useYn: form.useYn ?? "Y",
    };
  if (kind === "codeGroups")
    return {
      groupId: form.groupId,
      groupName: form.groupName,
      description: form.description ?? "",
      managingDepartment: form.managingDepartment ?? "",
      useYn: form.useYn ?? "Y",
    };
  if (kind === "codeDetails")
    return {
      groupId: form.groupId,
      codeValue: form.codeValue,
      codeName: form.codeName,
      parentCodeValue: form.parentCodeValue || null,
      sortOrder: Number(form.sortOrder ?? 0),
      extraAttributes: form.extraAttributes || "{}",
      useYn: form.useYn ?? "Y",
    };
  return mode === "create" ? form : { ...form };
}

async function saveForm(kind: ScreenKind, form: Row, mode: FormMode) {
  const body = payload(kind, form, mode);
  if (kind === "roles")
    return api(`/api/admin/roles/${encodeURIComponent(form.roleCode)}`, {
      method: "PUT",
      body: JSON.stringify(body),
    });
  if (kind === "orgs")
    return api(
      `/api/admin/organizations/${encodeURIComponent(form.orgCode)}/relationship`,
      { method: "PUT", body: JSON.stringify(body) },
    );
  if (kind === "permissions")
    return api("/api/admin/menu-permissions", {
      method: "PUT",
      body: JSON.stringify(body),
    });
  if (kind === "menuStructure") {
    await api(`/api/admin/menus/${encodeURIComponent(form.menuId)}/structure`, {
      method: "PUT",
      body: JSON.stringify(body),
    });
    return api("/api/admin/menus/order", {
      method: "PATCH",
      body: JSON.stringify({
        orders: [{ menuId: form.menuId, sortOrder: Number(form.sortOrder) }],
      }),
    });
  }
  if (kind === "userRoles")
    return api(`/api/admin/users/${encodeURIComponent(form.userId)}/roles`, {
      method: "PUT",
      body: JSON.stringify(body),
    });
  if (kind === "users")
    return api(`/api/admin/users/${encodeURIComponent(form.userId)}`, {
      method: "PATCH",
      body: JSON.stringify(body),
    });
  if (kind === "menus")
    return api(
      mode === "create"
        ? "/api/admin/menus"
        : `/api/admin/menus/${encodeURIComponent(form.menuId)}`,
      {
        method: mode === "create" ? "POST" : "PUT",
        body: JSON.stringify(body),
      },
    );
  if (kind === "codeGroups")
    return api(
      mode === "create"
        ? "/api/admin/code-groups"
        : `/api/admin/code-groups/${encodeURIComponent(form.groupId)}`,
      {
        method: mode === "create" ? "POST" : "PUT",
        body: JSON.stringify(body),
      },
    );
  if (kind === "codeDetails")
    return api(
      mode === "create"
        ? "/api/admin/code-details"
        : `/api/admin/code-details/${encodeURIComponent(form.detailId)}`,
      {
        method: mode === "create" ? "POST" : "PUT",
        body: JSON.stringify(body),
      },
    );
}

createRoot(document.getElementById("root")!).render(<App />);
