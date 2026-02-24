## 1. Config Loader

- [x] 1.1 Create `AppConfig.java` in `config/` package with SnakeYAML-based loader
- [x] 1.2 Implement `${ENV_VAR}` regex substitution on raw YAML string before parsing
- [x] 1.3 Add typed getters: `getAnthropicApiKey()`, `getAnthropicModel()`, `getConfluentBootstrap()`, etc.
- [x] 1.4 Add validation that fails fast if `anthropic.api-key` is empty

## 2. Default Config

- [x] 2.1 Create `src/main/resources/default-config.yaml` with all config keys and comments
- [x] 2.2 Add fallback logic: load default config, then overlay user config if it exists

## 3. Verify

- [ ] 3.1 Test with config file present — confirm values loaded
- [ ] 3.2 Test with config file missing — confirm env var fallback and helpful message
