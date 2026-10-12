import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { act, fireEvent, render, screen } from "@testing-library/react";
import SupportPanelPage from "./SupportPanelPage";
import SupportPanelApi from "./SupportPanelApi";
import AuthService from "../auth/AuthService";
import deferred from "../../testUtils/deferred";

vi.mock("./SupportPanelApi", () => ({
  default: {
    getOverview: vi.fn(), searchUsers: vi.fn(), getNewTicketCount: vi.fn(),
    getUserDetails: vi.fn(), getPortfolio: vi.fn(), getTrackedItems: vi.fn(),
    getSessions: vi.fn(), getActivity: vi.fn(), getAudit: vi.fn(), getTickets: vi.fn(),
    startCatalogueSync: vi.fn(),
  },
}));
vi.mock("../auth/AuthService", () => ({
  default: { getCurrentUser: vi.fn(), hasAuthority: vi.fn(), getSessionVersion: vi.fn() },
}));

const empty = { content: [], number: 0, totalPages: 0, totalElements: 0 };
const alice = { id: 1, username: "Alice", email: "alice@example.test", roles: ["USER"], enabled: true };
const bob = { id: 2, username: "Bob", email: "bob@example.test", roles: ["USER"], enabled: true };
const details = (user) => ({ user, lastSessionActivity: null });
const openUser = async (name) => {
  fireEvent.click(screen.getByRole("row", { name: new RegExp(name) }));
  fireEvent.change(screen.getByLabelText("Reason for access"), { target: { value: "Investigating a reported problem" } });
  fireEvent.click(screen.getByRole("button", { name: "Open read-only support view" }));
  await screen.findByRole("heading", { name });
};
async function openPanel() {
  const view = render(<SupportPanelPage />);
  await act(async () => {});
  fireEvent.click(screen.getByRole("button", { name: "User support" }));
  return view;
}
beforeEach(() => {
  vi.clearAllMocks();
  AuthService.getCurrentUser.mockReturnValue({ id: 99 });
  AuthService.getSessionVersion.mockReturnValue(1);
  AuthService.hasAuthority.mockReturnValue(true);
  SupportPanelApi.getOverview.mockResolvedValue({});
  SupportPanelApi.searchUsers.mockResolvedValue({ ...empty, content: [alice, bob] });
  SupportPanelApi.getNewTicketCount.mockResolvedValue(0);
  SupportPanelApi.getUserDetails.mockImplementation((id) => Promise.resolve(details(id === 1 ? alice : bob)));
  SupportPanelApi.getPortfolio.mockResolvedValue(empty);
  SupportPanelApi.getTrackedItems.mockResolvedValue([]);
  SupportPanelApi.getSessions.mockResolvedValue([]);
  SupportPanelApi.getActivity.mockResolvedValue(empty);
  SupportPanelApi.getAudit.mockResolvedValue(empty);
  SupportPanelApi.getTickets.mockResolvedValue(empty);
  SupportPanelApi.startCatalogueSync.mockResolvedValue({ message: "Synchronization started" });
});
afterEach(() => vi.useRealTimers());

test("late details for Alice cannot replace the selected Bob support view", async () => {
  const first = deferred();
  SupportPanelApi.getUserDetails.mockImplementation((id) => id === 1 ? first.promise : Promise.resolve(details(bob)));
  await openPanel();
  fireEvent.click(screen.getByRole("row", { name: /Alice/ }));
  fireEvent.change(screen.getByLabelText("Reason for access"), { target: { value: "Checking Alice's problem" } });
  fireEvent.click(screen.getByRole("button", { name: "Open read-only support view" }));
  const oldSignal = SupportPanelApi.getUserDetails.mock.calls[0][2].signal;
  await openUser("Bob");
  await act(async () => first.resolve(details(alice)));
  expect(oldSignal.aborted).toBe(true);
  expect(screen.getByRole("heading", { name: "Bob" })).toBeInTheDocument();
  expect(screen.queryByRole("heading", { name: "Alice" })).not.toBeInTheDocument();
});

test("late portfolio for a previous user cannot populate the new user's portfolio", async () => {
  const first = deferred();
  SupportPanelApi.getPortfolio.mockImplementation((id) => id === 1 ? first.promise : Promise.resolve({
    ...empty, content: [{ id: 22, itemName: "Bob Case", itemStatus: "BOUGHT", quantity: 1, price: 2 }],
  }));
  await openPanel();
  await openUser("Alice");
  fireEvent.click(screen.getByRole("button", { name: "Portfolio" }));
  const oldSignal = SupportPanelApi.getPortfolio.mock.calls[0][4].signal;
  await openUser("Bob");
  fireEvent.click(screen.getByRole("button", { name: "Portfolio" }));
  await screen.findByText("Bob Case");
  await act(async () => first.resolve({
    ...empty, content: [{ id: 11, itemName: "Alice Case", itemStatus: "BOUGHT", quantity: 1, price: 9 }],
  }));
  expect(oldSignal.aborted).toBe(true);
  expect(screen.queryByText("Alice Case")).not.toBeInTheDocument();
  expect(screen.getByText("Bob Case")).toBeInTheDocument();
});

