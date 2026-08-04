package com.kalin.investment_tracker.repository;

import com.kalin.investment_tracker.model.CsItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface CsItemRepository extends JpaRepository<CsItem, String>, JpaSpecificationExecutor<CsItem> {

    @Query("SELECT c FROM CsItem c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) AND NOT (c.id LIKE 'graffiti-%' OR c.id LIKE 'key-%' OR c.id LIKE 'collectible-%' OR c.id LIKE 'collection-%' OR LOWER(c.name) LIKE '%sticker collection%' OR LOWER(c.name) LIKE '%patch collection%' OR LOWER(c.name) LIKE '%pin collection%')")
    List<CsItem> searchByName(@Param("query") String query, Pageable pageable);

    @Query("SELECT c.id FROM CsItem c")
    List<String> findAllItemIds();

    @Query("SELECT COUNT(c) FROM CsItem c WHERE c.id LIKE 'graffiti-%' OR c.id LIKE 'key-%' OR c.id LIKE 'collectible-%' OR c.id LIKE 'collection-%' OR LOWER(c.name) LIKE '%sticker collection%' OR LOWER(c.name) LIKE '%patch collection%' OR LOWER(c.name) LIKE '%pin collection%'")
    long countExcludedCategories();

    @Modifying
    @Transactional
    @Query("DELETE FROM CsItem c WHERE c.id LIKE 'graffiti-%' OR c.id LIKE 'key-%' OR c.id LIKE 'collectible-%' OR c.id LIKE 'collection-%' OR LOWER(c.name) LIKE '%sticker collection%' OR LOWER(c.name) LIKE '%patch collection%' OR LOWER(c.name) LIKE '%pin collection%'")
    int deleteExcludedCategories();

    @Query("SELECT c FROM CsItem c WHERE (LOWER(c.name) = LOWER(:query) OR LOWER(c.marketHashName) = LOWER(:query) OR LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%'))) AND NOT (c.id LIKE 'graffiti-%' OR c.id LIKE 'key-%' OR c.id LIKE 'collectible-%' OR c.id LIKE 'collection-%' OR LOWER(c.name) LIKE '%sticker collection%' OR LOWER(c.name) LIKE '%patch collection%' OR LOWER(c.name) LIKE '%pin collection%') ORDER BY LENGTH(c.name) ASC")
    List<CsItem> findBestMatch(@Param("query") String query, Pageable pageable);
}
