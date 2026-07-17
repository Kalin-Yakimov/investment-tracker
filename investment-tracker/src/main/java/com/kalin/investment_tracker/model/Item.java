package com.kalin.investment_tracker.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.kalin.investment_tracker.user.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String itemName;

    private String csItemId; // Changed to String to match CsItem ID type

    @Enumerated(EnumType.STRING)
    private ItemStatus itemStatus;

    @JsonFormat(pattern = "dd.MM.yyyy")
    private LocalDate date;

    private int quantity;

    private BigDecimal price;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonBackReference
    private User user;

    // Steam Community Market specific fields
    private String marketHashName;

    @Column(precision = 18, scale = 2)
    private BigDecimal boughtPrice;

    private LocalDate boughtDate;

    private Boolean showInDashboard = false;

    @Column(precision = 18, scale = 2)
    private BigDecimal priceNotification;

    private Boolean steamFoundCheck = false;

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public ItemStatus getItemStatus() {
        return itemStatus;
    }

    public void setItemStatus(ItemStatus itemStatus) {
        this.itemStatus = itemStatus;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getCsItemId() {
        return csItemId;
    }

    public void setCsItemId(String csItemId) {
        this.csItemId = csItemId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getMarketHashName() {
        return marketHashName;
    }

    public void setMarketHashName(String marketHashName) {
        this.marketHashName = marketHashName;
    }

    public BigDecimal getBoughtPrice() {
        return boughtPrice;
    }

    public void setBoughtPrice(BigDecimal boughtPrice) {
        this.boughtPrice = boughtPrice;
    }

    public LocalDate getBoughtDate() {
        return boughtDate;
    }

    public void setBoughtDate(LocalDate boughtDate) {
        this.boughtDate = boughtDate;
    }

    public Boolean getShowInDashboard() {
        return showInDashboard;
    }

    public void setShowInDashboard(Boolean showInDashboard) {
        this.showInDashboard = showInDashboard;
    }

    public BigDecimal getPriceNotification() {
        return priceNotification;
    }

    public void setPriceNotification(BigDecimal priceNotification) {
        this.priceNotification = priceNotification;
    }

    public Boolean getSteamFoundCheck() {
        return steamFoundCheck;
    }

    public void setSteamFoundCheck(Boolean steamFoundCheck) {
        this.steamFoundCheck = steamFoundCheck;
    }
}
