package com.samuel.flashbooking.infrastructure.persistence.adapter;

import com.samuel.flashbooking.application.event.EventAvailability;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class PostgresEventAvailability implements EventAvailability {
    private final JdbcTemplate jdbc;

    public PostgresEventAvailability(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<View> findById(UUID id) {
        return jdbc.query("SELECT event_id,name,starts_at,total_capacity,available_tickets,created_at,updated_at,last_event_version " +
                        "FROM event_availability_projection WHERE event_id=?", (rs, row) -> new View(
                        rs.getObject(1, UUID.class), rs.getString(2), rs.getTimestamp(3).toInstant(), rs.getInt(4),
                        rs.getInt(5), rs.getTimestamp(6).toInstant(), rs.getTimestamp(7).toInstant(), rs.getLong(8)), id)
                .stream().findFirst();
    }
}
