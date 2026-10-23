package com.kalin.investment_tracker.user;

import java.util.Optional;

/** Account-level serialization point for authentication and security mutations. */
public interface UserSecurityRepository {
    Optional<User> findLockedById(Integer id);
    Optional<User> findLockedByEmail(String email);
}
