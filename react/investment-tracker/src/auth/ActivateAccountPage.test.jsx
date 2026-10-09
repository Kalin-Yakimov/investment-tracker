import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import ActivateAccountPage from "./ActivateAccountPage";
import UserService from "../service/UserService";
import { rememberActivationEmail } from "./authFeedback";

jest.mock("../service/UserService", () => ({
  __esModule: true,
  default: { confirm: jest.fn(), resendActivation: jest.fn() },
}));

function openActivation(state) {
  return render(
    <MemoryRouter initialEntries={[{ pathname: "/activate-account", state }]}>
      <Routes>
        <Route path="/activate-account" element={<ActivateAccountPage />} />
        <Route path="/login" element={<div>Sign in screen</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  jest.clearAllMocks();
  sessionStorage.clear();
});

test("submits the code together with the registration email and clears it on success", async () => {
  UserService.confirm.mockResolvedValue(undefined);
  rememberActivationEmail("person@example.com");
  openActivation({ email: "person@example.com" });
  expect(screen.getByLabelText("Email Address")).toHaveValue("person@example.com");
  fireEvent.change(screen.getByLabelText("Activation Code"), { target: { value: "012345" } });
  fireEvent.click(screen.getByRole("button", { name: "Activate Account" }));
  await screen.findByText("Sign in screen");
  expect(UserService.confirm).toHaveBeenCalledWith("012345", "person@example.com");
  expect(sessionStorage.getItem("investment-tracker.pending-activation-email")).toBeNull();
});

test("restores the pending email after reopening and displays the server limit message", async () => {
  rememberActivationEmail("person@example.com");
  UserService.confirm.mockRejectedValue({ error: "Too many requests. Please wait and try again." });
  openActivation();
  fireEvent.change(screen.getByLabelText("Activation Code"), { target: { value: "123456" } });
  fireEvent.click(screen.getByRole("button", { name: "Activate Account" }));
  await screen.findByText("Too many requests. Please wait and try again.");
  expect(screen.getByRole("button", { name: "Activate Account" })).toBeEnabled();
});

test("requesting a new code uses the email without claiming that delivery succeeded", async () => {
  UserService.resendActivation.mockResolvedValue(undefined);
  openActivation({ email: "person@example.com" });
  fireEvent.click(screen.getByRole("button", { name: "Request New Code" }));
  await waitFor(() => expect(UserService.resendActivation).toHaveBeenCalledWith("person@example.com"));
  await screen.findByText(/If this account needs activation, a new code has been requested/);
});

test("repeated submission cannot launch a second pending request", async () => {
  let resolve;
  UserService.confirm.mockReturnValue(new Promise((done) => { resolve = done; }));
  openActivation({ email: "person@example.com" });
  fireEvent.change(screen.getByLabelText("Activation Code"), { target: { value: "123456" } });
  const button = screen.getByRole("button", { name: "Activate Account" });
  fireEvent.click(button);
  fireEvent.click(button);
  expect(UserService.confirm).toHaveBeenCalledTimes(1);
  resolve(undefined);
  await screen.findByText("Sign in screen");
});
