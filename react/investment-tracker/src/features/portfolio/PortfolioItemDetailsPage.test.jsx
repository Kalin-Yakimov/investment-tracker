import { beforeEach, expect, test, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import PortfolioApi from "./PortfolioApi";
import AuthService from "../auth/AuthService";
import PortfolioItemDetailsPage from "./PortfolioItemDetailsPage";

vi.mock("./PortfolioApi", () => ({
  default: {
    getItem: vi.fn(),
    getItemByName: vi.fn(),
    deleteItem: vi.fn(),
  },
}));
vi.mock("../auth/AuthService", () => ({
  default: { getSessionVersion: vi.fn() },
}));

const record = {
  id: 7, itemName: "Case", itemStatus: "BOUGHT",
  quantity: 2, price: 3, date: "10/10/2026",
};
const openItem = () => render(
  <MemoryRouter initialEntries={["/viewItem/7"]}>
    <Routes><Route path="/viewItem/:id" element={<PortfolioItemDetailsPage />} /></Routes>
  </MemoryRouter>,
);

beforeEach(() => {
  vi.clearAllMocks();
  AuthService.getSessionVersion.mockReturnValue(1);
  PortfolioApi.getItem.mockResolvedValue(record);
  PortfolioApi.getItemByName.mockResolvedValue({ content: [record], totalPages: 2 });
});

test("item details and transaction pagination use the portfolio service payload", async () => {
  const { unmount } = openItem();
  await screen.findByRole("heading", { name: "Transaction History for Case" });
  await screen.findByRole("button", { name: "Next" });
  expect(screen.getByText("€6.00")).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "Edit Item" })).toHaveAttribute("href", "/editItem/7");
  expect(PortfolioApi.getItem).toHaveBeenCalledWith("7", { signal: expect.any(AbortSignal) });
  fireEvent.click(screen.getByRole("button", { name: "Next" }));
  await waitFor(() => expect(PortfolioApi.getItemByName).toHaveBeenLastCalledWith(
    "Case", 1, 10, { signal: expect.any(AbortSignal) },
  ));
  expect(PortfolioApi.getItem).toHaveBeenCalledTimes(1);
  unmount();
});

test("an unavailable portfolio item retains the error view without loading its history", async () => {
  const log = vi.spyOn(console, "error").mockImplementation(() => {});
  try {
    PortfolioApi.getItem.mockRejectedValue({ response: { status: 404 } });
    const { unmount } = openItem();
    expect(await screen.findByRole("alert")).toHaveTextContent("Item not found");
    expect(screen.getByRole("link", { name: "Back to Portfolio" })).toHaveAttribute("href", "/");
    expect(PortfolioApi.getItemByName).not.toHaveBeenCalled();
    unmount();
  } finally {
    log.mockRestore();
  }
});
