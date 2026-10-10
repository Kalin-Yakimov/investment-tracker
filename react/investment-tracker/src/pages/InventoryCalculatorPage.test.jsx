import { act, fireEvent, render, screen, within } from "@testing-library/react";
import deferred from "../testUtils/deferred";
import InventoryCalculatorPage from "./InventoryCalculatorPage";
import UserService from "../service/UserService";
import {
  clearInventoryCalculatorSession,
  loadInventoryCalculatorSession,
  saveInventoryCalculatorSession,
} from "../service/InventoryCalculatorSession";

jest.mock("../service/UserService", () => ({
  __esModule: true,
  default: {
    getCurrentUser: jest.fn(),
    calculateSteamInventory: jest.fn(),
  },
}));

const makeResult = (profileName, steamId64) => ({
  profileName,
  steamId64,
  avatarUrl: null,
  totalValue: 12.5,
  totalItemCount: 1,
  uniqueItemsCount: 1,
  mostValuableItem: null,
  items: [],
});

beforeEach(() => {
  clearInventoryCalculatorSession();
  jest.clearAllMocks();
  UserService.getCurrentUser.mockReturnValue({ id: 42 });
});

test("shows the saved result after returning to the calculator", async () => {
  UserService.calculateSteamInventory.mockResolvedValue(makeResult("First Profile", "123"));
  const { unmount } = render(<InventoryCalculatorPage />);

  fireEvent.change(screen.getByPlaceholderText(/paste steam profile url/i), {
    target: { value: "profile-a" },
  });
  fireEvent.click(screen.getByRole("button", { name: /calculate value/i }));
  await screen.findByRole("heading", { name: "First Profile" });

  unmount();
  render(<InventoryCalculatorPage />);

  expect(screen.getByRole("heading", { name: "First Profile" })).toBeInTheDocument();
  expect(screen.getByDisplayValue("profile-a")).toBeInTheDocument();
  expect(UserService.calculateSteamInventory).toHaveBeenCalledTimes(1);
});

test("replaces the old result after a successful search for another profile", async () => {
  saveInventoryCalculatorSession(42, "profile-a", makeResult("First Profile", "123"));
  UserService.calculateSteamInventory.mockResolvedValue(makeResult("Second Profile", "456"));
  render(<InventoryCalculatorPage />);

  fireEvent.change(screen.getByPlaceholderText(/paste steam profile url/i), {
    target: { value: "profile-b" },
  });
  fireEvent.click(screen.getByRole("button", { name: /calculate value/i }));
  await screen.findByRole("heading", { name: "Second Profile" });

  expect(screen.queryByRole("heading", { name: "First Profile" })).not.toBeInTheDocument();
  expect(loadInventoryCalculatorSession(42).steamUrl).toBe("profile-b");
});

test("keeps the previous result when a new search fails", async () => {
  saveInventoryCalculatorSession(42, "profile-a", makeResult("First Profile", "123"));
  UserService.calculateSteamInventory.mockRejectedValue({ error: "Profile unavailable" });
  const logError = jest.spyOn(console, "error").mockImplementation(() => {});

  try {
    render(<InventoryCalculatorPage />);
    fireEvent.change(screen.getByPlaceholderText(/paste steam profile url/i), {
      target: { value: "profile-b" },
    });
    fireEvent.click(screen.getByRole("button", { name: /calculate value/i }));
    await screen.findByText("Profile unavailable");

    expect(screen.getByRole("heading", { name: "First Profile" })).toBeInTheDocument();
    expect(loadInventoryCalculatorSession(42).steamUrl).toBe("profile-a");
  } finally {
    logError.mockRestore();
  }
});

test("shows missing prices as N/A and labels the known subtotal", () => {
  const result = {
    ...makeResult("Partial Profile", "123"),
    totalItemCount: 3,
    uniqueItemsCount: 2,
    unpricedItemCount: 2,
    items: [
      { itemName: "Known Case", quantity: 1, unitPrice: 12.5, totalPrice: 12.5, typeCategory: "Cases" },
      { itemName: "Unknown Item", quantity: 2, unitPrice: null, totalPrice: null, typeCategory: "Other" },
    ],
  };
  saveInventoryCalculatorSession(42, "profile-a", result);
  render(<InventoryCalculatorPage />);
  expect(screen.getByText("Known Value: €12.50")).toBeInTheDocument();
  expect(screen.getByText(/Prices are unavailable for 2 of 3 items/)).toBeInTheDocument();
  const row = screen.getByRole("row", { name: /Unknown Item/ });
  expect(within(row).getAllByText("N/A")).toHaveLength(2);
  expect(within(row).queryByText("€0.00")).not.toBeInTheDocument();
});

test("does not confuse a valid zero price with an unavailable price", () => {
  const result = {
    ...makeResult("Zero Profile", "123"), totalValue: 0, unpricedItemCount: 0,
    items: [{ itemName: "Zero Item", quantity: 1, unitPrice: 0, totalPrice: 0, typeCategory: "Other" }],
  };
  saveInventoryCalculatorSession(42, "profile-a", result);
  render(<InventoryCalculatorPage />);
  const row = screen.getByRole("row", { name: /Zero Item/ });
  expect(within(row).getAllByText("€0.00")).toHaveLength(2);
  expect(screen.queryByText(/Prices are unavailable/)).not.toBeInTheDocument();
});

test("explains a successful empty inventory without an error", () => {
  saveInventoryCalculatorSession(42, "profile-a", {
    ...makeResult("Empty Profile", "123"), totalValue: 0, totalItemCount: 0, uniqueItemsCount: 0,
  });
  render(<InventoryCalculatorPage />);
  expect(screen.getByText("This public CS2 inventory contains no items.")).toBeInTheDocument();
  expect(screen.queryByRole("heading", { name: "Calculation Error" })).not.toBeInTheDocument();
});

test("leaving the page aborts the calculation and prevents a late browser-session write", async () => {
  const pending = deferred();
  UserService.calculateSteamInventory.mockReturnValue(pending.promise);
  const { unmount } = render(<InventoryCalculatorPage />);
  fireEvent.change(screen.getByPlaceholderText(/paste steam profile url/i), { target: { value: "profile-a" } });
  fireEvent.click(screen.getByRole("button", { name: /calculate value/i }));
  const signal = UserService.calculateSteamInventory.mock.calls[0][1].signal;
  unmount();
  await act(async () => pending.resolve(makeResult("Late Profile", "123")));
  expect(signal.aborted).toBe(true);
  expect(loadInventoryCalculatorSession(42)).toBeNull();
});

test("duplicate form events cannot start two inventory calculations", async () => {
  const pending = deferred();
  UserService.calculateSteamInventory.mockReturnValue(pending.promise);
  render(<InventoryCalculatorPage />);
  fireEvent.change(screen.getByPlaceholderText(/paste steam profile url/i), { target: { value: "profile-a" } });
  const form = screen.getByPlaceholderText(/paste steam profile url/i).closest("form");
  fireEvent.submit(form);
  fireEvent.submit(form);
  expect(UserService.calculateSteamInventory).toHaveBeenCalledTimes(1);
  await act(async () => pending.resolve(makeResult("One Profile", "123")));
  expect(screen.getByRole("heading", { name: "One Profile" })).toBeInTheDocument();
});
