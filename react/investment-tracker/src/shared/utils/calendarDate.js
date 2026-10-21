// Portfolio dates are calendar dates, not UTC instants. Never parse them with Date.parse.
export function parseCalendarDate(value) {
  if (value instanceof Date) return Number.isFinite(value.getTime()) ? value : null;
  if (typeof value !== "string") return null;
  const iso = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  const dayFirst = /^(\d{2})[./](\d{2})[./](\d{4})$/.exec(value);
  if (!iso && !dayFirst) return null;
  const [year, month, day] = iso ? iso.slice(1).map(Number) : [dayFirst[3], dayFirst[2], dayFirst[1]].map(Number);
  const date = new Date(0);
  date.setFullYear(year, month - 1, day);
  date.setHours(0, 0, 0, 0);
  return date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day ? date : null;
}

export function formatCalendarDate(value, separator = "/") {
  const date = parseCalendarDate(value);
  if (!date) return "-";
  return [String(date.getDate()).padStart(2, "0"), String(date.getMonth() + 1).padStart(2, "0"), String(date.getFullYear()).padStart(4, "0")].join(separator);
}
