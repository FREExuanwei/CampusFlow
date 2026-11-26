package com.campusflow.modules.user.repository;

import com.campusflow.modules.user.entity.UserAccount;
import com.campusflow.modules.user.enums.UserStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    @EntityGraph(attributePaths = "roles")
    Optional<UserAccount> findByUsername(String username);

    boolean existsByUsername(String username);

    Optional<LoginAccount> findLoginAccountByUsername(String username);

    @EntityGraph(attributePaths = "roles")
    Optional<UserAccount> findWithRolesById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from UserAccount account where account.id = :id")
    Optional<UserAccount> findLockedById(@Param("id") long id);

    @Query("""
            select account from UserAccount account
            where (:status is null or account.status = :status)
              and (:username is null or locate(:username, account.username) > 0)
            """)
    Page<UserAccount> search(@Param("status") UserStatus status, @Param("username") String username, Pageable pageable);

    interface LoginAccount {
        Long getId();
        String getPasswordHash();
        UserStatus getStatus();
        long getTokenVersion();
    }
}
