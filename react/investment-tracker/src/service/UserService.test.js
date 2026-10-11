import { apiClient } from "./ApiClient";
import PortfolioService from "./PortfolioService";
import PriceService from "./PriceService";
import axios from "axios";
import UserService from "./UserService";
import deferred from "../testUtils/deferred";

// CRA's Jest resolver selects Axios' ESM entry. Use its official CJS build for
// these tests only; every request uses the in-memory adapter below, never HTTP.
jest.mock("axios", () => jest.requireActual("../../node_modules/axios/dist/node/axios.cjs"));

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
  UserService.accessToken = null;
  UserService.sessionVersion = 0;
  UserService.sessionChanges = 0;
  UserService.authListeners.clear();
  apiClient.defaults.adapter = jest.fn((config) => {
    const response = deferred();
    pending.push({ ...response, config });
    return response.promise;
  });
});

test("concurrent unauthorized reads share one refresh and retry with the refreshed token", async () => {
  UserService.saveToken(token(42, "old"));
  const first = PriceService.getTrackedItems();
  const second = PortfolioService.getPortfolioSummary();
  await flush();
  fail401(0); fail401(1);
  await flush();
  expect(pending).toHaveLength(3);
  expect(pending[2].config.url).toMatch(/auth\/refresh$/);
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
  UserService.saveToken(current);
  const controller = new AbortController();
  const result = PriceService.getTrackedItems({ signal: controller.signal }).catch((error) => error);
  await flush();
  controller.abort();
  fail401(0);
  expect(axios.isCancel(await result)).toBe(true);
  expect(pending).toHaveLength(1);
  expect(UserService.getToken()).toBe(current);
});

test("an old session's 401 cannot refresh or clear the newly signed-in account", async () => {
  UserService.saveToken(token(1));
  const result = PriceService.getTrackedItems().catch((error) => error);
  await flush();
  const next = token(2);
  UserService.saveToken(next);
  fail401(0);
  expect(axios.isCancel(await result)).toBe(true);
  expect(pending).toHaveLength(1);
  expect(UserService.getToken()).toBe(next);
});

test("a successful old-account response is also rejected after the account changes", async () => {
  UserService.saveToken(token(1));
  const result = PriceService.getTrackedItems().catch((error) => error);
  await flush();
  UserService.saveToken(token(2));
  succeed(0, [{ id: 10, itemName: "Private old record" }]);
  expect(axios.isCancel(await result)).toBe(true);
});

test("sign-out waits for an outstanding refresh but never adopts its late token", async () => {
  UserService.saveToken(token(42, "old"));
  const refresh = UserService.refreshAccessToken();
  await flush();
  const logout = UserService.logout();
  await flush();
  expect(pending).toHaveLength(1);
  succeed(0, { token: token(42, "late") });
  await flush();
  expect(await refresh).toBe(false);
  expect(pending).toHaveLength(2);
  expect(pending[1].config.url).toMatch(/auth\/logout$/);
  succeed(1, {});
  await logout;
  expect(UserService.getToken()).toBeNull();
  expect(UserService.sessionChanges).toBe(0);
});

test("only the latest login attempt can publish its token", async () => {
  const first = UserService.login("first@example.test", "test-password").catch((error) => error);
  await flush();
  const second = UserService.login("second@example.test", "test-password");
  await flush();
  const latest = token(2);
  succeed(1, { token: latest });
  await second;
  succeed(0, { token: token(1) });
  expect(axios.isCancel(await first)).toBe(true);
  expect(UserService.getToken()).toBe(latest);
  expect(UserService.sessionChanges).toBe(0);
});

test("a password change from an old session cannot sign out a new account", async () => {
  UserService.saveToken(token(1));
  const result = UserService.changePassword({}).catch((error) => error);
  await flush();
  const next = token(2);
  UserService.saveToken(next);
  succeed(0, { message: "Changed" });
  expect(axios.isCancel(await result)).toBe(true);
  expect(UserService.getToken()).toBe(next);
});

test("credentials are not attached to an unrelated origin with a similar URL prefix", async () => {
  UserService.saveToken(token(42));
  axios.defaults.adapter = apiClient.defaults.adapter;
  const result = axios.get(UserService.BASE_URL + ".example.test/public");
  await flush();
  expect(pending[0].config.headers.Authorization).toBeUndefined();
  succeed(0, {});
  await result;
});

test("history preserves cancellation errors without accessing a missing response", async () => {
  UserService.saveToken(token(42));
  const controller = new AbortController();
  controller.abort();
  const result = await PortfolioService.getItemByName("Case", 0, 10, { signal: controller.signal }).catch((error) => error);
  expect(axios.isCancel(result)).toBe(true);
  expect(pending).toHaveLength(0);
});

test("canceling while a shared refresh is pending prevents that read from being retried", async () => {
  UserService.saveToken(token(42, "old"));
  const controller = new AbortController();
  const result = PriceService.getTrackedItems({ signal: controller.signal }).catch((error) => error);
  await flush();
  fail401(0);
  await flush();
  expect(pending).toHaveLength(2);
  controller.abort();
  const fresh = token(42, "fresh");
  succeed(1, { token: fresh });
  expect(axios.isCancel(await result)).toBe(true);
  expect(pending).toHaveLength(2);
  expect(UserService.getToken()).toBe(fresh);
});
