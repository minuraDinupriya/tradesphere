package com.tradesphere.ordering;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CustomerUpdateRequest(@NotBlank @Size(max = 160) String name,
                                    @Size(max = 160) String contactName,
                                    @Email @Size(max = 254) String email,
                                    @Size(max = 40) String phone,
                                    @Size(max = 1000) String address,
                                    @NotNull Boolean active,
                                    @NotNull @PositiveOrZero Long version) {
    public CustomerValues values() { return new CustomerValues(name, contactName, email, phone, address, active); }
}
