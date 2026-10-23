import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import AuthService from "./AuthService";
import RegistrationPage from "./pages/RegistrationPage";
import ForgotPasswordPage from "./pages/ForgotPasswordPage";
import ChangePasswordModal from "./components/ChangePasswordModal";

beforeEach(() => {
  vi.spyOn(AuthService, "register").mockResolvedValue({});
  vi.spyOn(AuthService, "forgotPassword").mockResolvedValue({});
  vi.spyOn(AuthService, "resetPassword").mockResolvedValue({});
  vi.spyOn(AuthService, "changePassword").mockResolvedValue({});
});
afterEach(() => vi.restoreAllMocks());
const fill = (label, value) => fireEvent.change(screen.getByLabelText(label), { target: { value } });
const submit = (name) => fireEvent.submit(screen.getByRole("button", { name }).closest("form"));

test("registration rejects nine characters, then submits exactly ten characters unchanged", async () => {
  render(<MemoryRouter><RegistrationPage /></MemoryRouter>);
  fill("Username", "test-user");
  fill("Email Address", "test@example.invalid");
  fill("Password", "123456789");
  fill("Confirm Password", "123456789");
  submit("Register Account");
  expect(screen.getByText("Password must be at least 10 characters long.")).toBeInTheDocument();
  expect(AuthService.register).not.toHaveBeenCalled();
  fill("Password", "new-pass10");
  fill("Confirm Password", "new-pass10");
  submit("Register Account");
  await waitFor(() => expect(AuthService.register).toHaveBeenCalledWith({
    username: "test-user", email: "test@example.invalid", password: "new-pass10",
  }));
});

test("reset applies the same minimum and byte limit before sending the one-time code", async () => {
  render(<MemoryRouter><ForgotPasswordPage /></MemoryRouter>);
  fill("Email Address", "test@example.invalid");
  submit("Send Reset Code");
  await screen.findByLabelText("New Password");
  fill("6-Digit Reset Code", "123456");
  fill("New Password", "123456789");
  fill("Confirm New Password", "123456789");
  submit("Reset Password");
  expect(screen.getByText("Password must be at least 10 characters long.")).toBeInTheDocument();
  expect(AuthService.resetPassword).not.toHaveBeenCalled();
  fill("New Password", "я".repeat(37));
  fill("Confirm New Password", "я".repeat(37));
  submit("Reset Password");
  expect(screen.getByText(/Password is too long/)).toBeInTheDocument();
  expect(AuthService.resetPassword).not.toHaveBeenCalled();
  fill("New Password", "нова парола");
  fill("Confirm New Password", "нова парола");
  submit("Reset Password");
  await screen.findByText("Your password has been successfully reset!");
  expect(AuthService.resetPassword).toHaveBeenCalledWith("test@example.invalid", "123456", "нова парола");
});

test("change-password allows a short current password and displays the server's expected error", async () => {
  AuthService.changePassword.mockRejectedValue({ error: "Current password is incorrect", businessErrorCode: 300 });
  render(<ChangePasswordModal show onClose={vi.fn()} />);
  fill("Current Password", "legacy");
  fill("New Password", "123456789");
  fill("Confirm New Password", "123456789");
  submit("Update Password");
  expect(screen.getByText("Password must be at least 10 characters long.")).toBeInTheDocument();
  expect(AuthService.changePassword).not.toHaveBeenCalled();
  fill("New Password", "new-pass10");
  fill("Confirm New Password", "new-pass10");
  submit("Update Password");
  await screen.findByText("Current password is incorrect");
  expect(AuthService.changePassword).toHaveBeenCalledWith({
    currentPassword: "legacy", newPassword: "new-pass10", confirmationPassword: "new-pass10",
  });
  expect(screen.getByLabelText("Current Password")).toHaveValue("legacy");
  expect(screen.queryByText("Password updated successfully!")).not.toBeInTheDocument();
});
