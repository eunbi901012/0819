import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";

const source = () => readFileSync("src/main.tsx", "utf8");

describe("UI contract drift safeguards", () => {
  it("keeps selected row identity after owning list refresh", () => {
    expect(source()).toContain("const previousKey = rowKey(state.selected)");
    expect(source()).toContain("const nextSelected =");
  });

  it("does not send immutable roleCode in role update payload", () => {
    const rolePayload =
      source().match(/if \(kind === "roles"\)[\s\S]*?};/)?.[0] ?? "";
    expect(rolePayload).not.toContain("roleCode: form.roleCode");
  });

  it("prevents editing code detail lifecycle identity fields in edit mode", () => {
    const formFieldsSource = source().split("const formFields:")[1] ?? "";
    const codeDetailFields =
      formFieldsSource.match(/codeDetails: \[[\s\S]*?\n  \],\n};/)?.[0] ?? "";
    expect(codeDetailFields).toContain(
      'key: "codeValue", label: "코드값", required: true, createOnly: true',
    );
  });
});
