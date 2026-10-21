import { useRef } from "react";
import PriceTimeframeSelector from "../../shared/components/PriceTimeframeSelector";
import usePriceTracker from "./hooks/usePriceTracker";
import TrackerSearch from "./components/TrackerSearch";
import TrackerDetailModal from "./components/TrackerDetailModal";
import TrackedItemsTable from "./components/TrackedItemsTable";
import TrackerDeleteModal from "./components/TrackerDeleteModal";
import RequestError from "../../shared/components/RequestError";
import PriceAttribution from "../../shared/components/PriceAttribution";
import "./styles/PriceTrackerPage.css";

export default function PriceTrackerPage() {
  const state = usePriceTracker();
  const searchInput = useRef(null);
  const { scope, error, selectedTimeframe, setSelectedTimeframe } = state;
  return (
    <div className="tracker-page-container container py-4 pb-5">
      {/* Page Header */}
      <div className="row mb-4 align-items-center">
        <div className="col-12">
          <h2 className="tracker-header-title text-white fw-bold mb-1">
            Steam Community Market Price Tracker
          </h2>
        </div>
      </div>

      {/* Notification Messages */}
      {error && (
        <div
          className="mb-4 p-3 rounded-3 shadow-lg text-white"
          role="alert"
          style={{
            background: "rgba(22, 27, 38, 0.95)",
            border: "1px solid rgba(255, 255, 255, 0.15)",
            color: "#ffffff",
            backdropFilter: "blur(12px)",
            fontSize: "0.95rem",
            fontWeight: "500",
          }}
        >
          {error}
        </div>
      )}

      {/* Search & Add Item Section */}
      <TrackerSearch {...state} inputRef={searchInput} />
      <RequestError message={state.loadError} onRetry={state.fetchTrackedItems} retryLabel="Retry tracked items" pending={state.loading} />


      {/* Global Timeframe Control Bar */}
      <div className="timeframe-bar-card p-3 mb-4 d-flex flex-wrap align-items-center justify-content-between gap-3">
        <div className="d-flex align-items-center gap-2">
          <span className="timeframe-bar-title">📊 Trend Period:</span>
        </div>
        <PriceTimeframeSelector selectedTimeframe={selectedTimeframe} onSelect={setSelectedTimeframe} />
      </div>

      {/* Focused Overlay Modal for Item Detail View */}
      <TrackerDetailModal {...state} />


      {/* Main Tracked Items Table */}
      <TrackedItemsTable {...state} pending={state.loading || scope.isPending("tracked-change")} />

      {/* Centered Custom Delete Confirmation Modal */}
      <TrackerDeleteModal {...state} pending={scope.isPending("tracked-change")}
        onRemoved={() => searchInput.current?.focus({ preventScroll: true })} />
      <PriceAttribution />
    </div>
  );
}
