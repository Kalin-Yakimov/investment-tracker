import { beforeEach, expect, test, vi } from "vitest";
import { apiClient } from "../../shared/api/ApiClient";
import PortfolioApi from "../portfolio/PortfolioApi";
import PriceTrackerApi from "../price-tracker/PriceTrackerApi";
import axios from "axios";
import AuthService from "./AuthService";
import deferred from "../../testUtils/deferred";


let pending;
const token = (id, marker = "token") => "header." + btoa(JSON.stringify({ sub: String(id), email: "user@example.test", marker })) + ".signature";
const flush = async () => { for (let i = 0; i < 12; i += 1) await Promise.resolve(); };
const succeed = (index, data) => pending[index].resolve({
  data, status: 200, statusText: "OK", headers: {}, config: pending[index].config,
});
const fail401 = (index) => pending[index].reject(new axios.AxiosError(
  "Unauthorized", "ERR_BAD_REQUEST", pending[index].config, {},
  { status: 401, data: {}, config: pending[index].config },
));
beforeEach(() => {
  pending = [];
  AuthService.accessToken = null;
  AuthService.sessionVersion = 0;
  AuthService.sessionChanges = 0;
  AuthService.authListeners.clear();
  apiClient.defaults.adapter = vi.fn((config) => {
    const response = deferred();
    pending.push({ ...response, config });
    return response.promise;
  });
});

test("concurrent unauthorized reads share one refresh and retry with the refreshed token", async () => {
  AuthService.saveToken(token(42, "old"));
  const first = PriceTrackerApi.getTrackedItems();
  const second = PortfolioApi.getPortfolioSummary();
  await flush();
  fail401(0); fail401(1);
  await flush();
  expect(pending).toHaveLength(3);
  expect(pending[2].config.url).toMatch(/auth\/refresh$/);
  expect(pending[2].config.headers["X-Requested-With"]).toBe("XMLHttpRequest");
  const refreshed = token(42, "new");
  succeed(2, { token: refreshed });
  await flush();
  expect(pending).toHaveLength(5);
  expect(pending[3].config.headers.Authorization).toBe("Bearer " + refreshed);
  expect(pending[4].config.headers.Authorization).toBe("Bearer " + refreshed);
  succeed(3, []); succeed(4, { count: 2 });
  await expect(first).resolves.toEqual([]);
  await expect(second).resolves.toEqual({ count: 2 });
});

test("an aborted read's unauthorized response cannot trigger refresh or logout", async () => {
  const current = token(42);
  AuthService.saveToken(current);
  const controller = new AbortController();
  const result = PriceTrackerApi.getTrackedItems({ signal: controller.signal }).catch((error) => error);
  await flush();
  controller.abort();
  fail401(0);
  expect(axios.isCancel(await result)).toBe(true);
  expect(pending).toHaveLength(1);
  expect(AuthService.getToken()).toBe(current);
});

test("an old session's 401 cannot refresh or clear the newly signed-in account", async () => {
  AuthService.saveToken(token(1));
  const result = PriceTrackerApi.getTrackedItems().catch((error) => error);
  await flush();
  const next = token(2);
  AuthService.saveToken(next);
  fail401(0);
  expect(axios.isCancel(await result)).toBe(true);
  expect(pending).toHaveLength(1);
  expect(AuthService.getToken()).toBe(next);
});

test("a successful old-account response is also rejected after the account changes", async () => {
  AuthService.saveToken(token(1));
  const result = PriceTrackerApi.getTrackedItems().catch((error) => error);
  await flush();
  AuthService.saveToken(token(2));
  succeed(0, [{ id: 10, itemName: "Private old record" }]);
  expect(axios.isCancel(await result)).toBe(true);
});

