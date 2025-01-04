import axios from "axios";
import React, { useEffect, useState, useCallback } from "react";
import { useNavigate, useParams } from "react-router-dom";
import UserService from "../service/UserService";
import DatePicker from "react-datepicker";
import { format } from "date-fns";

export default function EditItem() {
  const navigate = useNavigate();
  const { id } = useParams();

  const [item, setItem] = useState({
    itemName: "",
    itemStatus: "PICK",
    date: new Date(),
    quantity: "",
    price: "",
  });

  const [error, setError] = useState("");

  const { itemName, itemStatus, date, quantity, price } = item;

  const onInputChange = (e) => {
    const { name, value } = e.target;
    setItem((prevItem) => ({
      ...prevItem,
      [name]: name === "itemStatus" ? value.toUpperCase() : value,
    }));
    setError("");
  };

  const loadItem = useCallback(async () => {
    try {
      const token = UserService.getToken();
      const result = await axios.get(`http://localhost:8080/item/${id}`, {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });
      let item = result.data;

      if (item.date) {
        const [day, month, year] = item.date.split(".");
        item.date = new Date(`${year}-${month}-${day}`);
      }

      console.log(item);
      setItem(item);
    } catch (error) {
      console.error("Error loading item:", error);
    }
  }, [id]);

  useEffect(() => {
    loadItem();
  }, [loadItem]);

  const onSubmit = async (e) => {
    e.preventDefault();
    if (itemStatus === "PICK" || !itemName || !date || !quantity || !price) {
      setError("Please fill out all fields correctly.");
      return;
    }

    try {
      const formattedDate = format(date, "dd.MM.yyyy");
      const token = UserService.getToken();
      await axios.put(
        `http://localhost:8080/item/${id}`,
        { ...item, date: formattedDate },
        {
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${token}`,
          },
        }
      );
      navigate("/");
    } catch (error) {
      console.error("There was an error submitting the form!", error);
    }
  };

  const handleDateChange = (date) => {
    setItem((prevItem) => ({
      ...prevItem,
      date: date,
    }));
    setError("");
  };

  return (
    <div className="container mt-4">
      <div className="row justify-content-center">
        <div className="col-md-12 border rounded p-4 shadow">
          <h2 className="text-center mb-4">Edit Item</h2>
          {error && <div className="alert alert-danger">{error}</div>}
          <form onSubmit={onSubmit} className="row g-3">
            <div className="col-md-2">
              <label htmlFor="itemName" className="form-label">
                Name
              </label>
              <input
                type="text"
                className="form-control"
                placeholder="Enter item name"
                name="itemName"
                value={itemName}
                onChange={onInputChange}
                id="itemName"
                required
              />
            </div>
            <div className="col-md-2">
              <label htmlFor="itemStatus" className="form-label">
                Status
              </label>
              <select
                className="form-control"
                name="itemStatus"
                value={itemStatus}
                onChange={onInputChange}
                id="itemStatus"
                required
              >
                <option value="PICK">Pick</option>
                <option value="BOUGHT">Bought</option>
                <option value="SOLD">Sold</option>
              </select>
            </div>
            <div className="col-md-2">
              <label htmlFor="date" className="form-label">
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
            <div className="col-md-2">
              <label htmlFor="quantity" className="form-label">
                Quantity
              </label>
              <input
                type="number"
                className="form-control"
                placeholder="Enter quantity"
                name="quantity"
                value={quantity}
                onChange={onInputChange}
                id="quantity"
                required
              />
            </div>
            <div className="col-md-2">
              <label htmlFor="price" className="form-label">
                Price
              </label>
              <input
                type="number"
                className="form-control"
                placeholder="Enter price"
                name="price"
                value={price}
                onChange={onInputChange}
                id="price"
                step="0.01"
                min="0"
                required
              />
            </div>
            <div className="col-md-2 d-flex align-items-end">
              <button type="submit" className="btn btn-primary me-2">
                Update
              </button>
              <button
                type="button"
                className="btn btn-danger"
                onClick={() => navigate("/")}
              >
                Cancel
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}
