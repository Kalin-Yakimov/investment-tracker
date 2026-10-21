package com.kalin.investment_tracker.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer>, UserSecurityRepository {

    Optional<User> findByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);

    @Query(
            value = """
                    select user from User user
                    where :query = ''
                       or lower(user.email) like lower(concat('%', :query, '%'))
                       or lower(user.username) like lower(concat('%', :query, '%'))
                    """,
            countQuery = """
                    select count(user) from User user
                    where :query = ''
                       or lower(user.email) like lower(concat('%', :query, '%'))
                       or lower(user.username) like lower(concat('%', :query, '%'))
                    """
    )
    Page<User> searchForAdministration(@Param("query") String query, Pageable pageable);

    long countByEnabledTrue();

    long countByEnabledFalse();

    long countByAccountLockedTrue();
}
