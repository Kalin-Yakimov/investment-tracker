
export default function PriceTimeframeSelector({ selectedTimeframe, onSelect }) {
  return (
    <div className="btn-group timeframe-toggle-group" role="group">
      {["7D", "1M", "3M"].map((timeframe) => (
        <button
          key={timeframe}
          type="button"
          className={`btn btn-sm timeframe-toggle-btn ${selectedTimeframe === timeframe ? "active" : ""}`}
          onClick={() => onSelect(timeframe)}
        >
          {timeframe}
        </button>
      ))}
    </div>
  );
}
