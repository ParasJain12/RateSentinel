package com.ratesentinel.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    // Serves the dashboard HTML page
    @GetMapping("/dashboard")
    public String dashboard() {
        return "forward:/dashboard.html";
    }

}