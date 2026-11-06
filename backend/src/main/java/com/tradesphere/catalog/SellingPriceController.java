package com.tradesphere.catalog;

import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.tradesphere.identity.OrganizationUser;
import tools.jackson.databind.annotation.JsonDeserialize;

@RestController
@RequestMapping("/api/v1/products/{id}/selling-price")
public class SellingPriceController {
    private final SellingPriceService prices;
    public SellingPriceController(SellingPriceService prices) { this.prices = prices; }

    @GetMapping
    public SellingPrice get(@AuthenticationPrincipal OrganizationUser user, @PathVariable UUID id) {
        return prices.get(user.organizationId(), id);
    }

    @PutMapping
    public SellingPrice update(@AuthenticationPrincipal OrganizationUser user, @PathVariable UUID id,
                                                   @Valid @RequestBody PriceRequest request) {
        return prices.update(user.organizationId(), id, request.amount(), request.version());
    }

    public record PriceRequest(
            @JsonDeserialize(using = SellingPriceStringDeserializer.class)
            @NotBlank @Pattern(regexp = "[0-9]{1,12}(\\.[0-9]{1,2})?", message = "Use a nonnegative LKR amount with up to 12 whole digits and two decimal places.") String amount,
            @NotNull @PositiveOrZero Long version) { }
}
