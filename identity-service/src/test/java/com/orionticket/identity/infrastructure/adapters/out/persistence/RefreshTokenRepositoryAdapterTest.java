package com.orionticket.identity.infrastructure.adapters.out.persistence;

import com.orionticket.identity.domain.model.RefreshToken;
import com.orionticket.identity.infrastructure.adapters.out.persistence.entity.RefreshTokenJpaEntity;
import com.orionticket.identity.infrastructure.adapters.out.persistence.mapper.RefreshTokenMapper;
import com.orionticket.identity.infrastructure.adapters.out.persistence.repository.SpringDataRefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenRepositoryAdapterTest {

    @Mock
    private SpringDataRefreshTokenRepository repository;

    @InjectMocks
    private RefreshTokenRepositoryAdapter adapter;

    @Test
    void savePersistsAndReturnsDomainToken() {
        RefreshToken token = RefreshToken.builder()
                .tokenId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .tokenHash("hash")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        RefreshTokenJpaEntity entity = RefreshTokenMapper.toEntity(token);

        when(repository.save(any(RefreshTokenJpaEntity.class))).thenReturn(entity);

        RefreshToken result = adapter.save(token);

        assertNotNull(result);
        assertEquals(token.getTokenId(), result.getTokenId());
        assertEquals(token.getTokenHash(), result.getTokenHash());
        verify(repository).save(any(RefreshTokenJpaEntity.class));
    }

    @Test
    void findByTokenHashReturnsDomainWhenFound() {
        UUID tokenId = UUID.randomUUID();
        RefreshTokenJpaEntity entity = RefreshTokenJpaEntity.builder()
                .tokenId(tokenId)
                .userId(UUID.randomUUID())
                .tokenHash("found-hash")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(repository.findByTokenHash("found-hash")).thenReturn(Optional.of(entity));

        Optional<RefreshToken> result = adapter.findByTokenHash("found-hash");

        assertTrue(result.isPresent());
        assertEquals(tokenId, result.get().getTokenId());
        assertEquals("found-hash", result.get().getTokenHash());
    }

    @Test
    void findByTokenHashReturnsEmptyWhenNotFound() {
        when(repository.findByTokenHash("missing")).thenReturn(Optional.empty());

        Optional<RefreshToken> result = adapter.findByTokenHash("missing");

        assertTrue(result.isEmpty());
    }

    @Test
    void revokeAllForUserDelegatesToRepository() {
        UUID userId = UUID.randomUUID();
        when(repository.revokeAllForUser(eq(userId), any(Instant.class))).thenReturn(3);

        int count = adapter.revokeAllForUser(userId);

        assertEquals(3, count);
        verify(repository).revokeAllForUser(eq(userId), any(Instant.class));
    }

    @Test
    void revokeChainDelegatesToRepository() {
        UUID tokenId = UUID.randomUUID();
        when(repository.revokeChain(eq(tokenId), any(Instant.class))).thenReturn(5);

        int count = adapter.revokeChain(tokenId);

        assertEquals(5, count);
        verify(repository).revokeChain(eq(tokenId), any(Instant.class));
    }

    @Test
    void countActiveForUserDelegatesToRepository() {
        UUID userId = UUID.randomUUID();
        when(repository.countActiveForUser(eq(userId), any(Instant.class))).thenReturn(2L);

        long count = adapter.countActiveForUser(userId);

        assertEquals(2L, count);
        verify(repository).countActiveForUser(eq(userId), any(Instant.class));
    }
}
