import { expect, test, vi } from "vitest";
import { buildPriceChartModel } from "./priceChartModel";
import { getPeriodChange } from "./priceHistory";
const points = [{ label: "09/10/2026", price: 10 }, { label: "10/10/2026", price: 12 }];

test("compact and detailed charts share the exact chronological price values", () => {
  const original = JSON.stringify(points);
  const compact = buildPriceChartModel(points);
  const detailed = buildPriceChartModel(points, { detailed: true });
  expect(compact.data.labels).toEqual(points.map(p => p.label));
  expect(detailed.data.datasets[0].data).toEqual(compact.data.datasets[0].data);
  expect(JSON.stringify(points)).toBe(original);
  expect(compact.options.scales.x.display).toBe(false);
  expect(detailed.options.scales.x.ticks.font.size).toBe(11);
  expect(compact.data.datasets[0].borderWidth).toBe(2);
  expect(detailed.data.datasets[0].borderWidth).toBe(2.5);
});

test("one observation and a constant zero price retain a visible point and nonzero bounds", () => {
  const model = buildPriceChartModel([{ label: "Today", price: 0 }]);
  expect(model.data.datasets[0].pointRadius).toBe(3);
  expect(model.options.scales.y.suggestedMin).toBe(0);
  expect(model.options.scales.y.suggestedMax).toBe(0.5);
  expect(getPeriodChange([{ price: 0 }])).toEqual({ pct: null, direction: "neutral" });
});

test("neutral/down trends and row colours remain unchanged", () => {
  expect(getPeriodChange([{ price: 10 }, { price: 10 }])).toEqual({ pct: 0, direction: "neutral" });
  expect(getPeriodChange([{ price: 10 }, { price: 9 }])).toEqual({ pct: -10, direction: "negative" });
  expect(getPeriodChange([{ price: 0 }, { price: 9 }]).pct).toBeNull();
  expect(buildPriceChartModel(points, { strokeColor: "#6b7280" }).data.datasets[0].borderColor).toBe("#6b7280");
});

test("both chart modes delegate to the same external tooltip and never overlay a native tooltip", () => {
  const tooltip = vi.fn();
  for (const detailed of [false, true]) {
    const model = buildPriceChartModel(points, { detailed, tooltip });
    expect(model.options.plugins.tooltip.enabled).toBe(false);
    expect(model.options.plugins.tooltip.external).toBe(tooltip);
  }
});
