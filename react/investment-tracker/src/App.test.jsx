import { expect, test, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import RegistrationPage from "./features/auth/pages/RegistrationPage";

vi.mock("./features/auth/AuthService", () => ({
  default: {
    register: vi.fn(),
  },
}));

test("renders the registration form with its required fields", () => {
  render(
    <MemoryRouter>
      <RegistrationPage />
    </MemoryRouter>,
  );

  expect(
    screen.getByRole("heading", { name: /create account/i }),
  ).toBeInTheDocument();
  expect(screen.getByLabelText(/username/i)).toBeRequired();
  expect(screen.getByLabelText(/email address/i)).toBeRequired();
  expect(screen.getByLabelText(/^password$/i)).toBeRequired();
  expect(screen.getByLabelText(/confirm password/i)).toBeRequired();
  expect(
    screen.getByRole("button", { name: /register account/i }),
  ).toBeInTheDocument();
});
