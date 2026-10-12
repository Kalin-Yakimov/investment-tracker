import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, useLocation } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";
import AuthService from "./features/auth/AuthService";
import deferred from "./testUtils/deferred";

// Feature views have their own tests. Keep real App, Navbar, guards and auth
// subscriptions here to verify routing/access without requesting backend data.
vi.mock("./features/auth/pages/LoginPage", () => ({ default: () => <h1>Login test view</h1> }));
vi.mock("./features/auth/pages/RegistrationPage", () => ({ default: () => <h1>Registration test view</h1> }));
vi.mock("./features/portfolio/PortfolioOverviewPage", () => ({ default: () => <h1>Portfolio test view</h1> }));
vi.mock("./features/portfolio/components/RegisterPortfolioItem", () => ({ default: () => null }));
vi.mock("./features/portfolio/PortfolioItemDetailsPage", () => ({ default: () => <h1>Details test view</h1> }));
vi.mock("./features/portfolio/EditPortfolioItemPage", () => ({ default: () => <h1>Edit test view</h1> }));
vi.mock("./features/price-tracker/PriceTrackerPage", () => ({ default: () => <h1>Tracker test view</h1> }));
vi.mock("./features/inventory/InventoryCalculatorPage", () => ({ default: () => <h1>Inventory test view</h1> }));
vi.mock("./features/support/SupportPanelPage", () => ({ default: () => <h1>Support test view</h1> }));

function LocationProbe() {
  const location = useLocation();
  return <div data-testid="location">{location.pathname}</div>;
}

function mount(path) {
  return render(<MemoryRouter initialEntries={[path]}><App /><LocationProbe /></MemoryRouter>);
}

function signedIn(authorities = ["USER"]) {
  // UI-only fixture, not a real signed token. Real JWT validation is covered by
  // the backend HTTP workflow tests; the browser never grants API permission.
  const payload = btoa(JSON.stringify({ sub: "101", email: "workflow@example.com", fullName: "Workflow User", authorities }));
  AuthService.saveToken(`fixture.${payload}.fixture`);
}

beforeEach(() => {
  AuthService.removeToken();
  vi.spyOn(AuthService, "initializeSession").mockResolvedValue(false);
});

afterEach(() => {
  cleanup();
  AuthService.removeToken();
});

describe("application route and profile access", () => {
  it.each(["/", "/tracker", "/inventory-calculator", "/viewItem/42", "/editItem/42", "/admin"])(
    "redirects an anonymous visit to %s to sign in", async (path) => {
      mount(path);
      expect(await screen.findByRole("heading", { name: "Login test view" })).toBeInTheDocument();
      expect(screen.getByTestId("location")).toHaveTextContent("/login");
      expect(screen.queryByRole("link", { name: "Steam Price Tracker" })).not.toBeInTheDocument();
    },
  );

  it("keeps registration available without a session", async () => {
    mount("/registration");
    expect(await screen.findByRole("heading", { name: "Registration test view" })).toBeInTheDocument();
  });

  it("waits for session restoration before evaluating a private route", async () => {
    const pending = deferred();
    AuthService.initializeSession.mockReturnValue(pending.promise);
    mount("/tracker");
    expect(screen.getByRole("status")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Login test view" })).not.toBeInTheDocument();
    await act(async () => { signedIn(); pending.resolve(true); });
    expect(await screen.findByRole("heading", { name: "Tracker test view" })).toBeInTheDocument();
    expect(screen.getByTestId("location")).toHaveTextContent("/tracker");
  });

  it.each(["USER", "SUPPORT", "ADMIN"])("enforces Support Panel access for %s in the route and profile menu", async (role) => {
    signedIn([role]);
    mount("/admin");
    expect(await screen.findByRole("heading", { name: role === "USER" ? "Portfolio test view" : "Support test view" }))
      .toBeInTheDocument();
    expect(screen.getByTestId("location")).toHaveTextContent(role === "USER" ? "/" : "/admin");
    fireEvent.click(screen.getByRole("button", { name: /workflow user/i }));
    if (role === "USER") {
      expect(screen.queryByRole("button", { name: "Support Panel" })).not.toBeInTheDocument();
    } else {
      expect(screen.getByRole("button", { name: "Support Panel" })).toBeInTheDocument();
    }
  });

  it("re-evaluates Support Panel access when the same account loses its role", async () => {
    signedIn(["ADMIN"]);
    mount("/admin");
    expect(await screen.findByRole("heading", { name: "Support test view" })).toBeInTheDocument();
    act(() => signedIn(["USER"]));
    expect(await screen.findByRole("heading", { name: "Portfolio test view" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Support test view" })).not.toBeInTheDocument();
  });

  it("leaves a private screen immediately when the session is cleared", async () => {
    signedIn();
    mount("/tracker");
    expect(await screen.findByRole("heading", { name: "Tracker test view" })).toBeInTheDocument();
    act(() => AuthService.removeToken());
    expect(await screen.findByRole("heading", { name: "Login test view" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Tracker test view" })).not.toBeInTheDocument();
  });

  it("signs out from the real profile menu and navigates to sign in", async () => {
    signedIn();
    vi.spyOn(AuthService, "logout").mockImplementation(async () => AuthService.removeToken());
    mount("/inventory-calculator");
    expect(await screen.findByRole("heading", { name: "Inventory test view" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /workflow user/i }));
    fireEvent.click(screen.getByRole("button", { name: "Sign Out" }));
    expect(await screen.findByRole("heading", { name: "Login test view" })).toBeInTheDocument();
    expect(AuthService.logout).toHaveBeenCalledOnce();
    expect(screen.queryByRole("link", { name: "Inventory Calculator" })).not.toBeInTheDocument();
  });
});
