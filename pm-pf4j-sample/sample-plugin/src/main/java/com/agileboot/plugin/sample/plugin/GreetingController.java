package com.agileboot.plugin.sample.plugin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sample-plugin")
public class GreetingController {

    @GetMapping("/greeting")
    public String greeting() {
        return "Hello from pm-spring-pf4j";
    }
}
