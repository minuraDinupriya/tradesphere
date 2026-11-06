package com.tradesphere.ordering;

import java.time.Instant;
import java.util.UUID;

public record OrderStateEvent(UUID id, String fromStatus, String toStatus, UUID actorId,
                              String actorUsername, Instant occurredAt, boolean backfilled) { }
