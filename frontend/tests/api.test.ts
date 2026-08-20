import { describe, expect, it, vi } from "vitest";
import { api } from "../src/api";

describe("frontend API client", () => {
  it("uses relative /api paths without base URL", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ success: true, meta: {}, data: { ok: true } }),
    });
    vi.stubGlobal("fetch", fetchMock);
    await api("/api/health");
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/health",
      expect.objectContaining({ credentials: "include" }),
    );
  });

  it("rejects absolute API URLs", async () => {
    await expect(api("http://example.invalid/api/health")).rejects.toThrow(
      "/api",
    );
  });
});
