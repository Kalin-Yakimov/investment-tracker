package com.kalin.investment_tracker.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.kalin.investment_tracker.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

@Entity
public class DropdownItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            nullable = false,
            length = 60
    )
    private String itemName;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @NotNull
    @JsonBackReference
    private User user;

    public DropdownItem() {
    }

    public DropdownItem(Long id, String itemName) {
        this.id = id;
        this.itemName = itemName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    @Override
    public String toString() {
        return "DropdownItem{" +
                "id=" + id +
                ", itemName='" + itemName + '\'' +
                '}';
    }
}
