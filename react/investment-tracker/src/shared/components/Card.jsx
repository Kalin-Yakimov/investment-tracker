import React from "react";
import { Card as BSCard } from "react-bootstrap";

function Card({ children }) {
  return (
    <BSCard>
      <BSCard.Body>{children}</BSCard.Body>
    </BSCard>
  );
}

export default Card;
