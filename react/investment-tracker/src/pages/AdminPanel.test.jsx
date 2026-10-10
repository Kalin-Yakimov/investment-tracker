import { act, fireEvent, render, screen } from "@testing-library/react";
import AdminPanel from "./AdminPanel";
import AdminService from "../service/AdminService";
import UserService from "../service/UserService";
import deferred from "../testUtils/deferred";

jest.mock("../service/AdminService", () => ({
  __esModule: true,
  default: {
    getOverview: jest.fn(), searchUsers: jest.fn(), getNewTicketCount: jest.fn(),
    getUserDetails: jest.fn(), getPortfolio: jest.fn(), getTrackedItems: jest.fn(),
    getSessions: jest.fn(), getActivity: jest.fn(), getAudit: jest.fn(), getTickets: jest.fn(),
    startCatalogueSync: jest.fn(),
  },
}));
jest.mock("../service/UserService", () => ({
  __esModule: true, default: { getCurrentUser: jest.fn(), hasAuthority: jest.fn(), getSessionVersion: jest.fn() },
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
  const view = render(<AdminPanel />);
  await act(async () => {});
  fireEvent.click(screen.getByRole("button", { name: "User support" }));
  return view;
}
beforeEach(() => {
  jest.clearAllMocks();
  UserService.getCurrentUser.mockReturnValue({ id: 99 });
  UserService.getSessionVersion.mockReturnValue(1);
  UserService.hasAuthority.mockReturnValue(true);
  AdminService.getOverview.mockResolvedValue({});
  AdminService.searchUsers.mockResolvedValue({ ...empty, content: [alice, bob] });
  AdminService.getNewTicketCount.mockResolvedValue(0);
  AdminService.getUserDetails.mockImplementation((id) => Promise.resolve(details(id === 1 ? alice : bob)));
  AdminService.getPortfolio.mockResolvedValue(empty);
  AdminService.getTrackedItems.mockResolvedValue([]);
  AdminService.getSessions.mockResolvedValue([]);
  AdminService.getActivity.mockResolvedValue(empty);
  AdminService.getAudit.mockResolvedValue(empty);
  AdminService.getTickets.mockResolvedValue(empty);
  AdminService.startCatalogueSync.mockResolvedValue({ message: "Synchronization started" });
});
afterEach(() => jest.useRealTimers());

test("late details for Alice cannot replace the selected Bob support view", async () => {
  const first = deferred();
  AdminService.getUserDetails.mockImplementation((id) => id === 1 ? first.promise : Promise.resolve(details(bob)));
  await openPanel();
  fireEvent.click(screen.getByRole("row", { name: /Alice/ }));
  fireEvent.change(screen.getByLabelText("Reason for access"), { target: { value: "Checking Alice's problem" } });
  fireEvent.click(screen.getByRole("button", { name: "Open read-only support view" }));
  const oldSignal = AdminService.getUserDetails.mock.calls[0][2].signal;
  await openUser("Bob");
  await act(async () => first.resolve(details(alice)));
  expect(oldSignal.aborted).toBe(true);
  expect(screen.getByRole("heading", { name: "Bob" })).toBeInTheDocument();
  expect(screen.queryByRole("heading", { name: "Alice" })).not.toBeInTheDocument();
});

test("late portfolio for a previous user cannot populate the new user's portfolio", async () => {
  const first = deferred();
  AdminService.getPortfolio.mockImplementation((id) => id === 1 ? first.promise : Promise.resolve({
    ...empty, content: [{ id: 22, itemName: "Bob Case", itemStatus: "BOUGHT", quantity: 1, price: 2 }],
  }));
  await openPanel();
  await openUser("Alice");
  fireEvent.click(screen.getByRole("button", { name: "Portfolio" }));
  const oldSignal = AdminService.getPortfolio.mock.calls[0][4].signal;
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
  AdminService.getPortfolio.mockReturnValue(old.promise);
  AdminService.getTrackedItems.mockReturnValue(latest.promise);
  const { container } = await openPanel();
  await openUser("Alice");
  fireEvent.click(screen.getByRole("button", { name: "Portfolio" }));
  const signal = AdminService.getPortfolio.mock.calls[0][4].signal;
  fireEvent.click(screen.getByRole("button", { name: "PriceTracker" }));
  await act(async () => old.resolve(empty));
  expect(signal.aborted).toBe(true);
  expect(container.querySelector(".admin-loading-bar")).not.toBeNull();
  await act(async () => latest.resolve([]));
  expect(container.querySelector(".admin-loading-bar")).toBeNull();
});

test("job polling never overlaps a pending overview and stops after completion", async () => {
  jest.useFakeTimers();
  const pending = deferred();
  AdminService.getOverview.mockResolvedValueOnce({ priceUpdate: { status: "RUNNING" } }).mockReturnValueOnce(pending.promise);
  const { unmount } = render(<AdminPanel />);
  await act(async () => {});
  expect(AdminService.getOverview).toHaveBeenCalledTimes(1);
  await act(async () => jest.advanceTimersByTime(10000));
  expect(AdminService.getOverview).toHaveBeenCalledTimes(2);
  await act(async () => pending.resolve({ priceUpdate: { status: "SUCCEEDED" } }));
  await act(async () => jest.advanceTimersByTime(10000));
  expect(AdminService.getOverview).toHaveBeenCalledTimes(2);
  unmount();
  expect(jest.getTimerCount()).toBe(0);
});

test("ticket-count polling does not overlap and is canceled on unmount", async () => {
  jest.useFakeTimers();
  const pending = deferred();
  AdminService.getNewTicketCount.mockResolvedValueOnce(0).mockReturnValueOnce(pending.promise);
  const { unmount } = render(<AdminPanel />);
  await act(async () => {});
  await act(async () => jest.advanceTimersByTime(90000));
  expect(AdminService.getNewTicketCount).toHaveBeenCalledTimes(2);
  const signal = AdminService.getNewTicketCount.mock.calls[1][0].signal;
  unmount();
  expect(signal.aborted).toBe(true);
  expect(jest.getTimerCount()).toBe(0);
});

test("newer contact-request filters cannot be overwritten by old results", async () => {
  const old = deferred();
  AdminService.getTickets.mockReturnValueOnce(old.promise).mockResolvedValue(empty);
  render(<AdminPanel />);
  await act(async () => {});
  fireEvent.click(screen.getByRole("button", { name: "Contact requests" }));
  const oldSignal = AdminService.getTickets.mock.calls[0][3].signal;
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
  jest.useFakeTimers();
  AdminService.getAudit.mockRejectedValue(new Error("First notice"));
  render(<AdminPanel />);
  await act(async () => {});
  fireEvent.click(screen.getByRole("button", { name: "Audit log" }));
  await act(async () => {});
  expect(screen.getByText("First notice")).toBeInTheDocument();
  act(() => jest.advanceTimersByTime(4000));
  AdminService.getOverview.mockRejectedValueOnce(new Error("Second notice"));
  fireEvent.click(screen.getByRole("button", { name: "Overview" }));
  await act(async () => {});
  act(() => jest.advanceTimersByTime(1000));
  expect(screen.getByText("Second notice")).toBeInTheDocument();
  act(() => jest.advanceTimersByTime(4000));
  expect(screen.queryByText("Second notice")).not.toBeInTheDocument();
});

test("starting a maintenance job replaces a pre-action overview request", async () => {
  jest.useFakeTimers();
  const stale = deferred();
  AdminService.getOverview
    .mockResolvedValueOnce({ priceUpdate: { status: "RUNNING" } })
    .mockReturnValueOnce(stale.promise)
    .mockResolvedValue({ priceUpdate: { status: "RUNNING" }, catalogueSync: { status: "RUNNING" } });
  render(<AdminPanel />);
  await act(async () => {});
  await act(async () => jest.advanceTimersByTime(2000));
  const signal = AdminService.getOverview.mock.calls[1][0].signal;
  fireEvent.click(screen.getByRole("button", { name: "Catalogue & prices" }));
  fireEvent.change(screen.getByLabelText("Reason for manual operation"), { target: { value: "Refresh the catalogue" } });
  fireEvent.click(screen.getByRole("button", { name: "Synchronize catalogue" }));
  await act(async () => {});
  expect(signal.aborted).toBe(true);
  expect(AdminService.getOverview).toHaveBeenCalledTimes(3);
  expect(screen.getByRole("button", { name: "Synchronize catalogue" })).toBeDisabled();
  await act(async () => stale.resolve({}));
  expect(screen.getByRole("button", { name: "Synchronize catalogue" })).toBeDisabled();
});
