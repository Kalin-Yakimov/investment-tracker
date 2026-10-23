import { beforeEach, expect, test, vi } from "vitest";
import PortfolioApi from "./PortfolioApi";
import PriceTrackerApi from "../price-tracker/PriceTrackerApi";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import PortfolioOverviewPage from "./PortfolioOverviewPage";
import AuthService from "../auth/AuthService";
import deferred from "../../testUtils/deferred";

vi.mock("../auth/AuthService", () => ({
  default: {
    getSessionVersion: vi.fn(),

  },
}));
vi.mock("react-chartjs-2", () => ({ Line: () => null }));
const page = (name) => ({ content: [{ id: 1, itemName: name, itemStatus: "BOUGHT", quantity: 1, price: 2 }], totalPages: 2 });
beforeEach(() => {
  vi.clearAllMocks();
  vi.spyOn(window, "scrollTo").mockImplementation(() => {});
  window.scrollY = 0;
  AuthService.getSessionVersion.mockReturnValue(1);
  PriceTrackerApi.getTrackedItems.mockResolvedValue({ content: [], number: 0, totalElements: 0, totalPages: 0 });
  PriceTrackerApi.getPriceHistory.mockResolvedValue([]);
  PortfolioApi.getPortfolioSummary.mockResolvedValue({ count: 1, totalInvestmentValue: 2 });
  globalThis.IntersectionObserver = vi.fn(function () {
    return { observe: vi.fn(), disconnect: vi.fn() };
  });
});

test("new filter wins even when the initial request finishes later", async () => {
  const old = deferred();
  PortfolioApi.getUserItems.mockReturnValueOnce(old.promise).mockResolvedValue(page("New result"));
  render(<MemoryRouter><PortfolioOverviewPage /></MemoryRouter>);
  const signal = PortfolioApi.getUserItems.mock.calls[0][3].signal;
  fireEvent.change(screen.getByRole("combobox"), { target: { value: "bought" } });
  await screen.findByText("New result");
  await act(async () => old.resolve(page("Old result")));
  expect(signal.aborted).toBe(true);
  expect(screen.queryByText("Old result")).not.toBeInTheDocument();
  expect(PriceTrackerApi.getTrackedItems).toHaveBeenCalledTimes(1);
  expect(PortfolioApi.getPortfolioSummary).toHaveBeenCalledTimes(1);
});

test("pagination does not reload unrelated pinned items or portfolio totals", async () => {
  PortfolioApi.getUserItems.mockResolvedValue(page("One Case"));
  const { unmount } = render(<MemoryRouter><PortfolioOverviewPage /></MemoryRouter>);
  await screen.findByText("One Case");
  fireEvent.click(screen.getByRole("button", { name: "Next" }));
  await act(async () => {});
  expect(PortfolioApi.getUserItems).toHaveBeenCalledTimes(2);
  expect(PortfolioApi.getUserItems.mock.calls[1].slice(0, 3)).toEqual([1, 10, "newest_date"]);
  expect(PriceTrackerApi.getTrackedItems).toHaveBeenCalledTimes(1);
  expect(PortfolioApi.getPortfolioSummary).toHaveBeenCalledTimes(1);
  expect(window.scrollTo).not.toHaveBeenCalled();
  unmount();
  expect(globalThis.IntersectionObserver.mock.results[0].value.disconnect).toHaveBeenCalled();
});

test("restores the return scroll once after both tables load, not on later filters or pagination", async () => {
  const ledger = deferred();
  const pinned = deferred();
  PortfolioApi.getUserItems.mockReturnValueOnce(ledger.promise).mockResolvedValue(page("Returned record"));
  PriceTrackerApi.getTrackedItems.mockReturnValueOnce(pinned.promise);
  render(<MemoryRouter><PortfolioOverviewPage initialPage={1} initialScrollTop={640} /></MemoryRouter>);
  expect(window.scrollTo).not.toHaveBeenCalled();
  await act(async () => ledger.resolve(page("Returned record")));
  expect(window.scrollTo).not.toHaveBeenCalled();
  await act(async () => pinned.resolve({ content: [], number: 0, totalElements: 0 }));
  expect(window.scrollTo).toHaveBeenCalledExactlyOnceWith({ top: 640, left: 0, behavior: "instant" });
  fireEvent.change(screen.getByRole("combobox"), { target: { value: "bought" } });
  await waitFor(() => expect(screen.getByText("Page 1 of 2")).toBeInTheDocument());
  fireEvent.click(screen.getByRole("button", { name: "Next" }));
  await waitFor(() => expect(screen.getByText("Page 2 of 2")).toBeInTheDocument());
  expect(window.scrollTo).toHaveBeenCalledTimes(1);
});

test("remembers the current viewport when opening a portfolio record", async () => {
  PortfolioApi.getUserItems.mockResolvedValue(page("Opened record"));
  const onItemOpen = vi.fn();
  render(<MemoryRouter><PortfolioOverviewPage onItemOpen={onItemOpen} /></MemoryRouter>);
  await screen.findByRole("button", { name: "Opened record" });
  window.scrollY = 380;
  fireEvent.click(screen.getByRole("button", { name: "Opened record" }));
  expect(onItemOpen).toHaveBeenCalledExactlyOnceWith(380);
});

