package com.samuel.flashbooking.application;

import com.samuel.flashbooking.domain.shared.DomainEvent;

public interface DomainEventOutbox {
    void append(DomainEvent event);
}
