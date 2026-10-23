import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import Navbar from "./Navbar";
import AuthService from "../features/auth/AuthService";

beforeEach(() => {
  AuthService.authListeners.clear();
  AuthService.accessToken = null;
  const payload = JSON.stringify({ sub: "42", fullName: "Калин 😀", email: "test@example.invalid", authorities: ["ADMIN"] });
  const encoded = btoa(String.fromCharCode(...new TextEncoder().encode(payload)))
    .replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  AuthService.saveToken(`header.${encoded}.signature`);
});
afterEach(() => vi.restoreAllMocks());

test("the real profile menu renders Unicode names and the decoded support permission", () => {
  render(<MemoryRouter><Navbar /></MemoryRouter>);
  fireEvent.click(screen.getByRole("button", { name: /Калин 😀/ }));
  expect(screen.getByRole("heading", { name: "Калин 😀" })).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Support Panel" })).toBeInTheDocument();
});

test("logout failures never print the credential-bearing error object", async () => {
  const logger = vi.spyOn(console, "error").mockImplementation(() => {});
  vi.spyOn(AuthService, "logout").mockImplementation(async () => {
    AuthService.removeToken();
    throw new Error("synthetic-private-request-content");
  });
  render(<MemoryRouter><Navbar /></MemoryRouter>);
  fireEvent.click(screen.getByRole("button", { name: /Калин 😀/ }));
  fireEvent.click(screen.getByRole("button", { name: "Sign Out" }));
  await waitFor(() => expect(logger).toHaveBeenCalledWith("Sign out failed; server-side revocation could not be confirmed."));
  expect(JSON.stringify(logger.mock.calls)).not.toContain("synthetic-private-request-content");
  expect(screen.getByRole("link", { name: "Sign In" })).toBeInTheDocument();
});
