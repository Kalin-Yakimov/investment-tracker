package com.kalin.investment_tracker.repository;

import com.kalin.investment_tracker.model.Item;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemRepository extends JpaRepository<Item, Long> {

    //shows all items of a specific user sorted by newest to oldest by date. Not creation date of the item in the DB.
    @Query("SELECT i FROM Item i WHERE i.user.id = :userId AND (i.steamFoundCheck IS NULL OR i.steamFoundCheck = false) ORDER BY i.date DESC")
    Page<Item> findByUserIdOrderByDateDesc(@Param("userId") Integer userId, Pageable pageable);

    //used for the item history.
    @Query("SELECT i FROM Item i WHERE i.user.id = :userId AND i.itemName = :itemName AND (i.steamFoundCheck IS NULL OR i.steamFoundCheck = false) ORDER BY i.date DESC")
    Page<Item> findByUserIdAndItemNameOrderByDateDesc(@Param("userId") Integer userId, @Param("itemName") String itemName, Pageable pageable);
}