test("tab cancellation cannot hide the pending indicator of a newer tab request", async () => {
  const old = deferred(), latest = deferred();
  SupportPanelApi.getPortfolio.mockReturnValue(old.promise);
  SupportPanelApi.getTrackedItems.mockReturnValue(latest.promise);
  const { container } = await openPanel();
  await openUser("Alice");
  fireEvent.click(screen.getByRole("button", { name: "Portfolio" }));
  const signal = SupportPanelApi.getPortfolio.mock.calls[0][4].signal;
  fireEvent.click(screen.getByRole("button", { name: "PriceTracker" }));
  await act(async () => old.resolve(empty));
  expect(signal.aborted).toBe(true);
  expect(container.querySelector(".admin-loading-bar")).not.toBeNull();
  await act(async () => latest.resolve([]));
  expect(container.querySelector(".admin-loading-bar")).toBeNull();
});

test("job polling never overlaps a pending overview and stops after completion", async () => {
  vi.useFakeTimers();
  const pending = deferred();
  SupportPanelApi.getOverview.mockResolvedValueOnce({ priceUpdate: { status: "RUNNING" } }).mockReturnValueOnce(pending.promise);
  const { unmount } = render(<SupportPanelPage />);
  await act(async () => {});
  expect(SupportPanelApi.getOverview).toHaveBeenCalledTimes(1);
  await act(async () => vi.advanceTimersByTime(10000));
  expect(SupportPanelApi.getOverview).toHaveBeenCalledTimes(2);
  await act(async () => pending.resolve({ priceUpdate: { status: "SUCCEEDED" } }));
  await act(async () => vi.advanceTimersByTime(10000));
  expect(SupportPanelApi.getOverview).toHaveBeenCalledTimes(2);
  unmount();
  expect(vi.getTimerCount()).toBe(0);
});

test("ticket-count polling does not overlap and is canceled on unmount", async () => {
  vi.useFakeTimers();
  const pending = deferred();
  SupportPanelApi.getNewTicketCount.mockResolvedValueOnce(0).mockReturnValueOnce(pending.promise);
  const { unmount } = render(<SupportPanelPage />);
  await act(async () => {});
  await act(async () => vi.advanceTimersByTime(90000));
  expect(SupportPanelApi.getNewTicketCount).toHaveBeenCalledTimes(2);
  const signal = SupportPanelApi.getNewTicketCount.mock.calls[1][0].signal;
  unmount();
  expect(signal.aborted).toBe(true);
  expect(vi.getTimerCount()).toBe(0);
});

test("newer contact-request filters cannot be overwritten by old results", async () => {
  const old = deferred();
  SupportPanelApi.getTickets.mockReturnValueOnce(old.promise).mockResolvedValue(empty);
  render(<SupportPanelPage />);
  await act(async () => {});
  fireEvent.click(screen.getByRole("button", { name: "Contact requests" }));
  const oldSignal = SupportPanelApi.getTickets.mock.calls[0][3].signal;
  fireEvent.change(screen.getByLabelText("Filter contact requests by status"), { target: { value: "RESOLVED" } });
  await act(async () => {});
  await act(async () => old.resolve({
    ...empty, content: [{ id: 1, message: "Old new ticket", status: "NEW", createdAt: null, updatedAt: null }],
  }));
  expect(oldSignal.aborted).toBe(true);
  expect(screen.queryByText("Old new ticket")).not.toBeInTheDocument();
  expect(screen.getByLabelText("Filter contact requests by status")).toHaveValue("RESOLVED");
});

test("an older notice timer cannot dismiss the replacement notice early", async () => {
  vi.useFakeTimers();
  SupportPanelApi.getAudit.mockRejectedValue(new Error("First notice"));
  render(<SupportPanelPage />);
  await act(async () => {});
  fireEvent.click(screen.getByRole("button", { name: "Audit log" }));
  await act(async () => {});
  expect(screen.getByText("First notice")).toBeInTheDocument();
  act(() => vi.advanceTimersByTime(4000));
  SupportPanelApi.getOverview.mockRejectedValueOnce(new Error("Second notice"));
  fireEvent.click(screen.getByRole("button", { name: "Overview" }));
  await act(async () => {});
  act(() => vi.advanceTimersByTime(1000));
  expect(screen.getByText("Second notice")).toBeInTheDocument();
  act(() => vi.advanceTimersByTime(4000));
  expect(screen.queryByText("Second notice")).not.toBeInTheDocument();
});

test("starting a maintenance job replaces a pre-action overview request", async () => {
  vi.useFakeTimers();
  const stale = deferred();
  SupportPanelApi.getOverview
    .mockResolvedValueOnce({ priceUpdate: { status: "RUNNING" } })
    .mockReturnValueOnce(stale.promise)
    .mockResolvedValue({ priceUpdate: { status: "RUNNING" }, catalogueSync: { status: "RUNNING" } });
  render(<SupportPanelPage />);
  await act(async () => {});
  await act(async () => vi.advanceTimersByTime(2000));
  const signal = SupportPanelApi.getOverview.mock.calls[1][0].signal;
  fireEvent.click(screen.getByRole("button", { name: "Catalogue & prices" }));
  fireEvent.change(screen.getByLabelText("Reason for manual operation"), { target: { value: "Refresh the catalogue" } });
  fireEvent.click(screen.getByRole("button", { name: "Synchronize catalogue" }));
  await act(async () => {});
  expect(signal.aborted).toBe(true);
  expect(SupportPanelApi.getOverview).toHaveBeenCalledTimes(3);
  expect(screen.getByRole("button", { name: "Synchronize catalogue" })).toBeDisabled();
  await act(async () => stale.resolve({}));
  expect(screen.getByRole("button", { name: "Synchronize catalogue" })).toBeDisabled();
});
