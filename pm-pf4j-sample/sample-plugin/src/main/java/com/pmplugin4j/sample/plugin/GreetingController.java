package com.pmplugin4j.sample.plugin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sample-plugin")
public class GreetingController {

    private final MongoGreetingStore mongoGreetingStore;

    public GreetingController(MongoGreetingStore mongoGreetingStore) {
        this.mongoGreetingStore = mongoGreetingStore;
    }

    @GetMapping("/greeting")
    public String greeting() {
        return "Hello from pm-spring-pf4j";
    }

    @PostMapping("/mongo-greetings")
    public MongoGreetingStore.GreetingDocument saveMongoGreeting(
            @RequestParam String message) {
        return mongoGreetingStore.save(message);
    }
}
