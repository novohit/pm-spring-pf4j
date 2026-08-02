# Plugin Extensions

插件扩展遵循一个边界：基础设施由宿主持有，插件只获得按插件派生的使用实例。扩展通过
`PluginResourceRegistrar` 参与插件上下文的创建、启动和关闭，不需要修改插件管理器源码。

## 生命周期

| 阶段 | 用途 |
|---|---|
| `BEFORE_CONTEXT_REFRESH` | 向插件上下文注册独立资源 |
| `AFTER_CONTEXT_REFRESH` | 扫描插件 Bean，并向宿主注册能力 |
| `BEFORE_CONTEXT_CLOSE` | 注销宿主资源并释放插件资源 |

启动时按 `order()` 从小到大执行，关闭和失败回滚时按实际完成顺序逆序执行。插件开始加载后，
registrar 集合会被冻结，避免不同插件看到不同的扩展集合。

## 三种集成方式

### 1. 在宿主中直接注册

适合只服务于一个宿主的能力。在宿主扫描范围内声明 Bean：

```java
@Configuration
class HostPluginExtensions {
    @Bean
    PluginResourceRegistrar redisPluginRegistrar() {
        return new RedisPluginRegistrar();
    }
}
```

### 2. 独立扩展模块自动发现

适合复用和发布。扩展模块提供 registrar，并通过 Spring Boot 自动配置导出；宿主只需添加该
扩展模块依赖。框架本身不内置或强制依赖具体基础设施。

### 3. 显式注册

外部包可以使用 `@ComponentScan`、`@Import` 将 registrar 交给 Spring，也可以在插件加载前调用：

```java
tenantPluginManager.addExternalRegistrar(new InternalRpcPluginRegistrar());
```

编程式注册不依赖包扫描；若在首个插件开始加载后调用，会立即失败并提示 registrar 已冻结。

## Mongo 宿主扩展示例

MongoDB 不属于框架模块。宿主依赖 Spring Data MongoDB，并声明一个 Registrar：

```java
@Bean
PluginResourceRegistrar mongoPluginRegistrar() {
    return new PluginResourceRegistrar() {
        @Override
        public Set<PluginLifecyclePhase> phases() {
            return Set.of(PluginLifecyclePhase.BEFORE_CONTEXT_REFRESH);
        }

        @Override
        public void onBeforeContextRefresh(AnnotationConfigApplicationContext pluginContext) {
            MongoTemplate host = pluginContext.getParent().getBean(MongoTemplate.class);
            MongoTemplate plugin = new MongoTemplate(host.getMongoDatabaseFactory());
            pluginContext.getBeanFactory().registerSingleton("mongoTemplate", plugin);
        }
    };
}
```

插件仅将 `spring-data-mongodb` 声明为 `provided`，然后直接注入标准接口：

```java
@Service
class CustomerDocuments {
    private final MongoOperations mongo;

    CustomerDocuments(MongoOperations mongo) {
        this.mongo = mongo;
    }

    Customer save(Customer customer) {
        return mongo.save(customer);
    }
}
```

可运行代码位于 `pm-pf4j-sample`：宿主的 `MongoPluginExtensionConfiguration` 创建插件专属
`MongoTemplate`，插件的 `MongoGreetingStore` 注入 `MongoOperations`。启动宿主并加载示例插件后，
可以调用 `POST /sample-plugin/mongo-greetings?message=hello` 验证写入。
