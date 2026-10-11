import { act, renderHook } from "@testing-library/react";
import useChartPointTooltip from "./useChartPointTooltip";

const context = () => ({
  chart: {
    width: 200, height: 80,
    canvas: { getBoundingClientRect: () => ({ left: 50, top: 200, width: 200, height: 80 }) },
  },
  tooltip: {
    opacity: 1,
    dataPoints: [{ dataIndex: 0, element: { tooltipPosition: () => ({ x: 100, y: 0 }) } }],
  },
});

test("point tooltip is above the selected point outside the canvas, hides on scroll and is removed on unmount", () => {
  const { result, unmount } = renderHook(() => useChartPointTooltip([{ label: "10/10/2026", price: 5 }], p => `€${p.toFixed(2)}`));
  act(() => result.current(context()));
  const tooltip = document.querySelector('[role="tooltip"]');
  expect(tooltip.parentElement).toBe(document.body);
  expect(tooltip.textContent).toBe("10/10/2026€5.00");
  expect(tooltip.style.left).toBe("150px");
  expect(tooltip.style.top).toBe("190px");
  expect(tooltip.style.visibility).toBe("visible");
  act(() => window.dispatchEvent(new Event("scroll")));
  expect(tooltip.style.visibility).toBe("hidden");
  unmount();
  expect(document.querySelector('[role="tooltip"]')).toBeNull();
});

test("changing chart points hides the previous tooltip and a detailed tooltip retains its price label", () => {
  const { result, rerender, unmount } = renderHook(({ points }) => useChartPointTooltip(points, p => `€${p}`, true), {
    initialProps: { points: [{ label: "First day", price: 5 }] },
  });
  act(() => result.current(context()));
  const tooltip = document.querySelector('[role="tooltip"]');
  expect(tooltip.textContent).toBe("First dayPrice: €5");
  rerender({ points: [{ label: "Second day", price: 6 }] });
  expect(tooltip.style.visibility).toBe("hidden");
  act(() => result.current(context()));
  expect(tooltip.textContent).toBe("Second dayPrice: €6");
  unmount();
});
