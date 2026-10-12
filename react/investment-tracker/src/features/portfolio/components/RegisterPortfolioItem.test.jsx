import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import RegisterPortfolioItem from "./RegisterPortfolioItem";
import CatalogueApi from "../../catalogue/CatalogueApi";
import { ITEM_SEARCH_DEBOUNCE_MS } from "../../../config/uiConfig";
import deferred from "../../../testUtils/deferred";

vi.mock("../../auth/AuthService", () => ({
  default: { getSessionVersion: () => 1 },
}));
vi.mock("../../catalogue/CatalogueApi", () => ({
  default: { searchCsItems: vi.fn().mockResolvedValue([]) },
}));

beforeEach(() => {
  vi.clearAllMocks();
  CatalogueApi.searchCsItems.mockReset().mockResolvedValue([]);
});
afterEach(() => vi.useRealTimers());

// Use the real date picker, not a form/library mock. The separate client
// resolution test covers Vite's browser entry (Vitest's Node entry differs).
test("renders the real portfolio registration form and date picker", () => {
  const { container } = render(<RegisterPortfolioItem />);
  expect(screen.getByRole("heading", { name: "Register Portfolio Item" })).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Register Item" })).toBeInTheDocument();
  const dateInput = container.querySelector(".react-datepicker__input-container input");
  expect(dateInput).toBeInTheDocument();
  expect(dateInput.value).toMatch(/^\d{2}\.\d{2}\.\d{4}$/);
  fireEvent.click(dateInput);
  expect(screen.getByRole("dialog")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Next Month" })).toBeInTheDocument();
});

test("keeps the portfolio dropdown mounted while typing and replaces its rows without flickering", async () => {
  vi.useFakeTimers();
  const next = deferred();
  CatalogueApi.searchCsItems.mockResolvedValueOnce([{ id: "first", name: "AK-47" }])
    .mockReturnValueOnce(next.promise);
  render(<RegisterPortfolioItem />);
  const input = screen.getByLabelText("Item Name");
  fireEvent.change(input, { target: { value: "AK" } });
  await act(async () => { await vi.advanceTimersByTimeAsync(ITEM_SEARCH_DEBOUNCE_MS); });
  const dropdown = screen.getByRole("list");
  expect(screen.getByText("AK-47")).toBeInTheDocument();
  fireEvent.change(input, { target: { value: "AK-47" } });
  expect(screen.getByRole("list")).toBe(dropdown);
  await act(async () => { await vi.advanceTimersByTimeAsync(ITEM_SEARCH_DEBOUNCE_MS); });
  expect(screen.getByRole("list")).toBe(dropdown);
  await act(async () => next.resolve([{ id: "redline", name: "AK-47 | Redline" }]));
  expect(screen.getByRole("list")).toBe(dropdown);
  expect(screen.queryByText("AK-47", { exact: true })).not.toBeInTheDocument();
  fireEvent.click(screen.getByText("AK-47 | Redline"));
  expect(input).toHaveValue("AK-47 | Redline");
  expect(screen.queryByRole("list")).not.toBeInTheDocument();
});
