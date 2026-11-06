package com.tradesphere.inventory;

import java.time.Instant;
import java.util.UUID;

public record Warehouse(UUID id, String code, String name, String location, boolean active,
                        long version, Instant createdAt, Instant updatedAt) { }
