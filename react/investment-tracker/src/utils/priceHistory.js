// Publication days are UTC everywhere: database, tracker and pinned-item charts.
export function formatPriceDate(value) {
  const date = value instanceof Date ? value : new Date(value);
  if (!value || !Number.isFinite(date.getTime())) return "-";
  const day = String(date.getUTCDate()).padStart(2, "0");
  const month = String(date.getUTCMonth() + 1).padStart(2, "0");
  return `${day}/${month}/${date.getUTCFullYear()}`;
}

export function formatPriceDateTime(value) {
  const date = new Date(value);
  if (!value || !Number.isFinite(date.getTime())) return "-";
  const hours = String(date.getUTCHours()).padStart(2, "0");
  const minutes = String(date.getUTCMinutes()).padStart(2, "0");
  return `${formatPriceDate(date)} ${hours}:${minutes}`;
}

export function sortPriceHistoryNewestFirst(history = []) {
  const time = (entry) => new Date(entry?.dateExtracted).getTime();
  const safeTime = (entry) => Number.isFinite(time(entry)) ? time(entry) : -Infinity;
  return [...(Array.isArray(history) ? history : [])]
    .sort((a, b) => safeTime(b) - safeTime(a) || (Number(b?.id) || 0) - (Number(a?.id) || 0));
}

export function buildDailyPricePoints(history = [], timeframe = "7D", now = Date.now()) {
  const days = timeframe === "3M" ? 90 : timeframe === "1M" ? 30 : 7;
  const cutoff = now - days * 24 * 60 * 60 * 1000;
  const dailyPoints = new Map();
  (Array.isArray(history) ? history : []).forEach((entry) => {
    if (entry?.price == null || !entry?.dateExtracted) return;
    const dateObj = new Date(entry.dateExtracted);
    const price = Number(entry.price);
    if (!Number.isFinite(dateObj.getTime()) || !Number.isFinite(price) || dateObj.getTime() < cutoff) return;
    const day = dateObj.toISOString().slice(0, 10);
    const previous = dailyPoints.get(day);
    if (!previous || dateObj.getTime() > previous.dateObj.getTime()) {
      dailyPoints.set(day, { dateObj, price });
    }
  });
  return [...dailyPoints.values()]
    .sort((a, b) => a.dateObj.getTime() - b.dateObj.getTime())
    .map((point) => ({
      ...point,
      dateExtracted: point.dateObj.toISOString(),
      label: formatPriceDate(point.dateObj),
    }));
}

export const getPeriodChange = (points) => {
  if (!points || points.length < 2) {
    return { pct: null, direction: "neutral" };
  }

  const latest = points[points.length - 1].price;
  const first = points[0].price;

  if (!first || first === 0) {
    return { pct: null, direction: "neutral" };
  }

  const pct = ((latest - first) / first) * 100;
  const direction = pct > 0 ? "positive" : pct < 0 ? "negative" : "neutral";
  return { pct, direction };
};
