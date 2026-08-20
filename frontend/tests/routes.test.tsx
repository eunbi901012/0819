import "@testing-library/jest-dom/vitest";
import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";

describe("route inventory", () => {
  it("contains all nine protected management routes and Korean labels", () => {
    const source = readFileSync("src/main.tsx", "utf8");
    for (const route of [
      "/admin/users",
      "/admin/organizations",
      "/admin/roles",
      "/admin/user-roles",
      "/admin/menu-permissions",
      "/admin/menu-structure",
      "/admin/menu-info",
      "/admin/code-groups",
      "/admin/code-details",
    ]) {
      expect(source).toContain(route);
    }
    expect(source).toContain("사용자 관리");
    expect(source).toContain("상세코드 관리");
  });
});
