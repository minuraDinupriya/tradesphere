package com.tradesphere.ordering;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CustomerValues(@NotBlank @Size(max = 160) String name,
                             @Size(max = 160) String contactName,
                             @Email @Size(max = 254) String email,
                             @Size(max = 40) String phone,
                             @Size(max = 1000) String address,
                             @NotNull Boolean active) {
    public CustomerValues normalized() {
        return new CustomerValues(name.strip(), clean(contactName), clean(email), clean(phone), clean(address), active);
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.strip(); }
}
