import { useState } from "react";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import CatalogueCombobox from "./CatalogueCombobox";
import CatalogueApi from "./CatalogueApi";
import { ITEM_SEARCH_DEBOUNCE_MS } from "../../config/uiConfig";
vi.mock("../auth/AuthService", () => ({ default: { getSessionVersion: () => 1 } }));
vi.mock("./CatalogueApi", () => ({ default: { searchCsItems: vi.fn() } }));
const rows = [{ id: "one", name: "Case One" }, { id: "two", name: "Case Two" }];
beforeEach(() => { vi.useFakeTimers(); CatalogueApi.searchCsItems.mockReset().mockResolvedValue(rows); });
afterEach(() => vi.useRealTimers());
function Search({ onSelect, submit }) {
  const [value, setValue] = useState("");
  return <form onSubmit={submit}><CatalogueCombobox label="Find item" value={value} onChange={setValue} onSelect={onSelect} /><button type="button">Outside</button></form>;
}
const search = async () => {
  fireEvent.change(screen.getByRole("combobox"), { target: { value: "Case" } });
  await act(async () => { await vi.advanceTimersByTimeAsync(ITEM_SEARCH_DEBOUNCE_MS); });
};
test("Arrow keys choose the active result, Enter selects without submitting, and Escape closes", async () => {
  const selected = vi.fn(), submit = vi.fn(event => event.preventDefault());
  render(<Search onSelect={selected} submit={submit} />); await search();
  const input = screen.getByRole("combobox");
  fireEvent.keyDown(input, { key: "ArrowDown" });
  const first = screen.getByRole("option", { name: "Case One" });
  expect(first).toHaveAttribute("aria-selected", "true");
  expect(input).toHaveAttribute("aria-activedescendant", first.id);
  fireEvent.keyDown(input, { key: "ArrowDown" }); fireEvent.keyDown(input, { key: "Enter" });
  expect(selected).toHaveBeenCalledWith(rows[1]); expect(submit).not.toHaveBeenCalled();
  fireEvent.focus(input); fireEvent.keyDown(input, { key: "Escape" });
  expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
  expect(input).toHaveAttribute("aria-expanded", "false");
});
test("failure offers an explicit search retry instead of silently appearing empty", async () => {
  CatalogueApi.searchCsItems.mockRejectedValueOnce(new Error("offline")).mockResolvedValue(rows);
  render(<Search onSelect={vi.fn()} submit={vi.fn()} />); await search();
  expect(screen.getByRole("alert")).toHaveTextContent("could not be loaded");
  fireEvent.click(screen.getByRole("button", { name: "Retry search" }));
  await act(async () => { await vi.advanceTimersByTimeAsync(ITEM_SEARCH_DEBOUNCE_MS); });
  expect(screen.getAllByRole("option")).toHaveLength(2);
  expect(CatalogueApi.searchCsItems).toHaveBeenCalledTimes(2);
});
