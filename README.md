# pm-spring-pf4j

`pm-spring-pf4j` is a Spring Boot plugin runtime built on PF4J. It gives each plugin an isolated child `ApplicationContext` and manages plugin-owned Spring resources across load, start, stop, restart, and unload operations.

## Status

The project is being extracted from a production application and redesigned as a reusable dependency. The current `0.1.0-SNAPSHOT` API is not stable yet.

Initial scope:

- one child `ApplicationContext` per plugin;
- Spring dependency injection inside plugins;
- dynamic Spring MVC controller registration;
- MyBatis mapper integration;
- resource tracking and lifecycle-safe cleanup;
- tenant-aware plugin selection at the host boundary.

## Build

```bash
mvn clean verify
```

## Migration provenance

The initial history was extracted from `pm-plugin-framework/` in the `variant/pmplugin` branch of `personnel-matching-backend`.

- source branch tip: `32a059f740d268a68c3a82ce7fe1b92a3bcaea3a`
- extraction method: Git subdirectory history filtering
- preserved metadata: author, author date, commit message, and commit order

Commit hashes changed because the original repository paths and parent trees were removed during extraction.

## Roadmap

1. Make the extracted module build independently.
2. Introduce an ordered plugin resource lifecycle engine.
3. Align child-context creation and cleanup with PF4J start/stop semantics.
4. Add rollback for partial plugin startup failures.
5. Add restart, unload, and class-loader leak integration tests.
6. Split optional integrations into focused modules after the core API stabilizes.
