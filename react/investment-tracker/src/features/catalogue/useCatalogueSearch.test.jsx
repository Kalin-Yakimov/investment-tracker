import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import useCatalogueSearch from "./useCatalogueSearch";
import CatalogueApi from "./CatalogueApi";
import { ITEM_SEARCH_DEBOUNCE_MS } from "../../config/uiConfig";
import deferred from "../../testUtils/deferred";

vi.mock("../auth/AuthService", () => ({
  default: { getSessionVersion: () => 1 },
}));
vi.mock("./CatalogueApi", () => ({ default: { searchCsItems: vi.fn() } }));

beforeEach(() => {
  vi.useFakeTimers();
  CatalogueApi.searchCsItems.mockReset();
});
afterEach(() => vi.useRealTimers());

const advanceSearch = () => act(async () => {
  await vi.advanceTimersByTimeAsync(ITEM_SEARCH_DEBOUNCE_MS);
});

test("retains successful suggestions during debounce and requests, then accepts only the latest reply", async () => {
  const original = [{ id: "original", name: "AK-47" }];
  const intermediate = deferred();
  const latest = deferred();
  CatalogueApi.searchCsItems.mockResolvedValueOnce(original)
    .mockReturnValueOnce(intermediate.promise).mockReturnValueOnce(latest.promise);
  const { result, rerender } = renderHook(({ query }) => useCatalogueSearch(query, true, 2),
    { initialProps: { query: "AK" } });
  await advanceSearch();
  expect(result.current.results).toEqual(original);
  rerender({ query: "AK-" });
  expect(result.current.results).toEqual(original);
  expect(CatalogueApi.searchCsItems).toHaveBeenCalledTimes(1);
  await advanceSearch();
  const oldSignal = CatalogueApi.searchCsItems.mock.calls[1][1].signal;
  expect(result.current.results).toEqual(original);
  rerender({ query: "AK-47" });
  expect(oldSignal.aborted).toBe(true);
  expect(result.current.results).toEqual(original);
  await advanceSearch();
  await act(async () => latest.resolve([{ id: "latest", name: "AK-47 Redline" }]));
  await act(async () => intermediate.resolve([{ id: "stale", name: "Old suggestion" }]));
  expect(result.current.results).toEqual([{ id: "latest", name: "AK-47 Redline" }]);
});

test.each([
  { query: "", enabled: true },
  { query: "A", enabled: true },
  { query: "AK-47", enabled: false },
])("clears visible suggestions for $query / enabled=$enabled and ignores a pending reply", async (next) => {
  const pending = deferred();
  CatalogueApi.searchCsItems.mockResolvedValueOnce([{ id: "initial" }]).mockReturnValueOnce(pending.promise);
  const { result, rerender } = renderHook(({ query, enabled }) => useCatalogueSearch(query, enabled, 2),
    { initialProps: { query: "AK", enabled: true } });
  await advanceSearch();
  rerender({ query: "AK-", enabled: true });
  await advanceSearch();
  const signal = CatalogueApi.searchCsItems.mock.calls[1][1].signal;
  rerender(next);
  expect(result.current.results).toEqual([]);
  expect(signal.aborted).toBe(true);
  await act(async () => pending.resolve([{ id: "stale" }]));
  await advanceSearch();
  expect(result.current.results).toEqual([]);
  expect(CatalogueApi.searchCsItems).toHaveBeenCalledTimes(2);
});

test("an empty latest result removes previous suggestions", async () => {
  CatalogueApi.searchCsItems.mockResolvedValueOnce([{ id: "old" }]).mockResolvedValueOnce([]);
  const { result, rerender } = renderHook(({ query }) => useCatalogueSearch(query),
    { initialProps: { query: "Case" } });
  await advanceSearch();
  rerender({ query: "No matching case" });
  expect(result.current.results).toEqual([{ id: "old" }]);
  await advanceSearch();
  expect(result.current.results).toEqual([]);
});

test("a failed latest request clears suggestions instead of leaving them stale indefinitely", async () => {
  const log = vi.spyOn(console, "error").mockImplementation(() => {});
  CatalogueApi.searchCsItems.mockResolvedValueOnce([{ id: "old" }]).mockRejectedValueOnce(new Error("Unavailable"));
  const { result, rerender } = renderHook(({ query }) => useCatalogueSearch(query),
    { initialProps: { query: "Case" } });
  await advanceSearch();
  rerender({ query: "Different case" });
  expect(result.current.results).toEqual([{ id: "old" }]);
  await advanceSearch();
  expect(result.current.results).toEqual([]);
  expect(result.current.error).toBe("Item search could not be loaded.");
  expect(log).not.toHaveBeenCalled();
});
