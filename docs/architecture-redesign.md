# pm-spring-pf4j architecture redesign

## Goals

`pm-spring-pf4j` is a reusable Spring Boot plugin runtime built on PF4J. Integrations are modular,
host policy is explicit, and plugin-owned resources follow a deterministic lifecycle.

The first production baseline must provide:

- one isolated child `ApplicationContext` per plugin;
- ordered, rollback-capable registration of plugin-owned resources;
- Spring MVC controllers with symmetric unregister;
- MyBatis-Plus and Spring Data JPA in the same plugin;
- one transaction boundary across both ORMs when they share a `DataSource`;
- optional ORM activation with no class-loading failure when an ORM is absent;
- deterministic stop, restart, unload, and failed-start cleanup.

## Module layout

```text
pm-pf4j-core                  public plugin API and lifecycle SPI
pm-pf4j-spring-boot-starter   host bootstrap, PF4J manager, capability detection
pm-pf4j-webmvc                MVC route registration and OpenAPI
pm-pf4j-mybatis               per-plugin MyBatis-Plus infrastructure
pm-pf4j-jpa                   per-plugin JPA/Hibernate infrastructure
pm-pf4j-test                  host fixtures and lifecycle assertions
pm-pf4j-sample                executable host and example plugins
```

Optional integrations depend on `core`; `core` never depends on an integration. The starter
composes registrars discovered as host Spring beans. Plugin code depends on `core` plus only the
APIs it uses.

## Plugin context lifecycle

```text
PF4J load
  -> instantiate lightweight plugin wrapper
PF4J start
  -> create child context (not refreshed)
  -> plugin customization hook
  -> BEFORE_CONTEXT_REFRESH registrars
  -> context.refresh()
  -> plugin ready hook
  -> AFTER_CONTEXT_REFRESH registrars
PF4J stop/unload
  -> BEFORE_CONTEXT_CLOSE registrars in reverse order
  -> context.close()
  -> release plugin references and ClassLoader
```

A failed registrar aborts startup. Registrars that already ran are cleaned in reverse order. A
plugin is never reported as started after partial resource registration.

## Dual ORM model

Both ORMs use the host's primary `DataSource`, but their metadata and factories are isolated per
plugin.

### MyBatis-Plus

Each plugin receives its own:

- `MybatisSqlSessionFactoryBean` and `SqlSessionFactory`;
- `SqlSessionTemplate`;
- mapper scanner restricted to `{pluginBasePackage}.dao`;
- configuration and interceptor chain.

It must not mutate the host `SqlSessionFactory` by adding plugin mapper classes. That approach
retains plugin classes after unload.

### JPA/Hibernate

When Hibernate and Spring Data JPA are present, each plugin receives its own:

- persistence unit and `EntityManagerFactory`;
- entity scan restricted to `{pluginBasePackage}.entity`;
- repository scan restricted to `{pluginBasePackage}.repository`;
- exception translation and explicit EMF cleanup.

JPA is opt-in. Absence of Hibernate must not prevent the starter or MyBatis module from loading.

### Transaction ownership

For one shared `DataSource`:

| Active ORM | Primary plugin transaction manager |
|---|---|
| MyBatis only | `DataSourceTransactionManager` |
| JPA only | `JpaTransactionManager` |
| MyBatis + JPA | `JpaTransactionManager` |

MyBatis uses `SpringManagedTransactionFactory`, allowing it to participate in the connection bound
by the plugin's `JpaTransactionManager`. Multiple data sources or XA transactions are outside the
initial contract and require an explicit host-provided transaction strategy.

## Conventions and override points

Defaults derive from the plugin main package rather than requiring the plugin ID to be a Java
package:

- MyBatis mappers: `{basePackage}.dao`
- JPA entities: `{basePackage}.entity`
- JPA repositories: `{basePackage}.repository`

The descriptor may override these packages. This preserves convenient conventions without making
an operational identifier double as a Java namespace.

## Design differences from the reference

- ORM integrations are separate artifacts instead of mandatory dependencies in one large JAR.
- Lifecycle rollback is part of the core contract.
- Plugin ID and Java package are distinct concepts.
- Capability activation is based on classpath, required host beans, and explicit configuration.
- Database migration is a separate future integration; ORM startup never performs implicit DDL by
  default.
