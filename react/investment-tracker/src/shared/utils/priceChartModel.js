// Shared presentation only. Publication filtering and UTC grouping live in priceHistory.
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
        data: prices,
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
        mode: "index",
        intersect: false,
        external: tooltip,
      },
    },
    scales: {
      x: {
        grid: { color: "rgba(255, 255, 255, 0.05)" },
        ticks: { color: "#64748b", font: { size: 11 } },
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
    options.scales.x = { display: false };
    options.scales.y.display = false;
    options.interaction = { mode: "nearest", axis: "x", intersect: false };
  }
  return { data, options };
}
