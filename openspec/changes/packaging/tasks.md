## 1. GraalVM Native Image

- [ ] 1.1 Add `org.graalvm.buildtools.native` plugin to build.gradle.kts
- [ ] 1.2 Create `src/main/resources/META-INF/native-image/reflect-config.json` for SnakeYAML and LangChain4j
- [ ] 1.3 Test `./gradlew nativeCompile` — fix reflection/resource issues as they appear

## 2. jlink Fallback

- [ ] 2.1 Add jlink plugin configuration to build.gradle.kts
- [ ] 2.2 Test `./gradlew jlink` produces a runnable image

## 3. Distribution

- [ ] 3.1 Create `kafka-agent` shell wrapper script
- [ ] 3.2 Verify: native binary starts in under 1 second
