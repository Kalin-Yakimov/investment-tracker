package com.kalin.investment_tracker.repository;

import com.kalin.investment_tracker.model.Item;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItemRepository extends JpaRepository<Item, Long> {

    // Shows all items of a specific user sorted by newest to oldest by date (non-Steam items only).
    @Query("SELECT i FROM Item i WHERE i.user.id = :userId AND (i.steamFoundCheck IS NULL OR i.steamFoundCheck = false) ORDER BY i.date DESC")
    Page<Item> findByUserIdOrderByDateDesc(@Param("userId") Integer userId, Pageable pageable);

    // Used for the item history (non-Steam items only).
    @Query("SELECT i FROM Item i WHERE i.user.id = :userId AND i.itemName = :itemName AND (i.steamFoundCheck IS NULL OR i.steamFoundCheck = false) ORDER BY i.date DESC")
    Page<Item> findByUserIdAndItemNameOrderByDateDesc(@Param("userId") Integer userId, @Param("itemName") String itemName, Pageable pageable);

    // Efficient DB-level query for all Steam-verified tracked items (replaces findAll() + stream filter in the scheduler).
    @Query("SELECT i FROM Item i WHERE i.steamFoundCheck = true")
    List<Item> findAllSteamTrackedItems();

    // Efficient DB-level query for a specific user's Steam-tracked items.
    @Query("SELECT i FROM Item i WHERE i.user.id = :userId AND i.steamFoundCheck = true")
    List<Item> findSteamTrackedItemsByUserId(@Param("userId") Integer userId);

    // Fast DB-level check if a user is already tracking a market hash name
    boolean existsByUserIdAndMarketHashNameAndSteamFoundCheckTrue(Integer userId, String marketHashName);

    // IDOR protection: find item by ID strictly belonging to the authenticated user
    java.util.Optional<Item> findByIdAndUserId(Long id, Integer userId);
}