test("returning from an item loads the remembered portfolio page without affecting pinned loading", async () => {
  PortfolioApi.getUserItems.mockResolvedValue(page("Returned record"));
  const onPageLoaded = vi.fn();
  render(<MemoryRouter><PortfolioOverviewPage initialPage={1} onPageLoaded={onPageLoaded} /></MemoryRouter>);
  await screen.findByText("Returned record");
  expect(PortfolioApi.getUserItems.mock.calls[0].slice(0, 3)).toEqual([1, 10, "newest_date"]);
  expect(screen.getByText("Page 2 of 2")).toBeInTheDocument();
  expect(onPageLoaded).toHaveBeenCalledWith(1);
  expect(PriceTrackerApi.getTrackedItems).toHaveBeenCalledTimes(1);
  fireEvent.change(screen.getByRole("combobox"), { target: { value: "bought" } });
  await waitFor(() => expect(PortfolioApi.getUserItems).toHaveBeenLastCalledWith(0, 10, "bought", { signal: expect.any(AbortSignal) }));
  expect(PriceTrackerApi.getTrackedItems).toHaveBeenCalledTimes(1);
});

test("a removed last portfolio page falls back to the nearest remaining page", async () => {
  PortfolioApi.getUserItems.mockResolvedValueOnce({ content: [], totalPages: 2 })
    .mockResolvedValueOnce(page("Last remaining page"));
  const onPageLoaded = vi.fn();
  render(<MemoryRouter><PortfolioOverviewPage initialPage={2} onPageLoaded={onPageLoaded} /></MemoryRouter>);
  await screen.findByText("Last remaining page");
  expect(PortfolioApi.getUserItems.mock.calls.map(call => call[0])).toEqual([2, 1]);
  expect(screen.getByText("Page 2 of 2")).toBeInTheDocument();
  expect(onPageLoaded).toHaveBeenCalledWith(1);
  expect(PriceTrackerApi.getTrackedItems).toHaveBeenCalledTimes(1);
});

test("deleting the final portfolio record returns an empty first page without repeated requests", async () => {
  PortfolioApi.getUserItems.mockResolvedValue({ content: [], totalPages: 0 });
  const onPageLoaded = vi.fn();
  render(<MemoryRouter><PortfolioOverviewPage initialPage={2} onPageLoaded={onPageLoaded} /></MemoryRouter>);
  await screen.findByText(/No portfolio items registered/);
  expect(PortfolioApi.getUserItems).toHaveBeenCalledTimes(1);
  expect(onPageLoaded).toHaveBeenCalledWith(0);
});

test("portfolio omits the optional CSGOTrader footer while retaining the required FX link", async () => {
  PortfolioApi.getUserItems.mockResolvedValue(page("Ledger record"));
  render(<MemoryRouter><PortfolioOverviewPage /></MemoryRouter>);
  await screen.findByText("Ledger record");
  expect(screen.queryByRole("link", { name: "CSGOTrader", exact: true })).not.toBeInTheDocument();
  expect(screen.getByRole("link", { name: "Rates by ExchangeRate-API", exact: true })).toBeInTheDocument();
});

test("failed loading is not an empty portfolio and a retry can recover", async () => {
  PortfolioApi.getUserItems.mockRejectedValueOnce(new Error("offline")).mockResolvedValue(page("Recovered record"));
  render(<MemoryRouter><PortfolioOverviewPage /></MemoryRouter>);
  expect(await screen.findByRole("alert")).toHaveTextContent("Portfolio history could not be loaded");
  expect(screen.queryByText(/No portfolio items registered/)).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Retry portfolio history" }));
  await screen.findByText("Recovered record");
  expect(PortfolioApi.getUserItems).toHaveBeenCalledTimes(2);
});

test("pinned scrolling requests only dashboard pages and preserves loaded rows while the next page waits", async () => {
  PortfolioApi.getUserItems.mockResolvedValue(page("Ledger record"));
  const callbacks = [];
  globalThis.IntersectionObserver = vi.fn(function (callback) {
    callbacks.push(callback); return { observe: vi.fn(), disconnect: vi.fn() };
  });
  const first = Array.from({ length: 10 }, (_, index) => ({ id: index + 1, itemName: `Pinned ${index + 1}`, price: 1, showInDashboard: true }));
  const next = deferred();
  PriceTrackerApi.getTrackedItems.mockResolvedValueOnce({ content: first, number: 0, totalElements: 12, totalPages: 2 })
    .mockReturnValueOnce(next.promise);
  render(<MemoryRouter><PortfolioOverviewPage /></MemoryRouter>);
  await screen.findByRole("button", { name: "Pinned 1", exact: true });
  await waitFor(() => expect(PriceTrackerApi.getPriceHistory).toHaveBeenCalledTimes(10));
  await act(async () => callbacks.at(-1)([{ isIntersecting: true }]));
  expect(PriceTrackerApi.getTrackedItems.mock.calls.at(-1)[0]).toMatchObject({ page: 1, size: 10, dashboard: true });
  expect(screen.getByRole("button", { name: "Pinned 1", exact: true })).toBeInTheDocument();
  await act(async () => next.resolve({ content: [{ id: 11, itemName: "Pinned 11", price: 1 }, { id: 12, itemName: "Pinned 12", price: 1 }], number: 1, totalElements: 12, totalPages: 2 }));
  await screen.findByRole("button", { name: "Pinned 12", exact: true });
  await waitFor(() => expect(PriceTrackerApi.getPriceHistory).toHaveBeenCalledTimes(12));
});

vi.mock("./PortfolioApi", () => ({
  default: { getUserItems: vi.fn(), getPortfolioSummary: vi.fn() },
}));

vi.mock("../price-tracker/PriceTrackerApi", () => ({
  default: { getTrackedItems: vi.fn(), getPriceHistory: vi.fn() },
}));
