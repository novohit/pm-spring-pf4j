package com.pmplugin4j.sample.basic;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sample-basic")
public class BasicController {

    @GetMapping("/hello")
    public String hello() {
        return "Hello from the basic plugin";
    }
}
