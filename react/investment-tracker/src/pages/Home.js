import React, { useEffect, useState, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import UserService from "../service/UserService";
import { Table } from "react-bootstrap";
import "../styles/Home.css";
import Card from "../utils/Card";

export default function Home() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [currentPage, setCurrentPage] = useState(0);
  const [itemsPerPage] = useState(10);
  const [totalPages, setTotalPages] = useState(0);
  const navigate = useNavigate();

  const loadItems = useCallback(
    async (page = 0) => {
      try {
        const result = await UserService.getUserItems(page, itemsPerPage);
        console.log("API response:", result);

        setItems(result.content);
        setTotalPages(result.totalPages);
        setCurrentPage(page);
      } catch (error) {
        console.error("Error loading items:", error);
        setItems([]);
      } finally {
        setLoading(false);
      }
    },
    [itemsPerPage],
  );

  useEffect(() => {
    loadItems();
  }, [loadItems]);

  const handleRowClick = (id) => {
    navigate(`/viewItem/${id}`);
  };

  const handlePageChange = (page) => {
    if (page === currentPage) return;
    setLoading(true);
    loadItems(page);
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
            onClick={() => handlePageChange(currentPage - 1)}
            disabled={currentPage === 0}
          >
            Previous
          </button>
        </li>
        {Array.from({ length: totalPages }, (_, index) => (
          <li
            key={index}
            className={`page-item ${index === currentPage ? "active" : ""}`}
          >
            <button
              className="page-link"
              onClick={() => handlePageChange(index)}
            >
              {index + 1}
            </button>
          </li>
        ))}
        <li
          className={`page-item ${
            currentPage === totalPages - 1 ? "disabled" : ""
          }`}
        >
          <button
            className="page-link"
            onClick={() => handlePageChange(currentPage + 1)}
            disabled={currentPage === totalPages - 1}
          >
            Next
          </button>
        </li>
      </ul>
    </nav>
  );

  return (
    <div className="container">
      <div className="py-4">
        {loading ? (
          <div className="alert alert-info" role="alert">
            Loading items...
          </div>
        ) : items.length > 0 ? (
          <Card>
            <Table bordered hover responsive size="md">
              <thead>
                <tr>
                  <th scope="col">Image</th>
                  <th scope="col">Name</th>
                  <th scope="col">Status</th>
                  <th scope="col">Date</th>
                  <th scope="col">Quantity</th>
                  <th scope="col">Price</th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => (
                  <tr
                    key={item.id}
                    onClick={() => handleRowClick(item.id)}
                    className="table-row-link"
                  >
                    <td>
                      {item.image && (
                        <img
                          src={item.image}
                          alt={item.itemName}
                          style={{
                            width: "60px",
                            height: "auto",
                            objectFit: "contain",
                          }}
                        />
                      )}
                    </td>
                    <td>{item.itemName}</td>
                    <td>
                      <span
                        className={`badge ${
                          item.itemStatus === "BOUGHT"
                            ? "bg-success"
                            : "bg-danger"
                        }`}
                      >
                        {item.itemStatus === "BOUGHT" ? "Bought" : "Sold"}
                      </span>
                    </td>
                    <td>{item.date}</td>
                    <td>{item.quantity}</td>
                    <td>${Number(item.price).toFixed(2)}</td>
                  </tr>
                ))}
              </tbody>
            </Table>
            {renderPagination()}
          </Card>
        ) : (
          <div className="alert alert-warning" role="alert">
            There are no items to show.
          </div>
        )}
      </div>
    </div>
  );
}
