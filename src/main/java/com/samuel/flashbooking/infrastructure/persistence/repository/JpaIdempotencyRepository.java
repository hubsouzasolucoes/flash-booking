package com.samuel.flashbooking.infrastructure.persistence.repository;

import com.samuel.flashbooking.infrastructure.persistence.entity.IdempotencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface JpaIdempotencyRepository extends JpaRepository<IdempotencyEntity, UUID> {
    Optional<IdempotencyEntity> findByKey(String key);
}
