import { expect, test, vi } from "vitest";
import { buildPriceChartModel } from "./priceChartModel";
import { getPeriodChange } from "./priceHistory";
const points = [{ label: "09/10/2026", dateExtracted: "2026-10-09T08:00:00Z", price: 10 }, { label: "10/10/2026", dateExtracted: "2026-10-10T08:00:00Z", price: 12 }];

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
  const model = buildPriceChartModel([{ label: "Today", dateExtracted: "2026-10-10T08:00:00Z", price: 0 }]);
  expect(model.data.datasets[0].pointRadius).toBe(3);
  expect(model.options.scales.y.suggestedMin).toBe(0);
  expect(model.options.scales.y.suggestedMax).toBe(0.5);
  const timestamp = Date.parse("2026-10-10T08:00:00Z");
  expect(model.options.scales.x.min).toBe(timestamp - 43200000);
  expect(model.options.scales.x.max).toBe(timestamp + 43200000);
  expect(getPeriodChange([{ price: 0 }])).toEqual({ pct: null, direction: "neutral" });
});

test.each([false, true])("chart detailed=%s fits the first and last observations instead of rounded tick dates", detailed => {
  const observations = ["2026-09-25T13:27:00Z", "2026-10-01T08:16:00Z", "2026-10-20T17:43:00Z"]
    .map((dateExtracted, index) => ({ dateExtracted, price: 6.46 + index * 0.05 }));
  const original = JSON.stringify(observations);
  const { data, options } = buildPriceChartModel(observations, { detailed });
  expect(options.scales.x).toMatchObject({
    type: "linear", bounds: "data", offset: false,
    min: Date.parse(observations[0].dateExtracted),
    max: Date.parse(observations.at(-1).dateExtracted),
  });
  const { min, max } = options.scales.x;
  const relativeX = data.datasets[0].data.map(point => (point.x - min) / (max - min));
  expect(relativeX[0]).toBe(0);
  expect(relativeX.at(-1)).toBe(1);
  expect(relativeX[1]).toBeCloseTo((Date.parse(observations[1].dateExtracted) - min) / (max - min));
  expect(JSON.stringify(observations)).toBe(original);
});

test("two observations fill the horizontal plot without adding a fake flat segment", () => {
  const { data, options } = buildPriceChartModel(points);
  expect(data.datasets[0].data).toHaveLength(2);
  expect(options.scales.x.min).toBe(data.datasets[0].data[0].x);
  expect(options.scales.x.max).toBe(data.datasets[0].data[1].x);
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

test("ten-day and one-day intervals have a 10:1 numeric distance on both charts", () => {
  const observations = ["2026-09-16T00:00:00Z", "2026-09-26T00:00:00Z", "2026-09-27T00:00:00Z"]
    .map((dateExtracted, index) => ({ dateExtracted, price: 10 + index }));
  for (const detailed of [true, false]) {
    const model = buildPriceChartModel(observations, { detailed });
    const data = model.data.datasets[0].data;
    expect(model.options.scales.x.type).toBe("linear");
    expect((data[1].x - data[0].x) / (data[2].x - data[1].x)).toBe(10);
    expect(model.data.datasets[0].spanGaps).toBe(true);
    expect(data.map(point => point.y)).toEqual([10, 11, 12]);
  }
});
