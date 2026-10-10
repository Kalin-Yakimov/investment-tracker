import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import PriceTracker from "./PriceTracker";
import UserService from "../service/UserService";
import deferred from "../testUtils/deferred";

jest.mock("../service/UserService", () => ({
  __esModule: true, default: {
    getSessionVersion: jest.fn(), getTrackedItems: jest.fn(), getPriceHistory: jest.fn(),
    searchCsItems: jest.fn(), toggleBookmark: jest.fn(), deleteTrackedItem: jest.fn(),
  },
}));
jest.mock("react-chartjs-2", () => ({ Line: () => null }));
beforeEach(() => {
  jest.clearAllMocks();
  UserService.getSessionVersion.mockReturnValue(1);
  UserService.getTrackedItems.mockResolvedValue([
    { id: 1, itemName: "Case One", marketHashName: "Case One", currentPrice: 2, showInDashboard: false },
    { id: 2, itemName: "Case Two", marketHashName: "Case Two", currentPrice: 3, showInDashboard: false },
  ]);
  UserService.getPriceHistory.mockResolvedValue([]);
});

test("bookmark changes do not refetch unchanged histories or allow overlapping writes", async () => {
  const pending = deferred();
  UserService.toggleBookmark.mockReturnValue(pending.promise);
  render(<PriceTracker />);
  await screen.findByText("Case One");
  await act(async () => {});
  await waitFor(() => expect(UserService.getPriceHistory).toHaveBeenCalledTimes(2));
  const buttons = screen.getAllByTitle("Pin to Main Dashboard");
  fireEvent.click(buttons[0]);
  fireEvent.click(buttons[1]);
  expect(UserService.toggleBookmark).toHaveBeenCalledTimes(1);
  expect(buttons[1]).toBeDisabled();
  await act(async () => pending.resolve({ bookmarked: true }));
  expect(UserService.getPriceHistory).toHaveBeenCalledTimes(2);
  expect(screen.getByTitle("Unpin from Main Dashboard")).toBeInTheDocument();
});

test("the historical log shows newest publication first without reversing chart data", async () => {
  const history = [
    { id: 1, dateExtracted: "2026-10-09T10:25:00Z", price: 2 },
    { id: 2, dateExtracted: "2026-10-10T13:25:01Z", price: 2 },
  ];
  UserService.getPriceHistory.mockResolvedValue(history);
  render(<PriceTracker />);
  await screen.findByText("Case One");
  await waitFor(() => expect(UserService.getPriceHistory).toHaveBeenCalledTimes(2));
  await act(async () => {});
  fireEvent.click(screen.getByText("Case One"));
  const table = screen.getByText("Publication Date (UTC)").closest("table");
  const dates = within(table).getAllByRole("row").slice(1)
    .map((row) => within(row).getAllByRole("cell")[0].textContent);
  expect(dates).toEqual(["10/10/2026 13:25", "09/10/2026 10:25"]);
  expect(history.map((point) => point.id)).toEqual([1, 2]);
});
