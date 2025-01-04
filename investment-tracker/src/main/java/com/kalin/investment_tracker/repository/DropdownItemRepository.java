package com.kalin.investment_tracker.repository;

import com.kalin.investment_tracker.model.DropdownItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;


public interface DropdownItemRepository extends JpaRepository<DropdownItem, Long> {

    //showing by newest order. (higher number id = newer record) No created date so using id.
    Page<DropdownItem> findAllByUserIdOrderByIdDesc(Integer userId, Pageable pageable);

    Page<DropdownItem> searchByUserIdAndItemNameContainingIgnoreCase(Integer userId, String itemName, Pageable pageable);

    boolean existsByItemNameAndUserId(String itemName, Integer userId);
}
