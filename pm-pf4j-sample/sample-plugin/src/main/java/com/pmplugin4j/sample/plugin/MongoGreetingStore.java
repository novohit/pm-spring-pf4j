package com.pmplugin4j.sample.plugin;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.stereotype.Service;

@Service
public class MongoGreetingStore {

    private final MongoOperations mongoOperations;

    public MongoGreetingStore(MongoOperations mongoOperations) {
        this.mongoOperations = mongoOperations;
    }

    public GreetingDocument save(String message) {
        return mongoOperations.save(new GreetingDocument(null, message));
    }

    @Document("plugin_greetings")
    public record GreetingDocument(@Id String id, String message) {
    }
}
