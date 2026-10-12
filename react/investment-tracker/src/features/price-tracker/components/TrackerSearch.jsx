
export default function TrackerSearch({ query, setQuery, setSelectedSearchItem, searchResults, handleSelectSearchItem }) {
  return (
      <div className="card tracker-card-dark mb-4 position-relative" style={{ overflow: "visible", zIndex: 100 }}>
        <div className="card-body p-4" style={{ overflow: "visible" }}>
          <h5 className="card-title text-white mb-3 fw-bold">Add CS Item to Tracker</h5>
          <div className="position-relative w-100">
            <input
              type="text"
              className="form-control tracker-search"
              placeholder="Type CS2 skin or item name to track (e.g. AK-47 | Redline, Case)..."
              value={query}
              onChange={(e) => {
                setQuery(e.target.value);
                setSelectedSearchItem(null);
              }}
              autoComplete="off"
            />
            {searchResults.length > 0 && (
              <div className="tracker-autocomplete-dropdown">
                {searchResults.map((item) => (
                  <div
                    key={item.id}
                    className="tracker-autocomplete-item d-flex align-items-center gap-3 p-2"
                    onClick={() => handleSelectSearchItem(item)}
                  >
                    {item.image && (
                      <img
                        src={item.image}
                        alt={item.name}
                        className="tracker-autocomplete-img"
                        style={{ width: "40px", height: "30px", objectFit: "contain" }}
                      />
                    )}
                    <div>
                      <div className="fw-semibold text-white">{item.name}</div>
                      <div className="small text-muted">{item.marketHashName}</div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
  );
}
