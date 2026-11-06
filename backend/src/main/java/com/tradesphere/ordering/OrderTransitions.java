package com.tradesphere.ordering;

import org.springframework.http.HttpStatus;

/** Approved one-way lifecycle. Replays are handled under the order lock using recorded history. */
public final class OrderTransitions {
    private OrderTransitions() { }

    public enum Action {
        CONFIRM("CONFIRMED"), PROCESS("PROCESSING"), CANCEL("CANCELLED"), FULFILL("FULFILLED");
        private final String target;
        Action(String target) { this.target = target; }
        public String target() { return target; }
    }

    public static String next(String current, Action action) {
        var allowed = switch (action) {
            case CONFIRM -> current.equals("PENDING");
            case PROCESS -> current.equals("CONFIRMED");
            case CANCEL -> current.equals("PENDING") || current.equals("CONFIRMED");
            case FULFILL -> current.equals("PROCESSING");
        };
        if (!allowed) throw new OrderException(HttpStatus.CONFLICT,
                "Cannot " + action.name().toLowerCase(java.util.Locale.ROOT) + " an order in " + current + " state. Reload the latest order.");
        return action.target();
    }
}
