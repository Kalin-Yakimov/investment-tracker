package com.kalin.investment_tracker.catalogue;

import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;

public class CsItemSpecification {

    public static Specification<CsItem> containsTextInName(String text) {
        return (root, query, criteriaBuilder) -> {
            if (text == null || text.isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            
            // Split by space, dash, pipe, parentheses, etc. to get clean keywords
            String[] keywords = text.toLowerCase().split("[\\s\\-\\|\\(\\)]+");

            // Start with a "true" predicate (conjunction)
            Predicate predicate = criteriaBuilder.conjunction();

            for (String keyword : keywords) {
                if (!keyword.isEmpty()) {
                    // Add a LIKE condition for each keyword: name LIKE %keyword%
                    // We use criteriaBuilder.lower() on the name column to make it case-insensitive
                    predicate = criteriaBuilder.and(predicate, 
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), "%" + keyword + "%"));
                }
            }
            return predicate;
        };
    }
}
