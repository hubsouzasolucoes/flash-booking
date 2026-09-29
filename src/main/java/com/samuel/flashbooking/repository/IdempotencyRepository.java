package com.samuel.flashbooking.repository;

import com.samuel.flashbooking.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, UUID> {
    Optional<IdempotencyRecord> findByKey(String key);
}
