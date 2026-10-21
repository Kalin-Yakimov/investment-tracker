import useChartPointTooltip from "../hooks/useChartPointTooltip";
import { buildPriceChartModel } from "../utils/priceChartModel";
import { formatCurrency } from "../utils/formatCurrency";
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

export function PriceSparkline({ points, strokeColor, emptyClassName = "tracker-chart-empty", state, onRetry }) {
  const showPointTooltip = useChartPointTooltip(points, formatCurrency);

  if (state?.status === "error") return <div className="sparkline-chart-frame"><button type="button" className="btn btn-sm text-secondary" title={state.error} onClick={event => { event.stopPropagation(); onRetry?.(); }}>Retry chart</button></div>;
  if (state?.status === "loading" && !points?.length) return <div className="sparkline-chart-frame text-secondary small" role="status">Loading chart...</div>;

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
      <Line data={data} options={options} role="img" aria-label={`Recorded price chart, ${points.length} observations. Open item details for dates and prices.`} />
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
        <Line data={data} options={options} role="img" aria-label={`Recorded prices across ${points.length} observations; the same data is available in the history table below.`} />
      </div>
    </div>
  );
}
