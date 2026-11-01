package com.swiftdrop.backend.delivery;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory placeholder until the team adds the database; same interface a JPA repository would offer. */
@Repository
public class DeliveryRepository {

    private final Map<UUID, Delivery> deliveries = new ConcurrentHashMap<>();

    public Delivery save(Delivery delivery) {
        deliveries.put(delivery.id(), delivery);
        return delivery;
    }

    public Optional<Delivery> findById(UUID id) {
        return Optional.ofNullable(deliveries.get(id));
    }

    public List<Delivery> findAll() {
        return List.copyOf(deliveries.values());
    }

    public List<Delivery> findByCourierId(UUID courierId) {
        return deliveries.values().stream().filter(delivery -> delivery.courierId().equals(courierId)).toList();
    }
}
