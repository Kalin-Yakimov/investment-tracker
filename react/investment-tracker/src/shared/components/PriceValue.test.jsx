import { fireEvent, render, screen } from "@testing-library/react";
import { expect, test } from "vitest";
import PriceValue from "./PriceValue";
test("keyboard focus explains stale stored prices without inventing missing FX dates", async () => {
  render(<PriceValue item={{ price: 9.2, priceFreshness: {
    status: "STORED_FALLBACK", storedFallback: true, sourceStale: true, fxStale: false,
    sourcePublishedAt: "2026-10-10T08:00:00Z", observedAt: "2026-10-10T08:00:00Z", fxRateAsOf: null, lastSuccessfulCheck: null,
  } }} />);
  const price = screen.getByLabelText(/€9.20/);
  fireEvent.focus(price);
  const tooltip = await screen.findByRole("tooltip");
  expect(tooltip).toHaveTextContent("USD/EUR rate date: unknown");
  expect(tooltip).toHaveTextContent("Showing a previously stored price");
  expect(tooltip).toHaveTextContent("not a live Steam sale price");
  expect(price).toHaveTextContent("!");
});
