import {
  cleanup,
  fireEvent,
  render,
  screen,
  within,
  waitFor,
} from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "../App";

const mockFetch = vi.fn(async (input: RequestInfo | URL) => {
  const url = String(input);
  if (url.includes("/api/auth/me")) {
    return new Response(
      JSON.stringify({
        success: true,
        data: {
          userId: "U-ADMIN",
          loginId: "admin",
          name: "관리자",
          roles: ["R09"],
        },
      }),
      { status: 200 },
    );
  }
  if (url.includes("/api/navigation/menus")) {
    return new Response(
      JSON.stringify({
        success: true,
        data: {
          items: [
            {
              menuId: "MENU-USER",
              menuName: "사용자 관리",
              url: "/admin/users",
            },
            {
              menuId: "MENU-ORG",
              menuName: "조직 관리",
              url: "/admin/organizations",
            },
            {
              menuId: "MENU-ROLE",
              menuName: "역할 관리",
              url: "/admin/roles",
            },
            {
              menuId: "MENU-USER-ROLE",
              menuName: "사용자 역할 관리",
              url: "/admin/user-roles",
            },
            {
              menuId: "MENU-PERM",
              menuName: "메뉴 권한 관리",
              url: "/admin/menu-permissions",
            },
            {
              menuId: "MENU-STRUCT",
              menuName: "메뉴 구조 관리",
              url: "/admin/menu-structure",
            },
            {
              menuId: "MENU-INFO",
              menuName: "메뉴 정보 관리",
              url: "/admin/menu-info",
            },
            {
              menuId: "MENU-CODE-GROUP",
              menuName: "코드그룹 관리",
              url: "/admin/code-groups",
            },
            {
              menuId: "MENU-CODE-DETAIL",
              menuName: "상세코드 관리",
              url: "/admin/code-groups/COMMON_YN/codes",
            },
            {
              menuId: "MENU-BATCH-DEF",
              menuName: "배치 정의 관리",
              url: "/admin/batch-definitions",
            },
            {
              menuId: "MENU-BATCH-EXEC",
              menuName: "배치 실행 관리",
              url: "/admin/batch-executions",
            },
            {
              menuId: "MENU-BATCH-RESULT",
              menuName: "배치 결과 조회",
              url: "/admin/batch-results",
            },
            {
              menuId: "MENU-BATCH-REPROCESS",
              menuName: "배치 오류 재처리",
              url: "/admin/batch-reprocess",
            },
          ],
        },
      }),
      { status: 200 },
    );
  }
  if (url.includes("/api/menus/tree")) {
    return new Response(
      JSON.stringify({
        success: true,
        data: {
          items: [
            {
              menuId: "MENU-USER",
              menuName: "사용자 관리",
              menuLevel: "LEAF",
              parentMenuId: "MENU-ROOT",
              displayOrder: 10,
            },
          ],
        },
      }),
      { status: 200 },
    );
  }
  if (url.includes("/api/code-groups")) {
    return new Response(
      JSON.stringify({
        success: true,
        data: {
          items: [
            {
              groupId: "COMMON_YN",
              groupName: "공통 YN",
              description: "Y/N 공통코드",
              managementDepartment: "SYSTEM",
              useYn: "Y",
            },
          ],
        },
      }),
      { status: 200 },
    );
  }
  if (url.includes("/api/batch-results/EXEC-SEED-FAILED")) {
    return new Response(
      JSON.stringify({
        success: true,
        data: {
          executionId: "EXEC-SEED-FAILED",
          batchId: "BATCH-EVAL-DATA",
          executionStatus: "FAILED",
          processedCount: 20,
          successCount: 18,
          failureCount: 2,
          excludedCount: 0,
          elapsedSeconds: 300,
          logFilePath: "/var/log/batch/EXEC-SEED-FAILED.log",
        },
      }),
      { status: 200 },
    );
  }
  if (url.includes("/api/batch-executions")) {
    return new Response(
      JSON.stringify({
        success: true,
        data: {
          items: [
            {
              executionId: "EXEC-SEED-FAILED",
              batchId: "BATCH-EVAL-DATA",
              operationType: "MANUAL_RUN",
              executionStatus: "FAILED",
            },
          ],
        },
      }),
      { status: 200 },
    );
  }
  if (url.includes("/api/batch-definitions")) {
    return new Response(
      JSON.stringify({
        success: true,
        data: {
          items: [
            {
              batchId: "BATCH-EVAL-DATA",
              batchType: "EVALUATION_DATA",
              scheduleCycle: "DAILY 02:00",
              maxExecutionSeconds: 3600,
              ownerUserId: "U-ADMIN",
              useYn: "Y",
            },
          ],
        },
      }),
      { status: 200 },
    );
  }
  if (url.includes("/api/batch-reprocess-targets")) {
    return new Response(
      JSON.stringify({
        success: true,
        data: {
          items: [
            {
              targetId: "RPT-EXEC-SEED-FAILED",
              originalExecutionId: "EXEC-SEED-FAILED",
              targetType: "EXECUTION",
              failureReference: "EXEC-SEED-FAILED",
              failureStatus: "FAILED",
            },
          ],
        },
      }),
      { status: 200 },
    );
  }
  return new Response(JSON.stringify({ success: true, data: { items: [] } }), {
    status: 200,
  });
});

