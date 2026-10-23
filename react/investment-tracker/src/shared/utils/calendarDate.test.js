import { expect, test } from "vitest";
import { formatCalendarDate, parseCalendarDate } from "./calendarDate";

test.each([["2026-10-11", "11/10/2026"], ["11.10.2026", "11/10/2026"], ["29/02/2024", "29/02/2024"]])("calendar date %s has an unambiguous day/month", (input, expected) => {
  expect(formatCalendarDate(input)).toBe(expected);
});
test.each(["29.02.2025", "31.04.2026", "2026-13-01", "nonsense", "2026-10-11T00:00:00Z", null])("rejects invalid calendar date %s without normalizing it", input => {
  expect(parseCalendarDate(input)).toBeNull(); expect(formatCalendarDate(input)).toBe("-");
});
test("a date-picker local midnight is serialized without a UTC day shift", () => {
  const localDate = new Date(2026, 0, 1);
  expect(formatCalendarDate(localDate, ".")).toBe("01.01.2026");
  expect(parseCalendarDate("2026-01-01").getDate()).toBe(1);
});
