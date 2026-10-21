import { formatPriceDate } from "../utils/priceHistory";

export default function PriceTrend({ trend = { pct: null, direction: "neutral" }, points = [] }) {
  const description = points.length < 2 ? "At least two recorded observations are needed to calculate a change."
    : `Change between the available observations from ${formatPriceDate(points[0].dateExtracted)} to ${formatPriceDate(points.at(-1).dateExtracted)} (UTC). This is not necessarily a full selected period.`;
  return <span className={`badge ${trend.direction === "positive" ? "bg-success" : trend.direction === "negative" ? "bg-danger" : "bg-secondary"}`} title={description} aria-label={`${trend.pct == null ? "Change unavailable" : `${trend.pct.toFixed(2)} percent`}. ${description}`}>
    {trend.pct == null ? "—" : `${trend.direction === "positive" ? "▲ +" : trend.direction === "negative" ? "▼ " : ""}${trend.pct.toFixed(2)}%`}
  </span>;
}
