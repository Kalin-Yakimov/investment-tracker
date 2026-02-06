package com.kalin.investment_tracker.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "cs_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CsItem {

    @Id
    private Long id;

    private String name;
}
