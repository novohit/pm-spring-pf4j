package com.agileboot.plugin.factory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

public class MongoRepositoryProxyHandler implements InvocationHandler {

    private static final Logger log = LoggerFactory.getLogger(MongoRepositoryProxyHandler.class);

    private final MongoOperations mongoOperations;
    private final Class<?> repositoryInterface;
    private final Class<?> entityType;

    public MongoRepositoryProxyHandler(MongoOperations mongoOperations, Class<?> repositoryInterface) {
        this.mongoOperations = mongoOperations;
        this.repositoryInterface = repositoryInterface;
        this.entityType = resolveEntityType();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();

        if ("toString".equals(name)) return "MongoRepositoryProxy[" + repositoryInterface.getSimpleName() + "]";
        if ("hashCode".equals(name)) return System.identityHashCode(proxy);
        if ("equals".equals(name)) return proxy == args[0];

        // save
        if ("save".equals(name) && args.length == 1) {
            return mongoOperations.save(args[0]);
        }
        // findById
        if ("findById".equals(name) && args.length == 1) {
            return Optional.ofNullable(mongoOperations.findById(args[0], entityType));
        }
        // findAll()
        if ("findAll".equals(name) && args.length == 0) {
            return mongoOperations.findAll(entityType);
        }
        // findAll(Sort)
        if ("findAll".equals(name) && args.length == 1 && args[0] instanceof Sort sort) {
            return mongoOperations.find(new Query().with(sort), entityType);
        }
        // findAll(Pageable)
        if ("findAll".equals(name) && args.length == 1 && args[0] instanceof Pageable pageable) {
            Query query = new Query().with(pageable);
            List<?> list = mongoOperations.find(query, entityType);
            long total = mongoOperations.count(new Query(), entityType);
            return new PageImpl<>(list, pageable, total);
        }
        // deleteById
        if ("deleteById".equals(name) && args.length == 1) {
            mongoOperations.remove(new Query(Criteria.where("_id").is(args[0])), entityType);
            return null;
        }
        // delete(entity)
        if ("delete".equals(name) && args.length == 1) {
            mongoOperations.remove(args[0]);
            return null;
        }
        // deleteAll()
        if ("deleteAll".equals(name) && args.length == 0) {
            mongoOperations.remove(new Query(), entityType);
            return null;
        }
        // count()
        if ("count".equals(name) && args.length == 0) {
            return mongoOperations.count(new Query(), entityType);
        }
        // existsById
        if ("existsById".equals(name) && args.length == 1) {
            return mongoOperations.exists(new Query(Criteria.where("_id").is(args[0])), entityType);
        }

        // Custom find methods: findByXxx(...)
        if (name.startsWith("find") && name.contains("By")) {
            return handleCustomFind(name, args);
        }
        // Custom count methods: countByXxx(...)
        if (name.startsWith("count") && name.contains("By")) {
            return handleCustomCount(name, args);
        }
        // Custom exists methods: existsByXxx(...)
        if (name.startsWith("exists") && name.contains("By")) {
            return handleCustomExists(name, args);
        }

        log.warn("未实现的MongoDB方法: {}.{}", repositoryInterface.getSimpleName(), name);
        return null;
    }

    private Object handleCustomFind(String methodName, Object[] args) {
        String fieldPart = methodName.substring(methodName.indexOf("By") + 2);
        String fieldName = toSnakeCase(fieldPart.split("Order|Desc|Asc|And|Or")[0]);
        Query query = new Query(Criteria.where(fieldName).is(args[0]));

        for (Object arg : args) {
            if (arg instanceof Pageable pageable) {
                query.with(pageable);
                List<?> list = mongoOperations.find(query, entityType);
                long total = mongoOperations.count(new Query(Criteria.where(fieldName).is(args[0])), entityType);
                return new PageImpl<>(list, pageable, total);
            }
            if (arg instanceof Sort sort) {
                query.with(sort);
            }
        }
        return mongoOperations.find(query, entityType);
    }

    private long handleCustomCount(String methodName, Object[] args) {
        String fieldPart = methodName.substring(methodName.indexOf("By") + 2);
        String fieldName = toSnakeCase(fieldPart.split("And|Or")[0]);
        return mongoOperations.count(new Query(Criteria.where(fieldName).is(args[0])), entityType);
    }

    private boolean handleCustomExists(String methodName, Object[] args) {
        String fieldPart = methodName.substring(methodName.indexOf("By") + 2);
        String fieldName = toSnakeCase(fieldPart.split("And|Or")[0]);
        return mongoOperations.exists(new Query(Criteria.where(fieldName).is(args[0])), entityType);
    }

    private String toSnakeCase(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    private Class<?> resolveEntityType() {
        for (java.lang.reflect.Type type : repositoryInterface.getGenericInterfaces()) {
            if (type instanceof java.lang.reflect.ParameterizedType pt) {
                java.lang.reflect.Type[] typeArgs = pt.getActualTypeArguments();
                if (typeArgs.length >= 1 && typeArgs[0] instanceof Class<?> c) return c;
            }
        }
        for (Class<?> iface : repositoryInterface.getInterfaces()) {
            for (java.lang.reflect.Type type : iface.getGenericInterfaces()) {
                if (type instanceof java.lang.reflect.ParameterizedType pt) {
                    java.lang.reflect.Type[] typeArgs = pt.getActualTypeArguments();
                    if (typeArgs.length >= 1 && typeArgs[0] instanceof Class<?> c) return c;
                }
            }
        }
        return null;
    }
}
