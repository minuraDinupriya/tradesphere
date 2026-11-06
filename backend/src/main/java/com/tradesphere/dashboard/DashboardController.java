package com.tradesphere.dashboard;

import com.tradesphere.identity.OrganizationUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {
    private final DashboardService dashboard;
    public DashboardController(DashboardService dashboard) { this.dashboard = dashboard; }
    @GetMapping("/api/v1/dashboard")
    public DashboardService.Summary get(@AuthenticationPrincipal OrganizationUser user) {
        return dashboard.summary(user.organizationId());
    }
}
