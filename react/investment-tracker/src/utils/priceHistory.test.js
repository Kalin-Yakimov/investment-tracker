import { buildDailyPricePoints, formatPriceDate, formatPriceDateTime, sortPriceHistoryNewestFirst } from "./priceHistory";

const entry = (id, dateExtracted, price = 5) => ({ id, dateExtracted, price });
const now = Date.parse("2026-10-12T12:00:00Z");

test("history is newest first without mutating the API response", () => {
  const history = [entry(1, "2026-10-09T10:25:00Z"), entry(2, "2026-10-10T13:25:01Z")];
  expect(sortPriceHistoryNewestFirst(history).map((point) => point.id)).toEqual([2, 1]);
  expect(history.map((point) => point.id)).toEqual([1, 2]);
});

test("charts keep the newest value per UTC day and remain chronological", () => {
  const history = [
    entry(3, "2026-10-11T10:25:00Z", 7),
    entry(1, "2026-10-10T10:25:00Z", 5),
    entry(2, "2026-10-10T13:25:01Z", 6),
  ];
  const points = buildDailyPricePoints(history, "7D", now);
  expect(points.map((point) => point.price)).toEqual([6, 7]);
  expect(points.map((point) => point.label)).toEqual(["10/10/2026", "11/10/2026"]);
});

test("unchanged prices on different publication days remain two real points", () => {
  const points = buildDailyPricePoints([
    entry(1, "2026-10-10T23:59:59Z"),
    entry(2, "2026-10-11T00:00:00Z"),
  ], "7D", now);
  expect(points.map((point) => point.price)).toEqual([5, 5]);
  expect(points.map((point) => point.label)).toEqual(["10/10/2026", "11/10/2026"]);
});

test("missing publication days are not invented and old or invalid data is excluded", () => {
  const points = buildDailyPricePoints([
    entry(1, "2026-10-02T10:25:00Z"),
    entry(2, "2026-10-09T10:25:00Z"),
    entry(3, "2026-10-11T10:25:00Z"),
    entry(4, "invalid"), entry(5, "2026-10-10T10:25:00Z", "invalid"),
  ], "7D", now);
  expect(points.map((point) => point.label)).toEqual(["09/10/2026", "11/10/2026"]);
});

test("publication dates and hours are formatted explicitly in UTC", () => {
  expect(formatPriceDate("2026-10-10T23:59:59Z")).toBe("10/10/2026");
  expect(formatPriceDateTime("2026-10-10T23:59:59Z")).toBe("10/10/2026 23:59");
  expect(formatPriceDate(null)).toBe("-");
});
