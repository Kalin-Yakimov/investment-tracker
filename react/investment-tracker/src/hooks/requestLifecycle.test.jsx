import React from "react";
import { act, renderHook } from "@testing-library/react";
import UserService from "../service/UserService";
import useCatalogueSearch from "./useCatalogueSearch";
import usePriceHistories from "./usePriceHistories";
import useRequestScope from "./useRequestScope";
import deferred from "../testUtils/deferred";
import { ITEM_SEARCH_DEBOUNCE_MS } from "../config/uiConfig";

jest.mock("../service/UserService", () => ({
  __esModule: true,
  default: { getSessionVersion: jest.fn(), subscribe: jest.fn(), searchCsItems: jest.fn(), getPriceHistory: jest.fn() },
}));

beforeEach(() => {
  jest.clearAllMocks();
  UserService.getSessionVersion.mockReturnValue(1);
  UserService.subscribe.mockReturnValue(() => {});
});
afterEach(() => jest.useRealTimers());

test("search debounces, aborts the old query and ignores a late old response", async () => {
  jest.useFakeTimers();
  const first = deferred(), second = deferred();
  UserService.searchCsItems.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
  const { result, rerender } = renderHook(({ query }) => useCatalogueSearch(query), { initialProps: { query: "AK" } });
  act(() => jest.advanceTimersByTime(ITEM_SEARCH_DEBOUNCE_MS - 1));
  expect(UserService.searchCsItems).not.toHaveBeenCalled();
  act(() => jest.advanceTimersByTime(1));
  const oldSignal = UserService.searchCsItems.mock.calls[0][1].signal;
  rerender({ query: "Case" });
  expect(oldSignal.aborted).toBe(true);
  act(() => jest.advanceTimersByTime(ITEM_SEARCH_DEBOUNCE_MS));
  await act(async () => second.resolve([{ id: "new", name: "Case" }]));
  await act(async () => first.resolve([{ id: "old", name: "AK" }]));
  expect(result.current).toEqual([{ id: "new", name: "Case" }]);
});

test("clearing or selecting a query cancels pending work and prevents dropdown resurrection", async () => {
  jest.useFakeTimers();
  const response = deferred();
  UserService.searchCsItems.mockReturnValue(response.promise);
  const { result, rerender } = renderHook(({ enabled }) => useCatalogueSearch("AK", enabled), { initialProps: { enabled: true } });
  act(() => jest.advanceTimersByTime(ITEM_SEARCH_DEBOUNCE_MS));
  const signal = UserService.searchCsItems.mock.calls[0][1].signal;
  rerender({ enabled: false });
  await act(async () => response.resolve([{ id: 1 }]));
  expect(signal.aborted).toBe(true);
  expect(result.current).toEqual([]);
});

test("unmount clears an unsent debounce timer", () => {
  jest.useFakeTimers();
  const { unmount } = renderHook(() => useCatalogueSearch("AK"));
  unmount();
  act(() => jest.advanceTimersByTime(ITEM_SEARCH_DEBOUNCE_MS));
  expect(UserService.searchCsItems).not.toHaveBeenCalled();
});

test("histories have at most four active requests and are not duplicated by sibling completions", async () => {
  const responses = Array.from({ length: 6 }, deferred);
  UserService.getPriceHistory.mockImplementation((id) => responses[Number(id) - 1].promise);
  const items = responses.map((_, index) => ({ id: index + 1 }));
  const { result, rerender } = renderHook(({ list }) => usePriceHistories(list), { initialProps: { list: items } });
  await act(async () => {});
  expect(UserService.getPriceHistory).toHaveBeenCalledTimes(4);
  await act(async () => responses[0].resolve([{ price: 1 }]));
  expect(UserService.getPriceHistory).toHaveBeenCalledTimes(5);
  rerender({ list: [...items] });
  expect(UserService.getPriceHistory).toHaveBeenCalledTimes(5);
  await act(async () => responses[1].resolve([]));
  expect(UserService.getPriceHistory).toHaveBeenCalledTimes(6);
  await act(async () => responses.slice(2).forEach((response) => response.resolve([])));
  expect(Object.keys(result.current)).toHaveLength(6);
  expect(UserService.getPriceHistory.mock.calls.map(([id]) => id)).toEqual(["1", "2", "3", "4", "5", "6"]);
});

test("removed item history is aborted and cannot reappear in the result map", async () => {
  const response = deferred();
  UserService.getPriceHistory.mockReturnValue(response.promise);
  const { result, rerender } = renderHook(({ items }) => usePriceHistories(items), { initialProps: { items: [{ id: 1 }] } });
  await act(async () => {});
  const signal = UserService.getPriceHistory.mock.calls[0][1].signal;
  rerender({ items: [] });
  expect(signal.aborted).toBe(true);
  await act(async () => response.resolve([{ price: 99 }]));
  expect(result.current).toEqual({});
});

test("failed histories are not repeatedly retried; explicit revision permits a new load", async () => {
  const log = jest.spyOn(console, "error").mockImplementation(() => {});
  UserService.getPriceHistory.mockRejectedValueOnce(new Error("unavailable")).mockResolvedValue([]);
  try {
    const { rerender } = renderHook(({ revision }) => usePriceHistories([{ id: 1 }], revision), { initialProps: { revision: 0 } });
    await act(async () => {});
    rerender({ revision: 0 });
    expect(UserService.getPriceHistory).toHaveBeenCalledTimes(1);
    rerender({ revision: 1 });
    await act(async () => {});
    expect(UserService.getPriceHistory).toHaveBeenCalledTimes(2);
  } finally { log.mockRestore(); }
});

test("StrictMode cleanup does not strand the history queue", async () => {
  UserService.getPriceHistory.mockResolvedValue([{ price: 12 }]);
  const wrapper = ({ children }) => <React.StrictMode>{children}</React.StrictMode>;
  const { result } = renderHook(() => usePriceHistories([{ id: 1 }]), { wrapper });
  await act(async () => {});
  expect(result.current["1"]).toEqual([{ price: 12 }]);
  expect(UserService.getPriceHistory).toHaveBeenCalledTimes(1);
});

test("histories start when the item list arrives after the initial empty render", async () => {
  UserService.getPriceHistory.mockResolvedValue([]);
  const { result, rerender } = renderHook(({ items }) => usePriceHistories(items), { initialProps: { items: [] } });
  rerender({ items: [{ id: 1 }, { id: 2 }] });
  await act(async () => {});
  expect(UserService.getPriceHistory).toHaveBeenCalledTimes(2);
  expect(result.current).toEqual({ "1": [], "2": [] });
});

test("a session notification invalidates old private requests and cleans up its subscription", () => {
  const unsubscribe = jest.fn();
  UserService.subscribe.mockReturnValue(unsubscribe);
  const { result, unmount } = renderHook(() => useRequestScope());
  let request;
  act(() => { request = result.current.begin("private"); });
  UserService.getSessionVersion.mockReturnValue(2);
  act(() => UserService.subscribe.mock.calls[0][0]());
  expect(request.signal.aborted).toBe(true);
  expect(request.isCurrent()).toBe(false);
  unmount();
  expect(unsubscribe).toHaveBeenCalledTimes(1);
});