vi.stubGlobal("fetch", mockFetch as unknown as typeof fetch);

describe("교수업적평가시스템 UI contract", () => {
  beforeEach(() => {
    mockFetch.mockClear();
    window.history.pushState({}, "", "/login");
    vi.spyOn(window, "confirm").mockReturnValue(true);
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it("renders Korean admin shell and user management route without absolute API URLs", async () => {
    window.history.pushState({}, "", "/admin/users");
    render(<App />);
    expect(
      await screen.findByText("한국교원대학교 교수업적평가시스템"),
    ).toBeInTheDocument();
    expect(await screen.findByText("사용자 관리")).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith(
      expect.stringMatching(/^\/api\//),
      expect.anything(),
    );
  });

  it("exposes separate menu parent and reorder save CTAs on menu structure route", async () => {
    window.history.pushState({}, "", "/admin/menu-structure");
    render(<App />);

    expect(await screen.findByText("메뉴 구조 관리")).toBeInTheDocument();
    expect((await screen.findAllByText("MENU-USER")).length).toBeGreaterThan(0);
    expect(
      screen.getByRole("button", { name: "부모 변경 저장" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "순서 재정렬 저장" }),
    ).toBeInTheDocument();
  });

  it("keeps lifecycle identity readonly in edit mode and excludes it from update payload", async () => {
    window.history.pushState({}, "", "/admin/code-groups");
    render(<App />);

    let groupId: HTMLElement | undefined;
    await waitFor(() => {
      groupId = screen
        .getAllByLabelText(/그룹ID/)
        .find((element) => (element as HTMLInputElement).value === "COMMON_YN");
      expect(groupId).toBeDefined();
    });
    expect(groupId).toHaveAttribute("readonly");

    const groupName = screen
      .getAllByLabelText(/명칭/)
      .find((element) => (element as HTMLInputElement).value === "공통 YN");
    expect(groupName).toBeDefined();
    fireEvent.change(groupName as HTMLInputElement, {
      target: { value: "공통 여부" },
    });
    fireEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() =>
      expect(mockFetch).toHaveBeenCalledWith(
        "/api/code-groups/COMMON_YN",
        expect.objectContaining({
          method: "PATCH",
          body: expect.not.stringContaining("groupId"),
        }),
      ),
    );
  });

  it("renders batch management routes and loads batch result detail by selected execution", async () => {
    window.history.pushState({}, "", "/admin/batch-results");
    render(<App />);

    expect(await screen.findByText("배치 결과 조회")).toBeInTheDocument();
    const resultTable = await screen.findByRole("table");
    fireEvent.click(within(resultTable).getByText("EXEC-SEED-FAILED"));

    await waitFor(() =>
      expect(mockFetch).toHaveBeenCalledWith(
        "/api/batch-results/EXEC-SEED-FAILED",
        expect.anything(),
      ),
    );
    expect(await screen.findByDisplayValue("300")).toBeInTheDocument();
  });

  it("serializes batch definition JSON parameters and required save reason", async () => {
    window.history.pushState({}, "", "/admin/batch-definitions");
    render(<App />);

    expect(await screen.findByText("배치 정의 관리")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "신규 등록" }));
    const detailForm = screen
      .getByRole("heading", { name: "등록" })
      .closest("section");
    expect(detailForm).not.toBeNull();
    const detail = within(detailForm as HTMLElement);
    fireEvent.change(detail.getByLabelText(/^배치ID/), {
      target: { value: "BATCH-NEW" },
    });
    fireEvent.change(detail.getByLabelText(/배치 업무유형/), {
      target: { value: "EVALUATION_DATA" },
    });
    fireEvent.change(detail.getByLabelText(/실행주기/), {
      target: { value: "DAILY 05:00" },
    });
    fireEvent.change(detail.getByLabelText(/실행 파라미터 JSON/), {
      target: { value: '{"year":"2026"}' },
    });
    fireEvent.change(detail.getByLabelText(/최대실행시간/), {
      target: { value: "900" },
    });
    fireEvent.change(detail.getByLabelText(/담당자ID/), {
      target: { value: "U-ADMIN" },
    });
    fireEvent.change(detail.getByLabelText(/변경 사유/), {
      target: { value: "등록" },
    });
    fireEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() =>
      expect(mockFetch).toHaveBeenCalledWith(
        "/api/batch-definitions",
        expect.objectContaining({
          method: "POST",
          body: expect.stringContaining(
            '"executionParameters":{"year":"2026"}',
          ),
        }),
      ),
    );
  });
});
