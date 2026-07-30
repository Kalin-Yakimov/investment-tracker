package com.kalin.investment_tracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.Index;

@Entity
@Table(name = "cs_items", indexes = {
    @Index(name = "idx_cs_items_market_hash", columnList = "marketHashName"),
    @Index(name = "idx_cs_items_name", columnList = "name")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CsItem {

    @Id
    private String id; // Changed to String because the new API uses string IDs like "skin-65604_0"

    private String name;

    @Column(length = 1000)
    private String image;

    @Column(length = 500)
    private String marketHashName;
}
