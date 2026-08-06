# pm-spring-pf4j

`pm-spring-pf4j` is a Spring Boot plugin runtime built on PF4J. It gives each plugin an isolated child `ApplicationContext` and manages plugin-owned Spring resources across load, start, stop, restart, and unload operations.

## Status

The project is being extracted from a production application and redesigned as a reusable dependency. The current `0.1.0-alpha.1` API is not stable yet.

Initial scope:

- one child `ApplicationContext` per plugin;
- Spring dependency injection inside plugins;
- dynamic Spring MVC controller registration;
- MyBatis mapper integration;
- optional JPA integration;
- resource tracking and lifecycle-safe cleanup;
- tenant-aware plugin selection at the host boundary.

## Modules

```text
pm-spring-pf4j
├── pm-pf4j-bom                  Published dependency version alignment
├── pm-pf4j-core                 Plugin API, events, SPI, shared configuration model
├── pm-pf4j-runtime              PF4J descriptors, repository, and runtime manager
├── pm-pf4j-spring-boot-starter  Auto-configuration and host dependency entry point
├── pm-pf4j-web                  MVC and WebFlux integration
├── pm-pf4j-security             MVC and WebFlux security integration
├── pm-pf4j-mybatis              Optional MyBatis integration
├── pm-pf4j-jpa                  Optional JPA integration
├── pm-pf4j-testkit              Reusable plugin testing support
├── pm-pf4j-integration-tests    Cross-module runtime verification
├── pm-pf4j-archetype            Minimal plugin Maven project generator
└── pm-pf4j-samples/
    ├── sample-host-webmvc       MVC host with MongoDB and plugin Security integration
    ├── sample-host-webflux      Reactive host with plugin Security integration
    ├── sample-plugin-basic      Minimal controller plugin
    ├── sample-plugin-data       Dynamic web plugin consuming the MongoDB bridge
    └── sample-plugin-security   Header-authenticated plugin
```

Both hosts demonstrate host-owned authorization with plugin-provided authentication. Build and copy
`sample-plugin-security` into `plugins/com.pmplugin4j.sample.security/`, then call
`GET /sample-security/hello` with `X-Plugin-Token: sample-token`. Requests without the header receive
`401 Unauthorized`. The MVC host additionally demonstrates an isolated MongoDB resource bridge with
`sample-plugin-data`.

The modules intentionally separate the plugin contract from optional host integrations. Plugin
projects should depend on `pm-pf4j-core` and only the integration APIs they use; host applications
normally consume `pm-pf4j-spring-boot-starter`.

See [Plugin Extensions](docs/plugin-extensions.md) for the three supported integration styles.
See [Plugin authentication](docs/plugin-authentication.md) for custom authentication providers and security filter
extension slots.

## Build

```bash
mvn clean verify
```

## Maven coordinates

Published artifacts use the Maven group `io.github.novohit`. Java packages remain under
`com.pmplugin4j`.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.novohit</groupId>
            <artifactId>pm-pf4j-bom</artifactId>
            <version>0.1.0-alpha.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
<dependency>
    <groupId>io.github.novohit</groupId>
    <artifactId>pm-pf4j-spring-boot-starter</artifactId>
</dependency>
</dependencies>
```

Pre-release versions are intended for evaluation while the public API is evolving.

## Generate a plugin

After the archetype is available from Maven Central or installed locally, generate a minimal Java 21 plugin with:

```bash
mvn archetype:generate -B \
  -DarchetypeGroupId=io.github.novohit \
  -DarchetypeArtifactId=pm-pf4j-archetype \
  -DarchetypeVersion=0.1.0-alpha.1 \
  -DgroupId=com.example \
  -DartifactId=example-plugin \
  -Dversion=1.0.0 \
  -Dpackage=com.example.plugin
```

The generated JAR contains a standard PF4J `plugin.properties` descriptor and keeps framework dependencies in
`provided` scope. Deploy plugins under the host's configured plugin directory and enable their `plugin.id` for the
current tenant.

Each plugin uses its own directory. Multiple versions may coexist; the runtime selects the latest version:

```text
plugins/
└── com.example.plugin/
    ├── com.example.plugin-1.0.0.jar
    └── com.example.plugin-1.1.0.jar
```

The plugin JAR declares its identity and startup metadata in `src/main/resources/plugin.properties`:

```properties
plugin.id=com.example.plugin
plugin.class=com.example.plugin.Plugin
plugin.version=1.1.0
plugin.provider=example
plugin.dependencies=
plugin.order=100000
```

## Migration provenance

The initial history was extracted from `pm-plugin-framework/` in the `variant/pmplugin` branch of `personnel-matching-backend`.

- source branch tip: `32a059f740d268a68c3a82ce7fe1b92a3bcaea3a`
- extraction method: Git subdirectory history filtering
- preserved metadata: author, author date, commit message, and commit order

Commit hashes changed because the original repository paths and parent trees were removed during extraction.

## Roadmap

1. Expand restart, unload, and class-loader leak integration coverage.
2. Add more reusable infrastructure Registrar modules as demand emerges.
3. Stabilize the public API through pre-release feedback.
