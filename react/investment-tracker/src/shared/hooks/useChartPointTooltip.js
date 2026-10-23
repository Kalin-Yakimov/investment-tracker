import { useEffect, useRef } from "react";

export default function useChartPointTooltip(points, formatPrice, detailed = false) {
  const tooltipRef = useRef(null);

  useEffect(() => () => tooltipRef.current?.cleanup(), []);

  useEffect(() => {
    if (tooltipRef.current) tooltipRef.current.element.style.visibility = "hidden";
  }, [points]);

  return ({ chart, tooltip }) => {
    const dataPoint = tooltip.dataPoints?.[0];
    const point = dataPoint && points?.[dataPoint.dataIndex];

    if (!point || tooltip.opacity === 0) {
      if (tooltipRef.current) tooltipRef.current.element.style.visibility = "hidden";
      return;
    }

    if (!tooltipRef.current) {
      const element = document.createElement("div");
      const date = document.createElement("span");
      const price = document.createElement("strong");
      element.className = `tracker-point-tooltip${detailed ? " tracker-point-tooltip-detail" : ""}`;
      element.setAttribute("role", "tooltip");
      element.append(date, price);
      document.body.appendChild(element);

      const hide = () => { element.style.visibility = "hidden"; };
      window.addEventListener("scroll", hide, true);
      window.addEventListener("resize", hide);
      tooltipRef.current = {
        element,
        date,
        price,
        cleanup: () => {
          window.removeEventListener("scroll", hide, true);
          window.removeEventListener("resize", hide);
          element.remove();
        },
      };
    }

    const { element, date, price } = tooltipRef.current;
    date.textContent = point.label;
    price.textContent = detailed ? `Price: ${formatPrice(point.price)}` : formatPrice(point.price);

    const canvasRect = chart.canvas.getBoundingClientRect();
    const position = dataPoint.element.tooltipPosition();
    const pointX = canvasRect.left + position.x * canvasRect.width / chart.width;
    const pointY = canvasRect.top + position.y * canvasRect.height / chart.height;
    const halfWidth = element.offsetWidth / 2;
    const centerX = Math.max(halfWidth + 8, Math.min(pointX, window.innerWidth - halfWidth - 8));
    const arrowOffset = Math.max(12 - halfWidth, Math.min(pointX - centerX, halfWidth - 12));

    element.style.left = `${centerX}px`;
    element.style.top = `${pointY - 10}px`;
    element.style.setProperty("--tooltip-arrow-offset", `${arrowOffset}px`);
    element.style.visibility = "visible";
  };
}
