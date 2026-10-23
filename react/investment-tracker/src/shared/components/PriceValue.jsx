import { useId } from "react";
import { OverlayTrigger, Tooltip } from "react-bootstrap";
import { formatCurrency } from "../utils/formatCurrency";

function priceDescription(price, metadata) {
  if (price == null || metadata?.status === "UNAVAILABLE") return "Price unavailable.";
  if (metadata?.storedFallback || metadata?.status === "STORED_FALLBACK") {
    return "Price not updated. Showing a previously stored price.";
  }
  if (metadata?.sourceStale || metadata?.fxStale || metadata?.status === "STALE") {
    return "Price may be outdated. Showing the last available price.";
  }
  if (metadata?.status !== "FRESH") return "Price update status unknown.";
  return "24-hour average price.";
}

export default function PriceValue({ item }) {
  const id = useId();
  const description = priceDescription(item.price, item.priceFreshness);
  const warn = item.price != null && item.priceFreshness && !["FRESH", "UNAVAILABLE"].includes(item.priceFreshness.status);
  return <OverlayTrigger placement="top" trigger={["hover", "focus"]} overlay={<Tooltip className="price-metadata-tooltip" id={`price-info-${id}`}>{description}</Tooltip>}>
    <span tabIndex={0} aria-label={`${formatCurrency(item.price)}. ${description}`}>
      {formatCurrency(item.price)}{warn && <small className="text-warning ms-1" aria-hidden="true">!</small>}
    </span>
  </OverlayTrigger>;
}
