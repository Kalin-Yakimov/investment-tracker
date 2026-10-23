import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { act, fireEvent, render, screen } from "@testing-library/react";
import ChangeUsernameModal from "./ChangeUsernameModal";
import AuthService from "../AuthService";
import deferred from "../../../testUtils/deferred";

vi.mock("../AuthService", () => ({
  default: { getSessionVersion: vi.fn(), changeUsername: vi.fn() },
}));
beforeEach(() => {
  vi.clearAllMocks();
  vi.useFakeTimers();
  AuthService.getSessionVersion.mockReturnValue(1);
  AuthService.changeUsername.mockResolvedValue({});
});
afterEach(() => vi.useRealTimers());
const save = () => fireEvent.submit(screen.getByRole("button", { name: "Save Username" }).closest("form"));

test("closing the successful modal cancels its delayed close callback", async () => {
  const onClose = vi.fn();
  const { rerender } = render(<ChangeUsernameModal show currentUsername="Original" onClose={onClose} />);
  save();
  await act(async () => {});
  expect(screen.getByText("Username updated successfully!")).toBeInTheDocument();
  rerender(<ChangeUsernameModal show={false} currentUsername="Original" onClose={onClose} />);
  rerender(<ChangeUsernameModal show currentUsername="Original" onClose={onClose} />);
  act(() => vi.advanceTimersByTime(1500));
  expect(onClose).not.toHaveBeenCalled();
  expect(screen.queryByText("Username updated successfully!")).not.toBeInTheDocument();
});

test("a pending previous submission cannot report success in a reopened modal", async () => {
  const pending = deferred();
  AuthService.changeUsername.mockReturnValue(pending.promise);
  const onClose = vi.fn(), onSuccess = vi.fn();
  const { rerender } = render(<ChangeUsernameModal show currentUsername="Original" onClose={onClose} onSuccess={onSuccess} />);
  save();
  rerender(<ChangeUsernameModal show={false} currentUsername="Original" onClose={onClose} onSuccess={onSuccess} />);
  rerender(<ChangeUsernameModal show currentUsername="Original" onClose={onClose} onSuccess={onSuccess} />);
  await act(async () => pending.resolve({}));
  act(() => vi.advanceTimersByTime(1500));
  expect(onSuccess).not.toHaveBeenCalled();
  expect(onClose).not.toHaveBeenCalled();
});

test("a server-side username conflict displays the public API error", async () => {
  AuthService.changeUsername.mockRejectedValue({ error: "Username is already taken by another account", businessErrorCode: 305 });
  render(<ChangeUsernameModal show currentUsername="Original" onClose={vi.fn()} />);
  save();
  await act(async () => {});
  expect(screen.getByText("Username is already taken by another account")).toBeInTheDocument();
  expect(screen.queryByText("Username updated successfully!")).not.toBeInTheDocument();
});
