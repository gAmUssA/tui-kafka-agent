## 1. GraalVM Native Image

- [x] 1.1 Add `org.graalvm.buildtools.native` plugin to build.gradle.kts
- [x] 1.2 Create `src/main/resources/META-INF/native-image/reflect-config.json` for SnakeYAML and LangChain4j
- [ ] 1.3 Test `./gradlew nativeCompile` — fix reflection/resource issues as they appear (requires GraalVM JDK)

## 2. jlink Fallback

- [x] 2.1 Add jlink plugin configuration to build.gradle.kts
- [x] 2.2 Test `./gradlew jlinkImage` produces a runnable image

## 3. Distribution

- [x] 3.1 Create `kafka-agent` shell wrapper script (native binary > fat JAR fallback)
- [ ] 3.2 Verify: native binary starts in under 1 second (requires GraalVM JDK)
