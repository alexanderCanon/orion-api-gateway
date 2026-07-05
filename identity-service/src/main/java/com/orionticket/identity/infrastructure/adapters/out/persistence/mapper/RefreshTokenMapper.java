package com.orionticket.identity.infrastructure.adapters.out.persistence.mapper;

import com.orionticket.identity.domain.model.RefreshToken;
import com.orionticket.identity.infrastructure.adapters.out.persistence.entity.RefreshTokenJpaEntity;

public final class RefreshTokenMapper {

    private RefreshTokenMapper() {}

    public static RefreshToken toDomain(RefreshTokenJpaEntity e) {
        if (e == null) return null;
        return RefreshToken.builder()
                .tokenId(e.getTokenId())
                .userId(e.getUserId())
                .tokenHash(e.getTokenHash())
                .parentId(e.getParentId())
                .issuedAt(e.getIssuedAt())
                .expiresAt(e.getExpiresAt())
                .revokedAt(e.getRevokedAt())
                .userAgent(e.getUserAgent())
                .ipAddress(e.getIpAddress())
                .build();
    }

    public static RefreshTokenJpaEntity toEntity(RefreshToken t) {
        if (t == null) return null;
        return RefreshTokenJpaEntity.builder()
                .tokenId(t.getTokenId())
                .userId(t.getUserId())
                .tokenHash(t.getTokenHash())
                .parentId(t.getParentId())
                .issuedAt(t.getIssuedAt())
                .expiresAt(t.getExpiresAt())
                .revokedAt(t.getRevokedAt())
                .userAgent(t.getUserAgent())
                .ipAddress(t.getIpAddress())
                .build();
    }
}
