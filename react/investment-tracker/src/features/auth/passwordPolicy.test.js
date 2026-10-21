import { expect, test } from "vitest";
import { passwordError, PASSWORD_HINT } from "./passwordPolicy";

test.each([
  [null, "required"], ["", "required"], [" ".repeat(10), "required"],
  ["a".repeat(9), "at least 10"], ["a".repeat(10), ""],
  ["a".repeat(72), ""], ["a".repeat(73), "72 UTF-8 bytes"],
  ["я".repeat(10), ""], ["я".repeat(36), ""], ["я".repeat(37), "72 UTF-8 bytes"],
  ["😀".repeat(9), "at least 10"], ["😀".repeat(10), ""],
  ["😀".repeat(18), ""], ["😀".repeat(19), "72 UTF-8 bytes"],
  ["  a passphrase  ", ""], ["\uD800" + "a".repeat(10), "invalid Unicode"],
])("password boundary %#: validates Unicode characters and UTF-8 bytes", (password, error) => {
  if (error) expect(passwordError(password)).toContain(error);
  else expect(passwordError(password)).toBe("");
});

test("short existing credentials remain usable without applying the new-password minimum", () => {
  expect(passwordError("legacy", { newPassword: false })).toBe("");
  expect(passwordError("legacy")).toContain("at least 10");
  expect(PASSWORD_HINT).toContain("at least 10");
  expect(passwordError("я".repeat(37), { newPassword: false })).toContain("72 UTF-8 bytes");
});
