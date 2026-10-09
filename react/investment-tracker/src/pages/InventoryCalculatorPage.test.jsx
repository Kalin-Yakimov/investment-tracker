import { fireEvent, render, screen } from "@testing-library/react";
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
