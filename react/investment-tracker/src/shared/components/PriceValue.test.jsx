import { fireEvent, render, screen } from "@testing-library/react";
import { expect, test } from "vitest";
import PriceValue from "./PriceValue";
test("keyboard focus gives a short stored-price warning without technical metadata", async () => {
  render(<PriceValue item={{ price: 9.2, priceFreshness: {
    status: "STORED_FALLBACK", storedFallback: true, sourceStale: true, fxStale: false,
    sourcePublishedAt: "2026-10-10T08:00:00Z", observedAt: "2026-10-10T08:00:00Z", fxRateAsOf: null, lastSuccessfulCheck: null,
  } }} />);
  const price = screen.getByLabelText(/€9.20/);
  fireEvent.focus(price);
  const tooltip = await screen.findByRole("tooltip");
  expect(tooltip.textContent).toBe("Price not updated. Showing a previously stored price.");
  expect(price).toHaveAccessibleName("€9.20. Price not updated. Showing a previously stored price.");
  expect(price).toHaveTextContent("!");
});

test.each([
  [9.2, { status: "FRESH" }, "24-hour average price.", false],
  [0, { status: "FRESH" }, "24-hour average price.", false],
  [9.2, { status: "STALE", sourceStale: true }, "Price may be outdated. Showing the last available price.", true],
  [9.2, { status: "STALE", fxStale: true }, "Price may be outdated. Showing the last available price.", true],
  [9.2, { status: "UNKNOWN" }, "Price update status unknown.", true],
  [9.2, null, "Price update status unknown.", false],
  [null, { status: "UNAVAILABLE" }, "Price unavailable.", false],
])("price %s with freshness %j has a concise, accurate tooltip", async (price, priceFreshness, message, warning) => {
  render(<PriceValue item={{ price, priceFreshness }} />);
  const value = screen.getByLabelText(new RegExp(message.replaceAll(".", "\\.")));
  fireEvent.focus(value);
  expect((await screen.findByRole("tooltip")).textContent).toBe(message);
  expect(value.textContent.includes("!")).toBe(warning);
});
