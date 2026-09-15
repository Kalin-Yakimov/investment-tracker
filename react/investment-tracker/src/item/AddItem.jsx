import axios from "axios";
import React, { useState, useEffect } from "react";
import UserService from "../service/UserService";
import DatePicker from "react-datepicker";
import { format } from "date-fns";
import "react-datepicker/dist/react-datepicker.module.css";
import "../styles/AddItem.css";

export default function AddItem({ onItemAdded }) {
  const [item, setItem] = useState({
    itemName: "",
    csItemId: null,
    itemStatus: "PICK",
    date: new Date(),
    quantity: "",
    price: "",
  });

  const [csItems, setCsItems] = useState([]);
  const [showDropdown, setShowDropdown] = useState(false);
  const [error, setError] = useState("");
  const { itemName, csItemId, itemStatus, date, quantity, price } = item;

  useEffect(() => {
    // If an item is already selected, don't trigger auto-complete search again
    if (csItemId) {
      setShowDropdown(false);
      setCsItems([]);
      return;
    }

    const delayDebounceFn = setTimeout(() => {
      if (itemName && itemName.length > 1) {
        fetchCsItems(itemName);
      } else {
        setCsItems([]);
        setShowDropdown(false);
      }
    }, 300);

    return () => clearTimeout(delayDebounceFn);
  }, [itemName, csItemId]);

  const fetchCsItems = async (query) => {
    try {
      const token = UserService.getToken();
      const response = await axios.get(
        `${UserService.BASE_URL}/cs-items/search`,
        {
          headers: {
            Authorization: `Bearer ${token}`,
          },
          params: {
            query: query,
          },
        },
      );
      setCsItems(response.data);
      setShowDropdown(true);
    } catch (error) {
      console.error("Error fetching CS items:", error);
    }
  };

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
    setShowDropdown(false);
    setCsItems([]);
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
    setCsItems([]);
    setShowDropdown(false);
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

    try {
      const formattedDate = format(date, "dd.MM.yyyy");
      const token = UserService.getToken();
      await axios.post(
        `${UserService.BASE_URL}/item`,
        { ...item, date: formattedDate },
        {
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${token}`,
          },
        },
      );
      clearForm();
      if (onItemAdded) onItemAdded();
    } catch (error) {
      console.error("There was an error submitting the form!", error);
    }
  };

  return (
    <div className="container mt-4 mb-4">
      <div className="card add-item-card shadow-lg p-4">
        <div className="d-flex align-items-center justify-content-between mb-3">
          <h3 className="add-item-title mb-0">📝 Register Portfolio Item</h3>
          <span className="text-white-50 small">Record CS items you bought or sold to track your portfolio</span>
        </div>

        {error && <div className="alert alert-danger py-2">{error}</div>}

        <form onSubmit={onSubmit}>
          <div className="row g-3 align-items-end">
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
                + Register Item
              </button>
            </div>
          </div>
        </form>
      </div>
    </div>
  );
}
