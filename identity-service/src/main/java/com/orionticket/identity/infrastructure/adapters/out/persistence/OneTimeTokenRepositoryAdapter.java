package com.orionticket.identity.infrastructure.adapters.out.persistence;

import com.orionticket.identity.domain.model.OneTimeToken;
import com.orionticket.identity.domain.model.OneTimeToken.TokenType;
import com.orionticket.identity.domain.port.out.OneTimeTokenRepositoryPort;
import com.orionticket.identity.infrastructure.adapters.out.persistence.entity.OneTimeTokenJpaEntity;
import com.orionticket.identity.infrastructure.adapters.out.persistence.mapper.OneTimeTokenMapper;
import com.orionticket.identity.infrastructure.adapters.out.persistence.repository.SpringDataOneTimeTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OneTimeTokenRepositoryAdapter implements OneTimeTokenRepositoryPort {

    private final SpringDataOneTimeTokenRepository repository;

    @Override
    public OneTimeToken save(OneTimeToken token) {
        OneTimeTokenJpaEntity entity = OneTimeTokenMapper.toEntity(token);
        OneTimeTokenJpaEntity saved = repository.save(entity);
        return OneTimeTokenMapper.toDomain(saved);
    }

    @Override
    public Optional<OneTimeToken> findByTokenHashAndType(String tokenHash, TokenType tokenType) {
        return repository.findByTokenHashAndTokenType(tokenHash, tokenType.name())
                .map(OneTimeTokenMapper::toDomain);
    }

    @Override
    public void markAllUnusedForUserAndType(UUID userId, TokenType tokenType) {
        repository.markAllUnusedForUserAndType(userId, tokenType.name(), Instant.now());
    }

    @Override
    public long countActiveForUserAndType(UUID userId, TokenType tokenType) {
        return repository.countActiveForUserAndType(userId, tokenType.name(), Instant.now());
    }
}
