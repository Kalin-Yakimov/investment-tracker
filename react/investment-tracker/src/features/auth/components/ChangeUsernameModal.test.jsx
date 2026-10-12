import { act, fireEvent, render, screen } from "@testing-library/react";
import ChangeUsernameModal from "./ChangeUsernameModal";
import AuthService from "../AuthService";
import deferred from "../../../testUtils/deferred";

jest.mock("../AuthService", () => ({
  __esModule: true, default: { getSessionVersion: jest.fn(), changeUsername: jest.fn() },
}));
beforeEach(() => {
  jest.clearAllMocks();
  jest.useFakeTimers();
  AuthService.getSessionVersion.mockReturnValue(1);
  AuthService.changeUsername.mockResolvedValue({});
});
afterEach(() => jest.useRealTimers());
const save = () => fireEvent.submit(screen.getByRole("button", { name: "Save Username" }).closest("form"));

test("closing the successful modal cancels its delayed close callback", async () => {
  const onClose = jest.fn();
  const { rerender } = render(<ChangeUsernameModal show currentUsername="Original" onClose={onClose} />);
  save();
  await act(async () => {});
  expect(screen.getByText("Username updated successfully!")).toBeInTheDocument();
  rerender(<ChangeUsernameModal show={false} currentUsername="Original" onClose={onClose} />);
  rerender(<ChangeUsernameModal show currentUsername="Original" onClose={onClose} />);
  act(() => jest.advanceTimersByTime(1500));
  expect(onClose).not.toHaveBeenCalled();
  expect(screen.queryByText("Username updated successfully!")).not.toBeInTheDocument();
});

test("a pending previous submission cannot report success in a reopened modal", async () => {
  const pending = deferred();
  AuthService.changeUsername.mockReturnValue(pending.promise);
  const onClose = jest.fn(), onSuccess = jest.fn();
  const { rerender } = render(<ChangeUsernameModal show currentUsername="Original" onClose={onClose} onSuccess={onSuccess} />);
  save();
  rerender(<ChangeUsernameModal show={false} currentUsername="Original" onClose={onClose} onSuccess={onSuccess} />);
  rerender(<ChangeUsernameModal show currentUsername="Original" onClose={onClose} onSuccess={onSuccess} />);
  await act(async () => pending.resolve({}));
  act(() => jest.advanceTimersByTime(1500));
  expect(onSuccess).not.toHaveBeenCalled();
  expect(onClose).not.toHaveBeenCalled();
});
