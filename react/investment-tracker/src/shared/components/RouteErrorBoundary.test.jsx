import { expect, test, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import RouteErrorBoundary from "./RouteErrorBoundary";
test("a render failure offers recovery, and a different route key resets the boundary", () => {
  const log = vi.spyOn(console, "error").mockImplementation(() => {});
  const suppressExpectedError = event => event.preventDefault();
  window.addEventListener("error", suppressExpectedError);
  function Broken() { throw new Error("render failed"); }
  try {
    const { rerender } = render(<RouteErrorBoundary key="one"><Broken /></RouteErrorBoundary>);
    expect(screen.getByRole("alert")).toHaveTextContent("could not be displayed");
    expect(screen.getByRole("button", { name: "Reload page" })).toBeInTheDocument();
    rerender(<RouteErrorBoundary key="two"><div>Working page</div></RouteErrorBoundary>);
    expect(screen.getByText("Working page")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  } finally { log.mockRestore(); window.removeEventListener("error", suppressExpectedError); }
});
