package com.kalin.investment_tracker.item;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@RequiredArgsConstructor
public class ItemMutationRepositoryImpl implements ItemMutationRepository {
    private final EntityManager entityManager;

    @Override
    public Optional<Item> findLockedTrackedItem(Long id, Integer userId) {
        entityManager.flush();
        var matches = lockingQuery(id, userId);
        if (matches.isEmpty()) return Optional.empty();
        // Hibernate 7 refresh can use a non-locking select once an entity already
        // holds the requested lock; InnoDB then reads the old repeatable-read snapshot.
        // Detach only this item, then hydrate it through a current locking read.
        entityManager.detach(matches.getFirst());
        return lockingQuery(id, userId).stream().findFirst();
    }

    private java.util.List<Item> lockingQuery(Long id, Integer userId) {
        return entityManager.createQuery("""
                SELECT i FROM Item i WHERE i.id = :id AND i.user.id = :userId
                  AND i.steamFoundCheck = true
                """, Item.class).setParameter("id", id).setParameter("userId", userId)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
    }
}
