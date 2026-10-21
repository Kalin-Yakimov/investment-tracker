import CatalogueCombobox from "../../catalogue/CatalogueCombobox";

export default function TrackerSearch({ query, setQuery, handleSelectSearchItem, scope, loading, inputRef }) {
  return (
      <div className="card tracker-card-dark mb-4 position-relative" style={{ overflow: "visible", zIndex: 100 }}>
        <div className="card-body p-4" style={{ overflow: "visible" }}>
          <h5 className="card-title text-white mb-3 fw-bold">Add CS Item to Tracker</h5>
          <div className="position-relative w-100">
            <CatalogueCombobox
              inputRef={inputRef}
              label="Search CS items to track"
              inputClassName="form-control tracker-search"
              placeholder="Type CS2 skin or item name to track (e.g. AK-47 | Redline, Case)..."
              value={query}
              onChange={setQuery}
              onSelect={handleSelectSearchItem}
              disabled={loading || scope.isPending("tracked-change")}
            />
          </div>
        </div>
      </div>
  );
}
