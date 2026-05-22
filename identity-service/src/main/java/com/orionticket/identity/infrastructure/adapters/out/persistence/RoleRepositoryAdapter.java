package com.orionticket.identity.infrastructure.adapters.out.persistence;

import com.orionticket.identity.domain.model.Role;
import com.orionticket.identity.domain.port.out.RoleRepositoryPort;
import com.orionticket.identity.infrastructure.adapters.out.persistence.entity.RoleJpaEntity;
import com.orionticket.identity.infrastructure.adapters.out.persistence.mapper.RoleMapper;
import com.orionticket.identity.infrastructure.adapters.out.persistence.repository.SpringDataRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RoleRepositoryAdapter implements RoleRepositoryPort {

    private final SpringDataRoleRepository roleRepository;

    @Override
    public Role save(Role role) {
        RoleJpaEntity entity = RoleMapper.toEntity(role);
        return RoleMapper.toDomain(roleRepository.save(entity));
    }

    @Override
    public Optional<Role> findById(UUID roleId) {
        return roleRepository.findById(roleId).map(RoleMapper::toDomain);
    }

    @Override
    public List<Role> findAll() {
        return roleRepository.findAll().stream()
                .map(RoleMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(UUID roleId) {
        roleRepository.deleteById(roleId);
    }
}
