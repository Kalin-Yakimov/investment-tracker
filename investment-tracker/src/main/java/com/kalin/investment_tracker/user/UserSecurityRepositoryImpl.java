package com.kalin.investment_tracker.user;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Locking;
import org.hibernate.Session;

import java.util.Optional;

public class UserSecurityRepositoryImpl implements UserSecurityRepository {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @SuppressWarnings("removal") // JPA 3.2 has no INCLUDE_FETCHES scope; review on JPA 4 / Hibernate 8 upgrade.
    public Optional<User> findLockedById(Integer id) {
        entityManager.flush();
        // Scalar selection locks only _user, without locking shared eager Role rows.
        var ids = entityManager.createQuery("select u.id from User u where u.id = :id", Integer.class)
                .setParameter("id", id).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        if (ids.isEmpty()) return Optional.empty();
        User managed = entityManager.find(User.class, id);
        // Flush above preserves pending work. Detach only this account so an initialized
        // roles collection cannot survive as stale first-level-cache state. Hibernate 7
        // refresh may fetch that collection separately using an old InnoDB snapshot.
        entityManager.detach(managed);
        // One current locking read includes the role membership. Shared role locks are
        // compatible across accounts; the exclusive account-row lock remains held.
        return entityManager.unwrap(Session.class).createSelectionQuery(
                        "select u from User u left join fetch u.roles where u.id = :id", User.class)
                .setParameter("id", id).setLockMode(LockModeType.PESSIMISTIC_READ)
                .setLockScope(Locking.Scope.INCLUDE_FETCHES)
                // Consume all joined rows: a short-circuited stream can leave only
                // the first role in the fetched collection under Hibernate 7.
                .getResultList().stream().findFirst();
    }

    @Override
    public Optional<User> findLockedByEmail(String email) {
        // A case-insensitive scan must not take FOR UPDATE locks on unrelated accounts.
        // Email/account IDs are immutable in the current application.
        var ids = entityManager.createQuery(
                        "select u.id from User u where lower(u.email) = lower(:email)", Integer.class)
                .setParameter("email", email).getResultList();
        return ids.isEmpty() ? Optional.empty() : findLockedById(ids.getFirst());
    }

}
