import axios from "axios";
import { apiClient } from "./ApiClient";
import UserService from "./UserService";
import PortfolioService from "./PortfolioService";
import PriceService from "./PriceService";
import InventoryService from "./InventoryService";
import CatalogueService from "./CatalogueService";
import ContactService from "./ContactService";
import AdminService from "./AdminService";

jest.mock("axios", () => jest.requireActual("../../node_modules/axios/dist/node/axios.cjs"));
let requests;
beforeEach(() => {
  requests = [];
  UserService.accessToken = null;
  UserService.sessionVersion = 0;
  UserService.sessionChanges = 0;
  UserService.authListeners.clear();
  UserService.saveToken("header." + btoa(JSON.stringify({ sub: "42" })) + ".signature");
  apiClient.defaults.adapter = async (config) => {
    requests.push(config);
    return { data: { ok: true }, status: 200, statusText: "OK", headers: {}, config };
  };
});

test.each([
  ["portfolio", () => PortfolioService.getUserItems(2, 10, "bought"), "/get-user-items", "get"],
  ["item", () => PortfolioService.createItem({ itemName: "Case" }), "/item", "post"],
  ["prices", () => PriceService.getPriceHistory(7), "/api/price-history/7", "get"],
  ["inventory", () => InventoryService.calculateSteamInventory("https://steamcommunity.com/id/example"), "/api/v1/inventory/calculate", "post"],
  ["catalogue", () => CatalogueService.searchCsItems("Case"), "/api/cs-items/search", "get"],
  ["contact", () => ContactService.createContactTicket("user@example.test", "Message"), "/api/contact", "post"],
])("%s service uses the isolated client and current session", async (_, call, url, method) => {
  await expect(call()).resolves.toEqual({ ok: true });
  expect(requests).toHaveLength(1);
  expect(requests[0].url).toBe(url);
  expect(requests[0].method).toBe(method);
  expect(requests[0].headers.Authorization).toBe("Bearer " + UserService.getToken());
  expect(requests[0].withCredentials).toBe(true);
  expect(requests[0]._sessionVersion).toBe(UserService.getSessionVersion());
});

test("support reason, pagination/filter parameters and read cancellation remain intact", async () => {
  const controller = new AbortController();
  await PortfolioService.getUserItems(2, 10, "bought", { signal: controller.signal });
  expect(requests[0].params).toEqual({ page: 2, size: 10, filter: "bought" });
  expect(requests[0].signal).toBe(controller.signal);
  await AdminService.getPortfolio(7, "Reported a problem", 1, 20, { signal: controller.signal });
  expect(requests[1].headers["X-Support-Reason"]).toBe("Reported a problem");
  expect(requests[1].params).toEqual({ page: 1, size: 20 });
});

test("global Axios is not configured with application cookies or interceptors", () => {
  expect(axios.defaults.withCredentials).not.toBe(true);
  expect(axios.interceptors.request.handlers.filter(Boolean)).toHaveLength(0);
  expect(axios.interceptors.response.handlers.filter(Boolean)).toHaveLength(0);
});

test("the application client rejects a similar foreign origin before sending credentials", async () => {
  await expect(apiClient.get(UserService.BASE_URL + ".example.test/private")).rejects.toMatchObject({ code: "ERR_BAD_REQUEST" });
  expect(requests).toHaveLength(0);
});

test("domain services retain server error payloads without swallowing cancellation", async () => {
  apiClient.defaults.adapter = (config) => Promise.reject(new axios.AxiosError("Invalid", "ERR_BAD_REQUEST", config, {}, {
    status: 400, data: { error: "Invalid request" }, config,
  }));
  await expect(ContactService.createContactTicket("", "")).rejects.toEqual({ error: "Invalid request" });
  const controller = new AbortController();
  controller.abort();
  const error = await PriceService.getTrackedItems({ signal: controller.signal }).catch(error => error);
  expect(axios.isCancel(error)).toBe(true);
});
