package com.orionticket.identity.infrastructure.adapters.out.persistence.repository;

import com.orionticket.identity.infrastructure.adapters.out.persistence.entity.RoleJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataRoleRepository extends JpaRepository<RoleJpaEntity, UUID> {
}
