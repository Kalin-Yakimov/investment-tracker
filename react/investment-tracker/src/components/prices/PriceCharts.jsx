import React from "react";
import useChartPointTooltip from "../../hooks/useChartPointTooltip";
import { buildPriceChartModel } from "../../utils/priceChartModel";
import { formatCurrency } from "../../utils/formatCurrency";
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler,
} from "chart.js";
import { Line } from "react-chartjs-2";

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler
);

export function PriceSparkline({ points, strokeColor, emptyClassName = "tracker-chart-empty" }) {
  const showPointTooltip = useChartPointTooltip(points, formatCurrency);

  if (!points || points.length === 0) {
    return (
      <div
        className={`sparkline-chart-frame ${emptyClassName} sparkline-empty-label`}
      >
        No data
      </div>
    );
  }

  const { data, options } = buildPriceChartModel(points, { strokeColor, tooltip: showPointTooltip });

  return (
    <div className="sparkline-chart-frame">
      <Line data={data} options={options} />
    </div>
  );
}

export function DetailedPriceChart({ points }) {
  const showPointTooltip = useChartPointTooltip(points, formatCurrency, true);

  if (!points || points.length === 0) {
    return <div className="tracker-chart-empty text-center py-5">No price history yet</div>;
  }

  const { data, options } = buildPriceChartModel(points, { detailed: true, tooltip: showPointTooltip });

  return (
    <div className="detailed-chart-wrapper mb-4">
      <div className="detailed-chart-container p-2" style={{ height: "220px" }}>
        <Line data={data} options={options} />
      </div>
    </div>
  );
}
