package com.orionticket.identity.infrastructure.adapters.out.persistence.repository;

import com.orionticket.identity.infrastructure.adapters.out.persistence.entity.OneTimeTokenJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataOneTimeTokenRepository extends JpaRepository<OneTimeTokenJpaEntity, UUID> {

    Optional<OneTimeTokenJpaEntity> findByTokenHashAndTokenType(String tokenHash, String tokenType);

    @Modifying
    @Query("UPDATE OneTimeTokenJpaEntity t SET t.usedAt = :now " +
           "WHERE t.userId = :userId AND t.tokenType = :tokenType AND t.usedAt IS NULL")
    int markAllUnusedForUserAndType(@Param("userId") UUID userId,
                                    @Param("tokenType") String tokenType,
                                    @Param("now") Instant now);

    @Query("SELECT COUNT(t) FROM OneTimeTokenJpaEntity t " +
           "WHERE t.userId = :userId AND t.tokenType = :tokenType " +
           "AND t.usedAt IS NULL AND t.expiresAt > :now")
    long countActiveForUserAndType(@Param("userId") UUID userId,
                                   @Param("tokenType") String tokenType,
                                   @Param("now") Instant now);
}
