package com.orionticket.identity.infrastructure.adapters.out.persistence;

import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.domain.port.out.UserRepositoryPort;
import com.orionticket.identity.infrastructure.adapters.out.persistence.entity.UserJpaEntity;
import com.orionticket.identity.infrastructure.adapters.out.persistence.repository.SpringDataUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepositoryPort {

    private final SpringDataUserRepository repository;

    @Override
    public User save(User user) {
        UserJpaEntity entity = UserJpaEntity.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .passwordHash(user.getPasswordHash())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .status(user.getStatus())
                .roleId(user.getRoleId())
                .organizerId(user.getOrganizerId())
                .createdAt(user.getCreatedAt())
                .build();
        
        UserJpaEntity savedEntity = repository.save(entity);
        return mapToDomain(savedEntity);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(email).map(this::mapToDomain);
    }

    @Override
    public Optional<User> findById(java.util.UUID userId) {
        return repository.findById(userId).map(this::mapToDomain);
    }

    @Override
    public java.util.List<User> findAll() {
        return repository.findAll().stream()
                .map(this::mapToDomain)
                .collect(java.util.stream.Collectors.toList());
    }

    private User mapToDomain(UserJpaEntity entity) {
        return User.builder()
                .userId(entity.getUserId())
                .email(entity.getEmail())
                .passwordHash(entity.getPasswordHash())
                .fullName(entity.getFullName())
                .phone(entity.getPhone())
                .status(entity.getStatus())
                .roleId(entity.getRoleId())
                .organizerId(entity.getOrganizerId())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
