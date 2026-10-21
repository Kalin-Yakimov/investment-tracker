import { beforeEach, expect, test, vi } from "vitest";
import PortfolioApi from "./PortfolioApi";
import PriceTrackerApi from "../price-tracker/PriceTrackerApi";
import { act, fireEvent, render, screen } from "@testing-library/react";
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
  AuthService.getSessionVersion.mockReturnValue(1);
  PriceTrackerApi.getTrackedItems.mockResolvedValue([]);
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
  unmount();
  expect(globalThis.IntersectionObserver.mock.results[0].value.disconnect).toHaveBeenCalled();
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

vi.mock("./PortfolioApi", () => ({
  default: { getUserItems: vi.fn(), getPortfolioSummary: vi.fn() },
}));

vi.mock("../price-tracker/PriceTrackerApi", () => ({
  default: { getTrackedItems: vi.fn() },
}));
