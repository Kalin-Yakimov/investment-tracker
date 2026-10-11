import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import PortfolioService from "../service/PortfolioService";
import UserService from "../service/UserService";
import ViewItem from "./ViewItem";

jest.mock("../service/PortfolioService", () => ({
  __esModule: true,
  default: {
    getItem: jest.fn(),
    getItemByName: jest.fn(),
    deleteItem: jest.fn(),
  },
}));
jest.mock("../service/UserService", () => ({
  __esModule: true,
  default: { getSessionVersion: jest.fn() },
}));

const record = {
  id: 7, itemName: "Case", itemStatus: "BOUGHT",
  quantity: 2, price: 3, date: "10/10/2026",
};
const openItem = () => render(
  <MemoryRouter initialEntries={["/viewItem/7"]}>
    <Routes><Route path="/viewItem/:id" element={<ViewItem />} /></Routes>
  </MemoryRouter>,
);

beforeEach(() => {
  jest.clearAllMocks();
  UserService.getSessionVersion.mockReturnValue(1);
  PortfolioService.getItem.mockResolvedValue(record);
  PortfolioService.getItemByName.mockResolvedValue({ content: [record], totalPages: 2 });
});

test("item details and transaction pagination use the portfolio service payload", async () => {
  const { unmount } = openItem();
  await screen.findByRole("heading", { name: "Transaction History for Case" });
  await screen.findByRole("button", { name: "Next" });
  expect(screen.getByText("€6.00")).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "Edit Item" })).toHaveAttribute("href", "/editItem/7");
  expect(PortfolioService.getItem).toHaveBeenCalledWith("7", { signal: expect.any(AbortSignal) });
  fireEvent.click(screen.getByRole("button", { name: "Next" }));
  await waitFor(() => expect(PortfolioService.getItemByName).toHaveBeenLastCalledWith(
    "Case", 1, 10, { signal: expect.any(AbortSignal) },
  ));
  expect(PortfolioService.getItem).toHaveBeenCalledTimes(1);
  unmount();
});

test("an unavailable portfolio item retains the error view without loading its history", async () => {
  const log = jest.spyOn(console, "error").mockImplementation(() => {});
  try {
    PortfolioService.getItem.mockRejectedValue({ response: { status: 404 } });
    const { unmount } = openItem();
    expect(await screen.findByRole("alert")).toHaveTextContent("Item not found");
    expect(screen.getByRole("link", { name: "Back to Portfolio" })).toHaveAttribute("href", "/");
    expect(PortfolioService.getItemByName).not.toHaveBeenCalled();
    unmount();
  } finally {
    log.mockRestore();
  }
});
