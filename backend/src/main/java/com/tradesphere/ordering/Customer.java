package com.tradesphere.ordering;

import java.time.Instant;
import java.util.UUID;

public record Customer(UUID id, String name, String contactName, String email, String phone, String address,
                       boolean active, long version, Instant createdAt, Instant updatedAt) { }
