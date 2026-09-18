package com.kalin.investment_tracker.repository;

import com.kalin.investment_tracker.model.Item;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Collection;

public interface ItemRepository extends JpaRepository<Item, Long> {

    // Shows all items of a specific user sorted by newest to oldest by date (non-Steam items only).
    @Query("SELECT i FROM Item i WHERE i.user.id = :userId AND (i.steamFoundCheck IS NULL OR i.steamFoundCheck = false) ORDER BY i.date DESC")
    @EntityGraph(attributePaths = "catalogueItem")
    Page<Item> findByUserIdOrderByDateDesc(@Param("userId") Integer userId, Pageable pageable);

    // All non-Steam items of a specific user with optional status filter and dynamic Pageable sorting
    @Query("SELECT i FROM Item i WHERE i.user.id = :userId AND (i.steamFoundCheck IS NULL OR i.steamFoundCheck = false) AND (:status IS NULL OR i.itemStatus = :status)")
    @EntityGraph(attributePaths = "catalogueItem")
    Page<Item> findByUserIdAndOptionalStatus(@Param("userId") Integer userId, @Param("status") com.kalin.investment_tracker.model.ItemStatus status, Pageable pageable);

    @Query(
            value = "SELECT i FROM Item i WHERE i.user.id = :userId " +
                    "AND (i.steamFoundCheck IS NULL OR i.steamFoundCheck = false) " +
                    "ORDER BY (COALESCE(i.price, 0) * i.quantity) DESC",
            countQuery = "SELECT COUNT(i) FROM Item i WHERE i.user.id = :userId " +
                    "AND (i.steamFoundCheck IS NULL OR i.steamFoundCheck = false)"
    )
    @EntityGraph(attributePaths = "catalogueItem")
    Page<Item> findPortfolioOrderByTotalPriceDesc(@Param("userId") Integer userId, Pageable pageable);

    @Query(
            value = "SELECT i FROM Item i WHERE i.user.id = :userId " +
                    "AND (i.steamFoundCheck IS NULL OR i.steamFoundCheck = false) " +
                    "ORDER BY (COALESCE(i.price, 0) * i.quantity) ASC",
            countQuery = "SELECT COUNT(i) FROM Item i WHERE i.user.id = :userId " +
                    "AND (i.steamFoundCheck IS NULL OR i.steamFoundCheck = false)"
    )
    @EntityGraph(attributePaths = "catalogueItem")
    Page<Item> findPortfolioOrderByTotalPriceAsc(@Param("userId") Integer userId, Pageable pageable);

    // Used for the item history (non-Steam items only).
    @Query("SELECT i FROM Item i WHERE i.user.id = :userId AND i.itemName = :itemName AND (i.steamFoundCheck IS NULL OR i.steamFoundCheck = false) ORDER BY i.date DESC")
    @EntityGraph(attributePaths = "catalogueItem")
    Page<Item> findByUserIdAndItemNameOrderByDateDesc(@Param("userId") Integer userId, @Param("itemName") String itemName, Pageable pageable);

    // Efficient DB-level query for all Steam-verified tracked items (replaces findAll() + stream filter in the scheduler).
    @Query("SELECT i FROM Item i WHERE i.steamFoundCheck = true")
    List<Item> findAllSteamTrackedItems();

    // Efficient DB-level query for a specific user's Steam-tracked items.
    @Query("SELECT i FROM Item i WHERE i.user.id = :userId AND i.steamFoundCheck = true")
    @EntityGraph(attributePaths = "catalogueItem")
    List<Item> findSteamTrackedItemsByUserId(@Param("userId") Integer userId);

    // Fast DB-level check if a user is already tracking a market hash name
    boolean existsByUserIdAndMarketHashNameAndSteamFoundCheckTrue(Integer userId, String marketHashName);

    // IDOR protection: find item by ID strictly belonging to the authenticated user
    @EntityGraph(attributePaths = "catalogueItem")
    java.util.Optional<Item> findByIdAndUserId(Long id, Integer userId);

    List<Item> findAllByIdInAndUserIdAndSteamFoundCheckTrue(Collection<Long> ids, Integer userId);

    // Total count of all non-Steam items for a user
    @Query("SELECT COUNT(i) FROM Item i WHERE i.user.id = :userId AND (i.steamFoundCheck IS NULL OR i.steamFoundCheck = false)")
    long countAllUserItems(@Param("userId") Integer userId);

    // Total investment value of all BOUGHT non-Steam items for a user
    @Query("SELECT COALESCE(SUM(i.price * i.quantity), 0) FROM Item i WHERE i.user.id = :userId AND (i.steamFoundCheck IS NULL OR i.steamFoundCheck = false) AND i.itemStatus = com.kalin.investment_tracker.model.ItemStatus.BOUGHT")
    java.math.BigDecimal sumTotalInvestmentValue(@Param("userId") Integer userId);

    @Query("SELECT COUNT(i) FROM Item i WHERE (i.steamFoundCheck IS NULL OR i.steamFoundCheck = false)")
    long countAllPortfolioItems();

    @Query("SELECT COUNT(i) FROM Item i WHERE i.steamFoundCheck = true")
    long countAllTrackedItems();

    @Query("SELECT COUNT(i) FROM Item i WHERE i.user.id = :userId AND i.steamFoundCheck = true")
    long countTrackedItemsByUserId(@Param("userId") Integer userId);

    @Query("SELECT i FROM Item i WHERE i.user.id = :userId AND (i.steamFoundCheck IS NULL OR i.steamFoundCheck = false)")
    @EntityGraph(attributePaths = "catalogueItem")
    Page<Item> findPortfolioForAdministration(@Param("userId") Integer userId, Pageable pageable);
}
