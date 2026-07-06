package com.orionticket.identity.infrastructure.adapters.out.persistence;

import com.orionticket.identity.domain.model.RefreshToken;
import com.orionticket.identity.domain.port.out.RefreshTokenRepositoryPort;
import com.orionticket.identity.infrastructure.adapters.out.persistence.entity.RefreshTokenJpaEntity;
import com.orionticket.identity.infrastructure.adapters.out.persistence.mapper.RefreshTokenMapper;
import com.orionticket.identity.infrastructure.adapters.out.persistence.repository.SpringDataRefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepositoryPort {

    private final SpringDataRefreshTokenRepository repository;

    @Override
    public RefreshToken save(RefreshToken token) {
        RefreshTokenJpaEntity saved = repository.save(RefreshTokenMapper.toEntity(token));
        return RefreshTokenMapper.toDomain(saved);
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(RefreshTokenMapper::toDomain);
    }

    @Override
    public int revokeAllForUser(UUID userId) {
        return repository.revokeAllForUser(userId, Instant.now());
    }

    @Override
    public int revokeChain(UUID tokenId) {
        return repository.revokeChain(tokenId, Instant.now());
    }

    @Override
    public long countActiveForUser(UUID userId) {
        return repository.countActiveForUser(userId, Instant.now());
    }
}
