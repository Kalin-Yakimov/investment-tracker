import { act, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import Home from "./Home";
import UserService from "../service/UserService";
import deferred from "../testUtils/deferred";

jest.mock("../service/UserService", () => ({
  __esModule: true, default: {
    getSessionVersion: jest.fn(), getUserItems: jest.fn(), getTrackedItems: jest.fn(),
    getPortfolioSummary: jest.fn(), getPriceHistory: jest.fn(),
  },
}));
jest.mock("react-chartjs-2", () => ({ Line: () => null }));
const page = (name) => ({ content: [{ id: 1, itemName: name, itemStatus: "BOUGHT", quantity: 1, price: 2 }], totalPages: 2 });
beforeEach(() => {
  jest.clearAllMocks();
  UserService.getSessionVersion.mockReturnValue(1);
  UserService.getTrackedItems.mockResolvedValue([]);
  UserService.getPortfolioSummary.mockResolvedValue({ count: 1, totalInvestmentValue: 2 });
  global.IntersectionObserver = jest.fn(() => ({ observe: jest.fn(), disconnect: jest.fn() }));
});

test("new filter wins even when the initial request finishes later", async () => {
  const old = deferred();
  UserService.getUserItems.mockReturnValueOnce(old.promise).mockResolvedValue(page("New result"));
  render(<MemoryRouter><Home /></MemoryRouter>);
  const signal = UserService.getUserItems.mock.calls[0][3].signal;
  fireEvent.change(screen.getByRole("combobox"), { target: { value: "bought" } });
  await screen.findByText("New result");
  await act(async () => old.resolve(page("Old result")));
  expect(signal.aborted).toBe(true);
  expect(screen.queryByText("Old result")).not.toBeInTheDocument();
  expect(UserService.getTrackedItems).toHaveBeenCalledTimes(1);
  expect(UserService.getPortfolioSummary).toHaveBeenCalledTimes(1);
});

test("pagination does not reload unrelated pinned items or portfolio totals", async () => {
  UserService.getUserItems.mockResolvedValue(page("One Case"));
  const { unmount } = render(<MemoryRouter><Home /></MemoryRouter>);
  await screen.findByText("One Case");
  fireEvent.click(screen.getByRole("button", { name: "Next" }));
  await act(async () => {});
  expect(UserService.getUserItems).toHaveBeenCalledTimes(2);
  expect(UserService.getUserItems.mock.calls[1].slice(0, 3)).toEqual([1, 10, "newest_date"]);
  expect(UserService.getTrackedItems).toHaveBeenCalledTimes(1);
  expect(UserService.getPortfolioSummary).toHaveBeenCalledTimes(1);
  unmount();
  expect(global.IntersectionObserver.mock.results[0].value.disconnect).toHaveBeenCalled();
});
