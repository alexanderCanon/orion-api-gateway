package com.orionticket.identity.infrastructure.adapters.out.persistence.mapper;

import com.orionticket.identity.domain.model.OneTimeToken;
import com.orionticket.identity.domain.model.OneTimeToken.TokenType;
import com.orionticket.identity.infrastructure.adapters.out.persistence.entity.OneTimeTokenJpaEntity;

/**
 * Mapper bidireccional entre {@link OneTimeToken} (dominio) y
 * {@link OneTimeTokenJpaEntity} (persistencia).
 */
public final class OneTimeTokenMapper {

    private OneTimeTokenMapper() {}

    public static OneTimeToken toDomain(OneTimeTokenJpaEntity e) {
        if (e == null) return null;
        return OneTimeToken.builder()
                .tokenId(e.getTokenId())
                .userId(e.getUserId())
                .tokenHash(e.getTokenHash())
                .tokenType(TokenType.valueOf(e.getTokenType()))
                .createdAt(e.getCreatedAt())
                .expiresAt(e.getExpiresAt())
                .usedAt(e.getUsedAt())
                .build();
    }

    public static OneTimeTokenJpaEntity toEntity(OneTimeToken t) {
        if (t == null) return null;
        return OneTimeTokenJpaEntity.builder()
                .tokenId(t.getTokenId())
                .userId(t.getUserId())
                .tokenHash(t.getTokenHash())
                .tokenType(t.getTokenType().name())
                .createdAt(t.getCreatedAt())
                .expiresAt(t.getExpiresAt())
                .usedAt(t.getUsedAt())
                .build();
    }
}
