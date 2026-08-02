# AGENTS.md

## Project Overview

`pm-spring-pf4j` is a Java 21 multi-module Maven framework that integrates PF4J plugins with
Spring Boot. Each plugin owns a child `ApplicationContext`, and plugin resources must be registered
and removed in a deterministic lifecycle order.

- Maven group: `io.github.novohit`
- Java package root: `com.pmplugin4j`
- Spring Boot: 3.3.x
- PF4J: 3.15.x
- License: Apache-2.0

Do not rename the Java package root to match the Maven group. Do not add source code or
documentation references to projects used only as private design references.

## Build and Verification

```bash
# Full reactor build and tests
mvn clean verify

# Test one module and its reactor dependencies
mvn -pl pm-pf4j-core -am test

# Validate Central sources/Javadoc generation without signing
mvn -Pcentral -Dgpg.skip=true verify
```

Run the full reactor build before handing off changes that affect public APIs, dependency wiring,
plugin lifecycle, auto-configuration, or Maven publishing.

## Module Boundaries

| Module | Responsibility |
|---|---|
| `pm-pf4j-core` | Public plugin API, lifecycle engine, events, configuration model, and resource registrar SPI |
| `pm-pf4j-webmvc` | Dynamic MVC controller registration/removal and OpenAPI integration |
| `pm-pf4j-mybatis` | Plugin-owned MyBatis mapper and SqlSession resources |
| `pm-pf4j-jpa` | Plugin-owned JPA EntityManager and repository resources |
| `pm-pf4j-spring-boot-starter` | Auto-configuration, plugin managers, host context integration, and configuration properties |
| `pm-pf4j-test` | Reusable host/plugin lifecycle testing support |
| `pm-pf4j-sample` | Example host and plugin; never publish these artifacts to Maven Central |

Keep optional framework integrations out of `pm-pf4j-core`. The core module must not depend on
Web MVC, MyBatis, JPA, MongoDB, or host-application business code.

## Architecture Invariants

- Maintain one child `ApplicationContext` per loaded plugin.
- Treat plugin start as transactional: if a registrar fails, roll back resources already registered.
- Stop and unload resources in reverse registration order.
- Registration and cleanup operations must be idempotent where practical.
- Never retain plugin classes, beans, controllers, mappers, repositories, or class loaders after
  unload.
- Host applications may expose services to plugins through explicit APIs; plugin internals must not
  leak into the host context.
- MyBatis and JPA integrations are independent optional modules. A host may enable either or both.
- Every dynamically registered resource must have a matching deterministic removal path.

## Spring and Compatibility Rules

- Use Spring Boot auto-configuration imports under
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- Prefer conditional auto-configuration so optional ORM and web dependencies remain optional.
- Avoid component scanning across plugin class loaders from the host context.
- Do not expose internal implementation classes as public API without an explicit compatibility
  reason.
- Treat changes to public interfaces, configuration property names, plugin descriptors, and Maven
  coordinates as compatibility-sensitive.

## Testing Expectations

- Every production-code change must add or update tests that verify the changed behavior.
- During implementation, run the affected module tests with reactor dependencies by using
  `mvn -pl <module> -am test -DskipTests=false`.
- Before committing or handing off any refactor, run `mvn clean verify` and report the result.
- Do not treat a successful compilation as sufficient verification for behavior changes.
- Put focused unit tests beside the owning module.
- Add lifecycle tests for start, partial-start rollback, stop, restart, and unload behavior.
- For dynamic resources, assert both registration and cleanup.
- For class-loader-sensitive changes, verify that stopped/unloaded plugins are not retained by host
  registries or static caches.
- Keep sample applications minimal; reusable test infrastructure belongs in `pm-pf4j-test`.

## Git and Release Rules

- Use Conventional Commit prefixes such as `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, and
  `build:`.
- Do not rewrite or move a tag after its version has been published to Maven Central.
- Maven Central versions are immutable. Fix a released version by publishing a higher version.
- Release tags use `v<version>`, for example `v0.1.0-alpha.1`.
- The GitHub Release tag must exactly match `${project.version}` with a leading `v`.
- Never commit Central tokens, GPG private keys, passphrases, generated signatures, Maven `target/`
  directories, or local IDE state.
- The Central release profile publishes framework modules only; sample artifacts must remain
  excluded.
