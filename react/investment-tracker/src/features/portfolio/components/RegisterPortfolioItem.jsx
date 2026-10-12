import { useState } from "react";
import PortfolioApi from "../PortfolioApi";
import DatePicker from "react-datepicker";
import { format } from "date-fns";
import "react-datepicker/dist/react-datepicker.module.css";
import "../styles/RegisterPortfolioItem.css";
import useCatalogueSearch from "../../catalogue/useCatalogueSearch";
import useRequestScope from "../../../shared/hooks/useRequestScope";
import { isRequestCanceled } from "../../../shared/utils/RequestScope";

export default function RegisterPortfolioItem({ onItemAdded }) {
  const scope = useRequestScope();
  const [item, setItem] = useState({
    itemName: "",
    csItemId: null,
    itemStatus: "PICK",
    date: new Date(),
    quantity: "",
    price: "",
  });

  const [error, setError] = useState("");
  const { itemName, csItemId, itemStatus, date, quantity, price } = item;

  const csItems = useCatalogueSearch(itemName, !csItemId, 2);
  const showDropdown = !csItemId && csItems.length > 0;
  const submitting = scope.isPending("submit");

  const onInputChange = (e) => {
    const { name, value } = e.target;
    setItem((prevItem) => ({
      ...prevItem,
      [name]: value,
      ...(name === "itemName" ? { csItemId: null } : {}),
    }));
    setError("");
  };

  const selectCsItem = (selectedItem) => {
    setItem((prevItem) => ({
      ...prevItem,
      itemName: selectedItem.name,
      csItemId: selectedItem.id,
    }));
  };

  const handleDateChange = (date) => {
    setItem((prevItem) => ({
      ...prevItem,
      date: date,
    }));
    setError("");
  };

  const clearForm = () => {
    setItem({
      itemName: "",
      csItemId: null,
      itemStatus: "PICK",
      date: new Date(),
      quantity: "",
      price: "",
    });
    setError("");
  };

  const onSubmit = async (e) => {
    e.preventDefault();

    if (!item.csItemId) {
      setError("Please select a valid CS item from the search dropdown menu.");
      return;
    }

    if (itemStatus === "PICK" || !itemName || !date || !quantity || !price) {
      setError("Please fill out all fields.");
      return;
    }

    const request = scope.begin("submit", { replace: false, abortable: false });
    if (!request) return;
    try {
      const formattedDate = format(date, "dd.MM.yyyy");
      await PortfolioApi.createItem({ ...item, date: formattedDate });
      if (!request.isCurrent()) return;
      clearForm();
      if (onItemAdded) onItemAdded();
    } catch (error) {
      if (request.isCurrent() && !isRequestCanceled(error)) {
        console.error("There was an error submitting the form!", error);
        setError(error.response?.data?.error || "The item could not be registered. Please try again.");
      }
    } finally {
      request.finish();
    }
  };

  return (
    <div className="container mt-4 mb-4">
      <div className="card add-item-card shadow-lg p-4">
        <div className="d-flex align-items-center justify-content-between mb-3">
          <h3 className="add-item-title mb-0">Register Portfolio Item</h3>
          <span className="text-white-50 small">Record CS items you bought or sold to track your portfolio</span>
        </div>

        {error && <div className="alert alert-danger py-2">{error}</div>}

        <form onSubmit={onSubmit}>
          <fieldset className="row g-3 align-items-end" disabled={submitting}>
            {/* Item Name Input with Auto-complete Dropdown */}
            <div className="col-12 col-md-4 position-relative">
              <label htmlFor="itemName" className="form-label text-white small fw-semibold">
                Item Name
              </label>
              <input
                type="text"
                className="form-control"
                placeholder="Search CS skin name..."
                name="itemName"
                value={itemName}
                onChange={onInputChange}
                id="itemName"
                required
                autoComplete="off"
              />
              {showDropdown && csItems.length > 0 && (
                <ul className="list-group position-absolute w-100 dark-dropdown-menu shadow-lg">
                  {csItems.map((csItem) => (
                    <li
                      key={csItem.id}
                      className="list-group-item dark-dropdown-item d-flex align-items-center gap-2"
                      onClick={() => selectCsItem(csItem)}
                    >
                      {csItem.image ? (
                        <img
                          src={csItem.image}
                          alt={csItem.name}
                          className="dark-dropdown-thumb"
                        />
                      ) : (
                        <div className="dark-dropdown-thumb-placeholder">📷</div>
                      )}
                      <span className="text-truncate small">{csItem.name}</span>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            {/* Status Select */}
            <div className="col-6 col-md-2">
              <label htmlFor="itemStatus" className="form-label text-white small fw-semibold">
                Status
              </label>
              <select
                className="form-select"
                name="itemStatus"
                value={itemStatus}
                onChange={onInputChange}
                id="itemStatus"
                required
              >
                <option value="PICK">-- Select --</option>
                <option value="BOUGHT">Bought</option>
                <option value="SOLD">Sold</option>
              </select>
            </div>

            {/* Date Picker */}
            <div className="col-6 col-md-2">
              <label htmlFor="date" className="form-label text-white small fw-semibold">
                Date
              </label>
              <DatePicker
                selected={date}
                onChange={handleDateChange}
                dateFormat={"dd.MM.yyyy"}
                className="form-control"
                required
              />
            </div>

            {/* Quantity Input */}
            <div className="col-6 col-md-2">
              <label htmlFor="quantity" className="form-label text-white small fw-semibold">
                Quantity
              </label>
              <input
                type="number"
                className="form-control"
                placeholder="Qty"
                name="quantity"
                value={quantity}
                onChange={onInputChange}
                id="quantity"
                min="1"
                required
              />
            </div>

            {/* Price Input */}
            <div className="col-6 col-md-2">
              <label htmlFor="price" className="form-label text-white small fw-semibold">
                Price (€)
              </label>
              <input
                type="number"
                className="form-control"
                placeholder="Price"
                name="price"
                value={price}
                onChange={onInputChange}
                id="price"
                step="0.01"
                min="0"
                required
              />
            </div>

            {/* Form Actions */}
            <div className="col-12 d-flex justify-content-end gap-2 mt-3">
              <button type="button" className="btn btn-secondary px-4" onClick={clearForm}>
                Clear
              </button>
              <button type="submit" className="btn btn-primary px-4">
                Register Item
              </button>
            </div>
          </fieldset>
        </form>
      </div>
    </div>
  );
}
