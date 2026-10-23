import { formatPriceDate } from "./priceHistory";
// A numeric UTC timestamp axis needs no date-adapter dependency.
export function buildPriceChartModel(points, { detailed = false, strokeColor, tooltip } = {}) {
  const prices = points.map((p) => p.price);
  const minPrice = Math.min(...prices);
  const maxPrice = Math.max(...prices);
  const priceRange = maxPrice - minPrice;
  const padding = priceRange > 0 ? priceRange * 0.15 : (minPrice > 0 ? minPrice * 0.05 : 0.5);

  const isPositive = prices[prices.length - 1] >= prices[0];
  const mainColor = strokeColor || (isPositive ? "#10b981" : "#ef4444");

  const data = {
    labels: points.map((p) => p.label),
    datasets: [
      {
        data: points.map((point) => ({ x: point.timestamp ?? new Date(point.dateExtracted ?? point.dateObj).getTime(), y: point.price })),
        spanGaps: true,
        borderColor: mainColor,
        borderWidth: detailed ? 2.5 : 2,
        tension: 0.35,
        pointRadius: detailed ? (points.length <= 10 ? 3 : 0) : (points.length === 1 ? 3 : 0),
        pointHoverRadius: detailed ? 6 : 5,
        pointHoverBackgroundColor: "#ffffff",
        pointHoverBorderColor: mainColor,
        pointHoverBorderWidth: detailed ? 3 : 2,
        fill: true,
        backgroundColor: (context) => {
          const chart = context.chart;
          const { ctx, chartArea } = chart;
          if (!chartArea) return null;
          const gradient = ctx.createLinearGradient(0, chartArea.top, 0, chartArea.bottom);
          gradient.addColorStop(0, mainColor + (detailed ? "44" : "33"));
          gradient.addColorStop(1, mainColor + "00");
          return gradient;
        },
      },
    ],
  };

  const options = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      tooltip: {
        enabled: false,
        mode: "nearest",
        axis: "x",
        intersect: false,
        external: tooltip,
      },
    },
    scales: {
      x: {
        type: "linear",
        bounds: "data",
        offset: false,
        grid: { color: "rgba(255, 255, 255, 0.05)" },
        ticks: { color: "#64748b", font: { size: 11 }, maxTicksLimit: 7, callback: formatPriceDate },
      },
      y: {
        grid: { color: "rgba(255, 255, 255, 0.05)" },
        ticks: {
          color: "#64748b",
          font: { size: 11 },
          callback: (val) => `€${val.toFixed(2)}`,
        },
        suggestedMin: Math.max(0, minPrice - padding),
        suggestedMax: maxPrice + padding,
      },
    },
  };

  if (!detailed) {
    options.scales.x.display = false;
    options.scales.y.display = false;
  }
  options.interaction = { mode: "nearest", axis: "x", intersect: false };
  if (points.length === 1) {
    const timestamp = data.datasets[0].data[0].x;
    options.scales.x.min = timestamp - 43200000;
    options.scales.x.max = timestamp + 43200000;
  } else if (points.length > 1) {
    // Tick rounding must not add empty dates before/after the recorded history.
    // Keep elapsed-time spacing, but use the full plot width for the actual points.
    options.scales.x.min = data.datasets[0].data[0].x;
    options.scales.x.max = data.datasets[0].data.at(-1).x;
  }
  return { data, options };
}
