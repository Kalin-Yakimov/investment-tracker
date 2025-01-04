package com.kalin.investment_tracker.repository;

import com.kalin.investment_tracker.model.Item;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, Long> {

    //shows all items of a specific user sorted by newest to oldest by date. Not creation date of the item in the DB.
    Page<Item> findByUserIdOrderByDateDesc(Integer userId, Pageable pageable);

    //used for the item history.
    Page<Item> findByUserIdAndItemNameOrderByDateDesc(Integer userId, String itemName, Pageable pageable);
}
