package com.agileboot.plugin.event;

import java.util.List;

/**
 * 事件总线接口
 *
 * 宿主和插件通过事件总线进行解耦通信
 */
public interface EventBus {

    /**
     * 发布事件
     *
     * @param event 事件对象
     */
    void publish(Object event);

    /**
     * 订阅事件
     *
     * @param eventType 事件类型
     * @param listener 事件监听器
     * @param <T> 事件类型
     */
    <T> void subscribe(Class<T> eventType, EventListener<T> listener);

    /**
     * 订阅事件（带租户过滤）
     *
     * @param eventType 事件类型
     * @param listener 事件监听器
     * @param tenantId 租户ID（null表示所有租户）
     * @param <T> 事件类型
     */
    <T> void subscribe(Class<T> eventType, EventListener<T> listener, String tenantId);

    /**
     * 取消订阅
     *
     * @param eventType 事件类型
     * @param listener 事件监听器
     * @param <T> 事件类型
     */
    <T> void unsubscribe(Class<T> eventType, EventListener<T> listener);

    /**
     * 获取事件的所有订阅者
     *
     * @param eventType 事件类型
     * @return 订阅者列表
     */
    <T> List<EventListener<T>> getSubscribers(Class<T> eventType);

    /**
     * 事件监听器
     *
     * @param <T> 事件类型
     */
    @FunctionalInterface
    interface EventListener<T> {
        /**
         * 处理事件
         *
         * @param event 事件对象
         */
        void onEvent(T event);
    }
}
