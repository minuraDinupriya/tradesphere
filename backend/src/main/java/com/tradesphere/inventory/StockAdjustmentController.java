package com.tradesphere.inventory;

import com.tradesphere.identity.OrganizationUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/stock-adjustments")
public class StockAdjustmentController {
    private final StockAdjustmentService adjustments;

    public StockAdjustmentController(StockAdjustmentService adjustments) {
        this.adjustments = adjustments;
    }

    @GetMapping
    public StockAdjustmentService.AdjustmentHistory history(@AuthenticationPrincipal OrganizationUser user,
            @RequestParam UUID warehouseId, @RequestParam UUID productId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return adjustments.history(user.organizationId(), warehouseId, productId, page, size);
    }

    @PostMapping
    public ResponseEntity<StockAdjustmentService.AdjustmentResult> adjust(@AuthenticationPrincipal OrganizationUser user,
            @Valid @RequestBody StockAdjustmentRequest request) {
        var result = adjustments.adjust(user.organizationId(), user.id(), request);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
    }
}
