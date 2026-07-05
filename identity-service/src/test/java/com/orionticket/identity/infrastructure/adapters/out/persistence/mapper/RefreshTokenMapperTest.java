package com.orionticket.identity.infrastructure.adapters.out.persistence.mapper;

import com.orionticket.identity.domain.model.RefreshToken;
import com.orionticket.identity.infrastructure.adapters.out.persistence.entity.RefreshTokenJpaEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RefreshTokenMapperTest {

    @Test
    void toDomainMapsAllFields() {
        UUID tokenId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Instant issued = Instant.now().minusSeconds(60);
        Instant expires = Instant.now().plusSeconds(3600);
        Instant revoked = Instant.now();

        RefreshTokenJpaEntity entity = RefreshTokenJpaEntity.builder()
                .tokenId(tokenId)
                .userId(userId)
                .tokenHash("abc123")
                .parentId(parentId)
                .issuedAt(issued)
                .expiresAt(expires)
                .revokedAt(revoked)
                .userAgent("Mozilla")
                .ipAddress("10.0.0.1")
                .build();

        RefreshToken domain = RefreshTokenMapper.toDomain(entity);

        assertEquals(tokenId, domain.getTokenId());
        assertEquals(userId, domain.getUserId());
        assertEquals("abc123", domain.getTokenHash());
        assertEquals(parentId, domain.getParentId());
        assertEquals(issued, domain.getIssuedAt());
        assertEquals(expires, domain.getExpiresAt());
        assertEquals(revoked, domain.getRevokedAt());
        assertEquals("Mozilla", domain.getUserAgent());
        assertEquals("10.0.0.1", domain.getIpAddress());
    }

    @Test
    void toEntityMapsAllFields() {
        UUID tokenId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        RefreshToken domain = RefreshToken.builder()
                .tokenId(tokenId)
                .userId(userId)
                .tokenHash("hash456")
                .parentId(null)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .revokedAt(null)
                .userAgent("curl/8")
                .ipAddress("192.168.1.1")
                .build();

        RefreshTokenJpaEntity entity = RefreshTokenMapper.toEntity(domain);

        assertEquals(tokenId, entity.getTokenId());
        assertEquals(userId, entity.getUserId());
        assertEquals("hash456", entity.getTokenHash());
        assertNull(entity.getParentId());
        assertEquals("curl/8", entity.getUserAgent());
        assertEquals("192.168.1.1", entity.getIpAddress());
    }

    @Test
    void toDomainNullReturnsNull() {
        assertNull(RefreshTokenMapper.toDomain(null));
    }

    @Test
    void toEntityNullReturnsNull() {
        assertNull(RefreshTokenMapper.toEntity(null));
    }

    @Test
    void roundTripPreservesAllFields() {
        UUID tokenId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Instant issued = Instant.now();
        Instant expires = Instant.now().plusSeconds(7200);

        RefreshToken original = RefreshToken.builder()
                .tokenId(tokenId)
                .userId(userId)
                .tokenHash("roundtrip")
                .parentId(parentId)
                .issuedAt(issued)
                .expiresAt(expires)
                .revokedAt(null)
                .userAgent("TestAgent")
                .ipAddress("10.0.0.99")
                .build();

        RefreshTokenJpaEntity entity = RefreshTokenMapper.toEntity(original);
        RefreshToken roundTripped = RefreshTokenMapper.toDomain(entity);

        assertEquals(original.getTokenId(), roundTripped.getTokenId());
        assertEquals(original.getUserId(), roundTripped.getUserId());
        assertEquals(original.getTokenHash(), roundTripped.getTokenHash());
        assertEquals(original.getParentId(), roundTripped.getParentId());
        assertEquals(original.getIssuedAt(), roundTripped.getIssuedAt());
        assertEquals(original.getExpiresAt(), roundTripped.getExpiresAt());
        assertEquals(original.getRevokedAt(), roundTripped.getRevokedAt());
        assertEquals(original.getUserAgent(), roundTripped.getUserAgent());
        assertEquals(original.getIpAddress(), roundTripped.getIpAddress());
    }
}
