import React, { useEffect, useState, useCallback } from "react";
import { Link, useParams } from "react-router-dom";
import axios from "axios";
import UserService from "../service/UserService";
import { Table } from "react-bootstrap";
import Card from "../utils/Card";

export default function ViewItem() {
  const [item, setItem] = useState({
    itemName: "",
    itemStatus: "",
    date: "",
    quantity: "",
    price: "",
    image: null,
  });
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [currentPage, setCurrentPage] = useState(0);
  const [historyTotalPages, setHistoryTotalPages] = useState(0);
  const [historyLoading, setHistoryLoading] = useState(false);
  const { id } = useParams();
  const itemsPerPage = 10;

  const loadItem = useCallback(async () => {
    try {
      const token = UserService.getToken();
      const result = await axios.get(`http://localhost:8080/item/${id}`, {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });
      console.log("Item data received:", result.data);
      console.log("Item image field:", result.data.image);
      setItem(result.data);
    } catch (error) {
      console.error("Error fetching item:", error);
      setError("Item not found");
    } finally {
      setLoading(false);
    }
  }, [id]);

  const loadItemHistory = useCallback(
    async (page = 0) => {
      setHistoryLoading(true);
      try {
        const result = await UserService.getItemByName(
          item.itemName,
          page,
          itemsPerPage,
        );
        console.log("Item history data received:", result.content);
        console.log(
          "History items with images:",
          result.content.map((item) => ({
            name: item.itemName,
            image: item.image,
          })),
        );
        setHistory(result.content);
        setHistoryTotalPages(result.totalPages);
        setCurrentPage(page);
      } catch (error) {
        console.error("Error fetching item history:", error);
      } finally {
        setHistoryLoading(false);
      }
    },
    [item.itemName, itemsPerPage],
  );

  useEffect(() => {
    loadItem();
  }, [loadItem]);

  useEffect(() => {
    if (item.itemName) {
      loadItemHistory();
    }
  }, [item.itemName, loadItemHistory]);

  const deleteItem = async () => {
    try {
      const token = UserService.getToken();
      await axios.delete(`http://localhost:8080/item/${id}`, {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });
      window.location.href = "/";
    } catch (error) {
      console.error("Error deleting item:", error);
    }
  };

  const handleHistoryPageChange = (page) => {
    if (page === currentPage) return;
    loadItemHistory(page);
  };

  const renderPagination = () => (
    <nav
      aria-label="Page navigation example"
      className="d-flex justify-content-end"
    >
      <ul className="pagination">
        <li className={`page-item ${currentPage === 0 ? "disabled" : ""}`}>
          <button
            className="page-link"
            onClick={() => handleHistoryPageChange(currentPage - 1)}
            disabled={currentPage === 0}
          >
            Previous
          </button>
        </li>
        {Array.from({ length: historyTotalPages }, (_, index) => (
          <li
            key={index}
            className={`page-item ${index === currentPage ? "active" : ""}`}
          >
            <button
              className="page-link"
              onClick={() => handleHistoryPageChange(index)}
            >
              {index + 1}
            </button>
          </li>
        ))}
        <li
          className={`page-item ${
            currentPage === historyTotalPages - 1 ? "disabled" : ""
          }`}
        >
          <button
            className="page-link"
            onClick={() => handleHistoryPageChange(currentPage + 1)}
            disabled={currentPage === historyTotalPages - 1}
          >
            Next
          </button>
        </li>
      </ul>
    </nav>
  );

  if (loading) return <div>Loading...</div>;

  if (error) {
    return (
      <div className="container mt-4">
        <div className="alert alert-danger" role="alert">
          {error}
        </div>
        <Link className="btn btn-primary my-2" to={"/"}>
          Back to Home
        </Link>
      </div>
    );
  }

  return (
    <div className="container mt-4 mb-4">
      <div className="row justify-content-center">
        <div className="col-md-12 border rounded p-4 shadow bg-light">
          <h2
            className="text-center mb-4"
            style={{ color: "#333", fontWeight: "bold" }}
          >
            Item Details
          </h2>

          {/* Image Section - Refactored Design */}
          <div
            style={{
              display: "flex",
              justifyContent: "center",
              alignItems: "center",
              marginBottom: "2rem",
            }}
          >
            <div
              style={{
                backgroundColor: "#fff",
                border: "2px solid #ddd",
                borderRadius: "8px",
                padding: "20px",
                textAlign: "center",
                boxShadow: "0 2px 8px rgba(0,0,0,0.1)",
                maxWidth: "280px",
                width: "100%",
              }}
            >
              <div
                style={{
                  width: "240px",
                  height: "240px",
                  display: "flex",
                  justifyContent: "center",
                  alignItems: "center",
                  backgroundColor: "#f9f9f9",
                  borderRadius: "6px",
                  marginBottom: "1rem",
                  overflow: "hidden",
                }}
              >
                {item.image ? (
                  <img
                    src={item.image}
                    alt={item.itemName}
                    style={{
                      width: "100%",
                      height: "100%",
                      objectFit: "contain",
                    }}
                    onError={(e) => {
                      console.error("Image failed to load:", item.image);
                      e.target.style.display = "none";
                    }}
                  />
                ) : (
                  <div
                    style={{
                      textAlign: "center",
                      color: "#999",
                      fontSize: "14px",
                    }}
                  >
                    <p>No image available</p>
                  </div>
                )}
              </div>
              <h4
                style={{
                  fontSize: "1.4rem",
                  fontWeight: "bold",
                  color: "#333",
                  marginBottom: "0",
                  wordBreak: "break-word",
                }}
              >
                {item.itemName}
              </h4>
            </div>
          </div>

          {/* Action Buttons */}
          <div
            style={{
              display: "flex",
              justifyContent: "center",
              gap: "10px",
              marginBottom: "2rem",
              flexWrap: "wrap",
            }}
          >
            <Link className="btn btn-primary" to={`/editItem/${id}`}>
              Edit
            </Link>
            <Link className="btn btn-secondary" to={"/"}>
              Back to Home
            </Link>
            <button className="btn btn-danger" onClick={deleteItem}>
              Delete
            </button>
          </div>

          {/* Item History Section */}
          <h3
            className="text-center mt-5 mb-4"
            style={{ color: "#333", fontWeight: "bold" }}
          >
            Item History
          </h3>
          {historyLoading ? (
            <div className="alert alert-info">Loading history...</div>
          ) : history.length > 0 ? (
            <>
              <Card>
                <Table bordered hover responsive size="md">
                  <thead style={{ backgroundColor: "#f8f9fa" }}>
                    <tr>
                      <th scope="col" style={{ textAlign: "center" }}>
                        Image
                      </th>
                      <th scope="col">Name</th>
                      <th scope="col">Status</th>
                      <th scope="col">Date</th>
                      <th scope="col" style={{ textAlign: "center" }}>
                        Quantity
                      </th>
                      <th
                        scope="col"
                        style={{ textAlign: "right", paddingRight: "20px" }}
                      >
                        Price
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {history.map((historyItem, index) => (
                      <tr key={index}>
                        <td
                          style={{
                            textAlign: "center",
                            verticalAlign: "middle",
                          }}
                        >
                          {historyItem.image ? (
                            <img
                              src={historyItem.image}
                              alt={historyItem.itemName}
                              style={{
                                width: "60px",
                                height: "60px",
                                objectFit: "contain",
                              }}
                              onError={(e) => {
                                console.error(
                                  "History image failed to load:",
                                  historyItem.image,
                                );
                                e.target.style.display = "none";
                              }}
                            />
                          ) : (
                            <span style={{ color: "#999", fontSize: "12px" }}>
                              No image
                            </span>
                          )}
                        </td>
                        <td>{historyItem.itemName}</td>
                        <td>
                          <span
                            className={`badge ${
                              historyItem.itemStatus === "BOUGHT"
                                ? "bg-success"
                                : "bg-danger"
                            }`}
                          >
                            {historyItem.itemStatus === "BOUGHT"
                              ? "Bought"
                              : "Sold"}
                          </span>
                        </td>
                        <td>{historyItem.date}</td>
                        <td style={{ textAlign: "center" }}>
                          {historyItem.quantity}
                        </td>
                        <td
                          style={{ textAlign: "right", paddingRight: "20px" }}
                        >
                          ${Number(historyItem.price).toFixed(2)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </Table>
                {renderPagination()}
              </Card>
            </>
          ) : (
            <div className="alert alert-warning" role="alert">
              There is no history to show.
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
