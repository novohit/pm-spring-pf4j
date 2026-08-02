# Java formatter configuration

- `spring-java-format-official-0.0.47.prefs` is an unmodified backup extracted from the official `io.spring.javaformat:spring-javaformat-formatter:0.0.47` artifact.
- `spring-style.prefs` is the project-owned configuration derived from the Spring settings and customized for this repository.

The active project configuration uses a 120-character limit for Java code, comments, and Javadoc.
Spotless direct goals operate on one selected Maven project, so format each affected module explicitly:

```shell
mvn -pl pm-pf4j-core spotless:apply
```

Use `mvn validate` for a fast reactor-wide formatting and Checkstyle check. Use `mvn clean verify`
for the complete build and test suite before handoff.

To verify or refresh the official backup from the Maven artifact:

```shell
unzip -p ~/.m2/repository/io/spring/javaformat/spring-javaformat-formatter/0.0.47/spring-javaformat-formatter-0.0.47.jar \\
  io/spring/javaformat/formatter/eclipse/formatter.prefs
```
