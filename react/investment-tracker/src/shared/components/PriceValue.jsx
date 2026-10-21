import { useId } from "react";
import { OverlayTrigger, Tooltip } from "react-bootstrap";
import { formatCurrency } from "../utils/formatCurrency";
import { formatPriceDateTime } from "../utils/priceHistory";

function priceDescription(metadata) {
  const lines = ["CSGOTrader 24-hour average, converted to EUR; not a live Steam sale price."];
  if (!metadata) return lines[0];
  const time = value => value ? `${formatPriceDateTime(value)} UTC` : "unknown";
  lines.push(`Source publication: ${time(metadata.sourcePublishedAt)}.`, `First observed/publication: ${time(metadata.observedAt)}.`,
    `Last successful source check: ${time(metadata.lastSuccessfulCheck)}.`, `USD/EUR rate date: ${time(metadata.fxRateAsOf)}.`);
  if (metadata.storedFallback) lines.push("Showing a previously stored price; it has not been confirmed against the current cached publication.");
  if (metadata.sourceStale) lines.push("The source publication is older than the freshness threshold.");
  if (metadata.fxStale) lines.push("The conversion rate is older than the freshness threshold.");
  if (metadata.status === "UNKNOWN") lines.push("Timestamp details are incomplete.");
  if (metadata.status === "UNAVAILABLE") lines.push("No price is available yet.");
  return lines.join(" ");
}

export default function PriceValue({ item }) {
  const id = useId();
  const description = priceDescription(item.priceFreshness);
  const warn = item.price != null && item.priceFreshness && !["FRESH", "UNAVAILABLE"].includes(item.priceFreshness.status);
  return <OverlayTrigger placement="top" trigger={["hover", "focus"]} overlay={<Tooltip className="price-metadata-tooltip" id={`price-info-${id}`}>{description}</Tooltip>}>
    <span tabIndex={0} aria-label={`${formatCurrency(item.price)}. ${description}`}>
      {formatCurrency(item.price)}{warn && <small className="text-warning ms-1" aria-hidden="true">!</small>}
    </span>
  </OverlayTrigger>;
}
