import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, test, vi } from "vitest";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import EditPortfolioItemPage from "./EditPortfolioItemPage";
import PortfolioApi from "./PortfolioApi";
vi.mock("../auth/AuthService", () => ({ default: { getSessionVersion: () => 1 } }));
vi.mock("./PortfolioApi", () => ({ default: { getItem: vi.fn(), updateItem: vi.fn() } }));
const item = { id: 1, itemName: "Catalogue Case", csItemId: "case", itemStatus: "BOUGHT", date: "2026-10-10", quantity: 2, price: 0 };
beforeEach(() => { vi.clearAllMocks(); PortfolioApi.getItem.mockResolvedValue(item); PortfolioApi.updateItem.mockResolvedValue(item); });
function open() { return render(<MemoryRouter initialEntries={["/editItem/1"]}><Routes>
  <Route path="/editItem/:id" element={<EditPortfolioItemPage />} /><Route path="/viewItem/1" element={<div>Saved record</div>} />
</Routes></MemoryRouter>); }
test("a loaded numeric zero price can be saved and a catalogue-linked name is read-only", async () => {
  open(); await screen.findByDisplayValue("Catalogue Case");
  expect(screen.getByLabelText("Item Name")).toHaveAttribute("readonly");
  expect(screen.getByLabelText("Date")).toHaveValue("10.10.2026");
  fireEvent.click(screen.getByRole("button", { name: "Update Item" }));
  await screen.findByText("Saved record");
  expect(PortfolioApi.updateItem).toHaveBeenCalledWith("1", { itemName: "Catalogue Case", csItemId: "case", itemStatus: "BOUGHT", date: "10.10.2026", quantity: 2, price: "0" });
});
test("load failure disables saving and a successful explicit retry restores the form", async () => {
  PortfolioApi.getItem.mockRejectedValueOnce(new Error("offline")).mockResolvedValue(item);
  open(); expect(await screen.findByRole("alert")).toHaveTextContent("could not be loaded");
  expect(screen.getByRole("button", { name: "Update Item" })).toBeDisabled();
  expect(PortfolioApi.updateItem).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "Retry loading item" }));
  await waitFor(() => expect(screen.getByRole("button", { name: "Update Item" })).toBeEnabled());
});
test("a free-text legacy name remains editable and server validation is displayed without losing inputs", async () => {
  PortfolioApi.getItem.mockResolvedValue({ ...item, csItemId: null });
  PortfolioApi.updateItem.mockRejectedValue({ response: { data: { validationErrors: ["Price is invalid"] } } });
  open(); await screen.findByDisplayValue("Catalogue Case");
  const name = screen.getByLabelText("Item Name"); expect(name).not.toHaveAttribute("readonly");
  fireEvent.change(name, { target: { value: "Manual name" } });
  fireEvent.click(screen.getByRole("button", { name: "Update Item" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Price is invalid");
  expect(name).toHaveValue("Manual name");
});
