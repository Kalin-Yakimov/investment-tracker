package com.kalin.investment_tracker.item;

import java.util.Optional;

/** Current locking reads for mutations, distinct from normal read-only queries. */
public interface ItemMutationRepository {
    Optional<Item> findLockedTrackedItem(Long id, Integer userId);
}