test("sign-out waits for an outstanding refresh but never adopts its late token", async () => {
  AuthService.saveToken(token(42, "old"));
  const refresh = AuthService.refreshAccessToken();
  await flush();
  const logout = AuthService.logout();
  await flush();
  expect(pending).toHaveLength(1);
  succeed(0, { token: token(42, "late") });
  await flush();
  expect(await refresh).toBe(false);
  expect(pending).toHaveLength(2);
  expect(pending[1].config.url).toMatch(/auth\/logout$/);
  expect(pending[1].config.headers["X-Requested-With"]).toBe("XMLHttpRequest");
  succeed(1, {});
  await logout;
  expect(AuthService.getToken()).toBeNull();
  expect(AuthService.sessionChanges).toBe(0);
});

test("only the latest login attempt can publish its token", async () => {
  const first = AuthService.login("first@example.test", "test-password").catch((error) => error);
  await flush();
  const second = AuthService.login("second@example.test", "test-password");
  await flush();
  const latest = token(2);
  succeed(1, { token: latest });
  await second;
  succeed(0, { token: token(1) });
  expect(axios.isCancel(await first)).toBe(true);
  expect(AuthService.getToken()).toBe(latest);
  expect(AuthService.sessionChanges).toBe(0);
});

test("sign-out without an in-memory access token still sends the cookie-authenticated request", async () => {
  const logout = AuthService.logout();
  await flush();
  expect(pending).toHaveLength(1);
  expect(pending[0].config.url).toMatch(/auth\/logout$/);
  expect(pending[0].config.withCredentials).toBe(true);
  expect(pending[0].config.headers["X-Requested-With"]).toBe("XMLHttpRequest");
  expect(pending[0].config.headers.Authorization).toBeUndefined();
  succeed(0, {});
  await logout;
  expect(AuthService.getToken()).toBeNull();
  expect(AuthService.sessionChanges).toBe(0);
});

test("a password change from an old session cannot sign out a new account", async () => {
  AuthService.saveToken(token(1));
  const result = AuthService.changePassword({}).catch((error) => error);
  await flush();
  const next = token(2);
  AuthService.saveToken(next);
  succeed(0, { message: "Changed" });
  expect(axios.isCancel(await result)).toBe(true);
  expect(AuthService.getToken()).toBe(next);
});

test("credentials are not attached to an unrelated origin with a similar URL prefix", async () => {
  AuthService.saveToken(token(42));
  axios.defaults.adapter = apiClient.defaults.adapter;
  const result = axios.get(AuthService.BASE_URL + ".example.test/public");
  await flush();
  expect(pending[0].config.headers.Authorization).toBeUndefined();
  succeed(0, {});
  await result;
});

test("history preserves cancellation errors without accessing a missing response", async () => {
  AuthService.saveToken(token(42));
  const controller = new AbortController();
  controller.abort();
  const result = await PortfolioApi.getItemByName("Case", 0, 10, { signal: controller.signal }).catch((error) => error);
  expect(axios.isCancel(result)).toBe(true);
  expect(pending).toHaveLength(0);
});

test("canceling while a shared refresh is pending prevents that read from being retried", async () => {
  AuthService.saveToken(token(42, "old"));
  const controller = new AbortController();
  const result = PriceTrackerApi.getTrackedItems({ signal: controller.signal }).catch((error) => error);
  await flush();
  fail401(0);
  await flush();
  expect(pending).toHaveLength(2);
  controller.abort();
  const fresh = token(42, "fresh");
  succeed(1, { token: fresh });
  expect(axios.isCancel(await result)).toBe(true);
  expect(pending).toHaveLength(2);
  expect(AuthService.getToken()).toBe(fresh);
});

test.each(["password", "username"])("an expected %s validation/conflict error keeps the existing session", async (action) => {
  const current = token(42);
  AuthService.saveToken(current);
  const result = (action === "password" ? AuthService.changePassword({}) : AuthService.changeUsername("Taken"))
    .catch((error) => error);
  await flush();
  const status = action === "password" ? 400 : 409;
  const data = { error: action === "password" ? "Current password is incorrect" : "Username is already taken by another account" };
  pending[0].reject(new axios.AxiosError("Expected input error", "ERR_BAD_REQUEST", pending[0].config, {},
    { status, data, config: pending[0].config }));
  expect(await result).toEqual(data);
  expect(AuthService.getToken()).toBe(current);
  expect(pending).toHaveLength(1);
});
