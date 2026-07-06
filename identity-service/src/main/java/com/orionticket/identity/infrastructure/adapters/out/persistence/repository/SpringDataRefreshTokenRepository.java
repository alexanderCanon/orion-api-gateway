package com.orionticket.identity.infrastructure.adapters.out.persistence.repository;

import com.orionticket.identity.infrastructure.adapters.out.persistence.entity.RefreshTokenJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataRefreshTokenRepository extends JpaRepository<RefreshTokenJpaEntity, UUID> {

    Optional<RefreshTokenJpaEntity> findByTokenHash(String tokenHash);

    /**
     * Revoca todos los tokens activos (revokedAt null) de un usuario.
     * Devuelve el número de filas afectadas.
     */
    @Modifying
    @Query("UPDATE RefreshTokenJpaEntity t SET t.revokedAt = :now " +
           "WHERE t.userId = :userId AND t.revokedAt IS NULL")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    /**
     * Revoca recursivamente toda la cadena de rotación descendente de un token
     * (el token mismo y todos sus hijos). Usa una CTE recursiva para recorrer
     * la cadena parent_id.
     */
    @Modifying
    @Query(value = """
            WITH RECURSIVE chain AS (
                SELECT token_id FROM refresh_tokens WHERE token_id = :tokenId
                UNION ALL
                SELECT t.token_id FROM refresh_tokens t
                JOIN chain c ON t.parent_id = c.token_id
            )
            UPDATE refresh_tokens SET revoked_at = :now
            WHERE token_id IN (SELECT token_id FROM chain) AND revoked_at IS NULL
            """, nativeQuery = true)
    int revokeChain(@Param("tokenId") UUID tokenId, @Param("now") Instant now);

    @Query("SELECT COUNT(t) FROM RefreshTokenJpaEntity t " +
           "WHERE t.userId = :userId AND t.revokedAt IS NULL AND t.expiresAt > :now")
    long countActiveForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
