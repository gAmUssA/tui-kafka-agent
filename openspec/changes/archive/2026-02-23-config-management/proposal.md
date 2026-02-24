## Why

The app needs API keys for Anthropic and Confluent Cloud, plus model settings and cluster endpoints. A YAML config file with environment variable substitution keeps secrets out of code and lets users customize behavior.

## What Changes

- Create `AppConfig` class that loads `~/.config/kafka-agent/config.yaml`
- Support `${ENV_VAR}` substitution in YAML values
- Create `default-config.yaml` resource with all config keys documented
- Config sections: `anthropic` (api-key, model, max-tokens, caching, thinking) and `confluent` (bootstrap, api-key, api-secret, flink settings)

## Non-goals

- No runtime config reloading — restart required for changes
- No GUI config editor

## Capabilities

### New Capabilities
- `yaml-config`: YAML configuration loading with environment variable substitution

### Modified Capabilities
