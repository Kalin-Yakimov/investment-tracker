package com.kalin.investment_tracker.repository;

import com.kalin.investment_tracker.model.CsItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CsItemRepository extends JpaRepository<CsItem, String>, JpaSpecificationExecutor<CsItem> {

    @Query("SELECT c FROM CsItem c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<CsItem> searchByName(@Param("query") String query, Pageable pageable);
}
