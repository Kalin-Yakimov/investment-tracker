import { beforeEach, expect, test, vi } from "vitest";
import PriceTrackerApi from "./PriceTrackerApi";
import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import PriceTrackerPage from "./PriceTrackerPage";
import AuthService from "../auth/AuthService";
import deferred from "../../testUtils/deferred";

vi.mock("../auth/AuthService", () => ({
  default: {
    getSessionVersion: vi.fn(),

  },
}));
vi.mock("react-chartjs-2", () => ({ Line: () => null }));
beforeEach(() => {
  vi.clearAllMocks();
  AuthService.getSessionVersion.mockReturnValue(1);
  PriceTrackerApi.getTrackedItems.mockResolvedValue([
    { id: 1, itemName: "Case One", marketHashName: "Case One", currentPrice: 2, showInDashboard: false },
    { id: 2, itemName: "Case Two", marketHashName: "Case Two", currentPrice: 3, showInDashboard: false },
  ]);
  PriceTrackerApi.getPriceHistory.mockResolvedValue([]);
});

test("bookmark changes do not refetch unchanged histories or allow overlapping writes", async () => {
  const pending = deferred();
  PriceTrackerApi.toggleBookmark.mockReturnValue(pending.promise);
  render(<PriceTrackerPage />);
  await screen.findByText("Case One");
  await act(async () => {});
  await waitFor(() => expect(PriceTrackerApi.getPriceHistory).toHaveBeenCalledTimes(2));
  const buttons = screen.getAllByTitle("Pin to Main Dashboard");
  fireEvent.click(buttons[0]);
  fireEvent.click(buttons[1]);
  expect(PriceTrackerApi.toggleBookmark).toHaveBeenCalledTimes(1);
  expect(buttons[1]).toBeDisabled();
  await act(async () => pending.resolve({ bookmarked: true }));
  expect(PriceTrackerApi.getPriceHistory).toHaveBeenCalledTimes(2);
  expect(screen.getByTitle("Unpin from Main Dashboard")).toBeInTheDocument();
});

test("the historical log shows newest publication first without reversing chart data", async () => {
  const history = [
    { id: 1, dateExtracted: "2026-10-09T10:25:00Z", price: 2 },
    { id: 2, dateExtracted: "2026-10-10T13:25:01Z", price: 2 },
  ];
  PriceTrackerApi.getPriceHistory.mockResolvedValue(history);
  render(<PriceTrackerPage />);
  await screen.findByText("Case One");
  await waitFor(() => expect(PriceTrackerApi.getPriceHistory).toHaveBeenCalledTimes(2));
  await act(async () => {});
  fireEvent.click(screen.getByText("Case One"));
  const table = screen.getByText("Observation Date (UTC)").closest("table");
  const dates = within(table).getAllByRole("row").slice(1)
    .map((row) => within(row).getAllByRole("cell")[0].textContent);
  expect(dates).toEqual(["10/10/2026 13:25", "09/10/2026 10:25"]);
  expect(history.map((point) => point.id)).toEqual([1, 2]);
});

test("failed history has a retry control and recovers to an actual empty chart", async () => {
  PriceTrackerApi.getTrackedItems.mockResolvedValue([{ id: 1, itemName: "Case One" }]);
  PriceTrackerApi.getPriceHistory.mockRejectedValueOnce(new Error("offline")).mockResolvedValue([]);
  render(<PriceTrackerPage />);
  fireEvent.click(await screen.findByRole("button", { name: "Retry chart" }));
  await screen.findByText("No data");
  expect(PriceTrackerApi.getPriceHistory).toHaveBeenCalledTimes(2);
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
});
test("tracked-list failure remains visible and offers an explicit recovery", async () => {
  PriceTrackerApi.getTrackedItems.mockRejectedValueOnce(new Error("offline")).mockResolvedValue([]);
  render(<PriceTrackerPage />);
  expect(await screen.findByRole("alert")).toHaveTextContent("Tracked items could not be loaded");
  expect(screen.queryByText("No items tracked yet.")).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Retry tracked items" }));
  await screen.findByText("No items tracked yet.");
});
test("details expose an accessible dialog, Escape dismisses it and focus is restored", async () => {
  render(<PriceTrackerPage />);
  const trigger = await screen.findByRole("button", { name: "Case One" });
  trigger.focus(); fireEvent.click(trigger);
  expect(await screen.findByRole("dialog", { name: "Case One" })).toBeInTheDocument();
  fireEvent.keyDown(document, { key: "Escape", keyCode: 27 });
  await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  await waitFor(() => expect(trigger).toHaveFocus());
});

test("successful deletion restores focus to search when the original trigger no longer exists", async () => {
  PriceTrackerApi.deleteTrackedItem.mockResolvedValue({ message: "Item deleted successfully" });
  render(<PriceTrackerPage />);
  fireEvent.click(await screen.findByRole("button", { name: "Delete Case One from tracker" }));
  fireEvent.click(within(screen.getByRole("dialog", { name: "Remove Item?" })).getByRole("button", { name: "Remove Item" }));
  await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  await waitFor(() => expect(screen.getByRole("combobox", { name: "Search CS items to track" })).toHaveFocus());
  expect(screen.queryByRole("button", { name: "Case One" })).not.toBeInTheDocument();
});

vi.mock("./PriceTrackerApi", () => ({
  default: { getTrackedItems: vi.fn(), getPriceHistory: vi.fn(), toggleBookmark: vi.fn(), deleteTrackedItem: vi.fn() },
}));

vi.mock("../catalogue/CatalogueApi", () => ({
  default: { searchCsItems: vi.fn() },
}));
