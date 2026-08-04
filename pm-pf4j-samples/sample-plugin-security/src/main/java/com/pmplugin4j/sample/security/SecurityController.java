package com.pmplugin4j.sample.security;

import com.pmplugin4j.core.PluginAuthenticated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sample-security")
public class SecurityController {

    @PluginAuthenticated
    @GetMapping("/hello")
    public String hello() {
        return "Hello from the authenticated plugin";
    }
}
