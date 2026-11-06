package com.tradesphere.api;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Same-origin React routing in the packaged demo. API routes retain their separate security rules. */
@Controller
@Profile("demo")
public class DemoPageController {
    @GetMapping({"/", "/dashboard", "/products", "/products/**", "/customers", "/customers/**",
            "/orders", "/orders/**", "/warehouses", "/warehouses/**", "/inventory", "/inventory/**"})
    public String page() { return "forward:/index.html"; }
}
