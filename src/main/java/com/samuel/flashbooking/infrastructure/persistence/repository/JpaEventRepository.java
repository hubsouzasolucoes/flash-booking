package com.samuel.flashbooking.infrastructure.persistence.repository;

import com.samuel.flashbooking.infrastructure.persistence.entity.EventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaEventRepository extends JpaRepository<EventEntity, UUID> {
}
