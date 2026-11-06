package com.tradesphere.identity;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class SessionController {
    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) { return new CsrfResponse(token.getHeaderName(), token.getToken()); }

    @GetMapping("/session")
    public SessionResponse session(@AuthenticationPrincipal CatalogUser user) {
        return new SessionResponse(user.username(), user.organizationName(), user.canWriteCatalog());
    }

    public record CsrfResponse(String headerName, String token) { }
    public record SessionResponse(String username, String organizationName, boolean canWriteCatalog) { }
}
