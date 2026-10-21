import { beforeEach, expect, test } from "vitest";
import { decodeJwtPayload } from "./jwtPayload";
import AuthService from "./AuthService";

function token(payload, padded = false) {
  const bytes = new TextEncoder().encode(JSON.stringify(payload));
  let encoded = btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_");
  if (!padded) encoded = encoded.replace(/=+$/, "");
  return `header.${encoded}.signature`;
}

beforeEach(() => {
  AuthService.accessToken = null;
  AuthService.sessionVersion = 0;
  AuthService.authListeners.clear();
});

test.each([false, true])("decodes UTF-8 names, emoji and Base64URL symbols (padded=%s)", (padded) => {
  const payload = { sub: "42", fullName: "Калин 😀 \uFFFF", email: "user@example.invalid", authorities: ["ADMIN"] };
  const jwt = token(payload, padded);
  expect(jwt.split(".")[1]).toMatch(/[-_]/);
  expect(decodeJwtPayload(jwt)).toEqual(payload);
  AuthService.saveToken(jwt);
  expect(AuthService.getCurrentUser()).toEqual({ id: 42, name: payload.fullName, email: payload.email, authorities: ["ADMIN"] });
  expect(AuthService.isAuthenticated()).toBe(true);
  expect(AuthService.hasAuthority("ADMIN")).toBe(true);
});

test.each([null, "", "one-part", "header..signature", "header.a.signature", "header.%.signature",
  "header._w.signature", "header.e30.signature.extra", "header.eyJzdWIi.signature", token(null), token([]), token("text")])(
  "malformed token %#: returns null without throwing or exposing token contents", (jwt) => {
    expect(decodeJwtPayload(jwt)).toBeNull();
    AuthService.saveToken(jwt);
    expect(AuthService.getCurrentUser()).toBeNull();
    expect(AuthService.isAuthenticated()).toBe(false);
    expect(AuthService.hasAuthority("ADMIN")).toBe(false);
  },
);

test.each([null, false, true, {}, [], "not-an-id", 0, -1, 1.5, "9007199254740993"])(
  "invalid account identifier %#: cannot identify an authenticated UI session", (sub) => {
    AuthService.saveToken(token({ sub }));
    expect(AuthService.getCurrentUser()).toBeNull();
  },
);

test("same-account token renewal preserves its session identity and updates the Unicode display name", () => {
  AuthService.saveToken(token({ sub: "42", fullName: "Първо име" }));
  const version = AuthService.getSessionVersion();
  AuthService.saveToken(token({ sub: "42", fullName: "Ново име" }));
  expect(AuthService.getCurrentUser().name).toBe("Ново име");
  expect(AuthService.getSessionVersion()).toBe(version);
  AuthService.saveToken(token({ sub: "43", fullName: "Друг профил" }));
  expect(AuthService.getSessionVersion()).toBeGreaterThan(version);
});

test("unexpected display-claim types cannot become React children or non-string authorities", () => {
  AuthService.saveToken(token({ sub: "42", fullName: {}, email: [], authorities: ["USER", {}, 123] }));
  expect(AuthService.getCurrentUser()).toEqual({ id: 42, name: "User Account", email: "", authorities: ["USER"] });
});
