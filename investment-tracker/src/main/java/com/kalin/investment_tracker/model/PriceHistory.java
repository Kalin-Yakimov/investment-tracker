package com.kalin.investment_tracker.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "price_history")
public class PriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(precision = 18, scale = 2)
    private BigDecimal price;

    private int quantity;

    @Column(precision = 18, scale = 2)
    private BigDecimal totalPrice;

    private LocalDateTime dateAssigned;

    private LocalDateTime dateExtracted;

    public PriceHistory() {
    }

    public PriceHistory(Item item, BigDecimal price, int quantity, LocalDateTime dateAssigned, LocalDateTime dateExtracted) {
        this.item = item;
        this.price = price;
        this.quantity = quantity;
        this.totalPrice = price.multiply(BigDecimal.valueOf(quantity));
        this.dateAssigned = dateAssigned;
        this.dateExtracted = dateExtracted;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public LocalDateTime getDateAssigned() {
        return dateAssigned;
    }

    public void setDateAssigned(LocalDateTime dateAssigned) {
        this.dateAssigned = dateAssigned;
    }

    public LocalDateTime getDateExtracted() {
        return dateExtracted;
    }

    public void setDateExtracted(LocalDateTime dateExtracted) {
        this.dateExtracted = dateExtracted;
    }
}
