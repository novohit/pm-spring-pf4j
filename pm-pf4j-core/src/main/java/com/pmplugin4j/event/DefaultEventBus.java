package com.pmplugin4j.event;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 事件总线实现
 *
 * 支持同步/异步事件发布，支持租户过滤
 */
public class DefaultEventBus implements EventBus {

    private static final Logger log = LoggerFactory.getLogger(DefaultEventBus.class);

    /**
     * 事件订阅者映射：eventType -> List<Subscription>
     */
    private final Map<Class<?>, List<Subscription<?>>> subscriptions = new ConcurrentHashMap<>();

    @Override
    public void publish(Object event) {
        log.debug("Publishing event: {}", event.getClass().getSimpleName());

        Class<?> eventType = event.getClass();
        List<Subscription<?>> subscribers = subscriptions.get(eventType);

        if (subscribers == null || subscribers.isEmpty()) {
            log.debug("No subscribers for event: {}", eventType.getSimpleName());
            return;
        }

        // 获取事件中的租户ID（如果有）
        String tenantId = extractTenantId(event);

        for (Subscription<?> subscription : subscribers) {
            // 检查租户过滤
            if (subscription.tenantId != null && !subscription.tenantId.equals(tenantId)) {
                continue;
            }

            try {
                ((EventListener<Object>) subscription.listener).onEvent(event);
            } catch (Exception e) {
                log.error("Error handling event {} in listener: {}", eventType.getSimpleName(),
                        subscription.listener.getClass().getSimpleName(), e);
            }
        }
    }

    @Override
    public <T> void subscribe(Class<T> eventType, EventListener<T> listener) {
        subscribe(eventType, listener, null);
    }

    @Override
    public <T> void subscribe(Class<T> eventType, EventListener<T> listener, String tenantId) {
        log.debug("Subscribing to event: {} with tenant: {}", eventType.getSimpleName(), tenantId);

        List<Subscription<?>> subscribers = subscriptions.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());

        subscribers.add(new Subscription<>(listener, tenantId));
    }

    @Override
    public <T> void unsubscribe(Class<T> eventType, EventListener<T> listener) {
        log.debug("Unsubscribing from event: {}", eventType.getSimpleName());

        List<Subscription<?>> subscribers = subscriptions.get(eventType);
        if (subscribers != null) {
            subscribers.removeIf(s -> s.listener == listener);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<EventListener<T>> getSubscribers(Class<T> eventType) {
        List<Subscription<?>> subscribers = subscriptions.get(eventType);
        if (subscribers == null) {
            return Collections.emptyList();
        }

        List<EventListener<T>> result = new ArrayList<>();
        for (Subscription<?> subscription : subscribers) {
            result.add((EventListener<T>) subscription.listener);
        }
        return result;
    }

    /**
     * 从事件中提取租户ID 支持通过反射获取event.getTenantId()方法
     */
    private String extractTenantId(Object event) {
        try {
            // 尝试通过反射调用getTenantId()方法
            Method method = event.getClass().getMethod("getTenantId");
            return (String) method.invoke(event);
        } catch (Exception e) {
            // 如果没有getTenantId方法，返回null
            return null;
        }
    }

    /**
     * 订阅记录
     */
    private static class Subscription<T> {
        final EventListener<T> listener;
        final String tenantId;

        Subscription(EventListener<T> listener, String tenantId) {
            this.listener = listener;
            this.tenantId = tenantId;
        }
    }
}
