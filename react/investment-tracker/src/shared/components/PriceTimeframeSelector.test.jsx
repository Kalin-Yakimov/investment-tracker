import { expect, test, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import PriceTimeframeSelector from "./PriceTimeframeSelector";

test("both price views share the same periods, active state and selection callback", () => {
  const onSelect = vi.fn();
  const { rerender } = render(<PriceTimeframeSelector selectedTimeframe="7D" onSelect={onSelect} />);
  for (const timeframe of ["7D", "1M", "3M"]) {
    fireEvent.click(screen.getByRole("button", { name: timeframe }));
    expect(onSelect).toHaveBeenLastCalledWith(timeframe);
    rerender(<PriceTimeframeSelector selectedTimeframe={timeframe} onSelect={onSelect} />);
    expect(screen.getByRole("button", { name: timeframe })).toHaveClass("active");
    expect(screen.getAllByRole("button").filter(button => button.classList.contains("active"))).toHaveLength(1);
  }
});
