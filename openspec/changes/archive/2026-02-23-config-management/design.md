## Context

The app needs credentials for Anthropic API and Confluent Cloud. Users should store these in a config file rather than passing them as CLI args every time.

## Goals / Non-Goals

**Goals:**
- Load YAML config from `~/.config/kafka-agent/config.yaml`
- Substitute `${ENV_VAR}` patterns with actual environment variables
- Provide typed access to config values via `AppConfig`

**Non-Goals:**
- No CLI arg parsing — config file only
- No config file creation wizard

## Decisions

**SnakeYAML for parsing**: Already a project dependency. Simple, no annotations required. Parse to `Map<String, Object>` and wrap with typed accessors.

**XDG-style config path**: `~/.config/kafka-agent/config.yaml` follows Linux/macOS conventions.

**Env var substitution via regex**: Simple `\\$\\{([^}]+)\\}` regex replacement before YAML parsing. No nested substitution needed.

**Fail-fast on missing required config**: If `anthropic.api-key` is unset, exit with a clear error message rather than failing later on first API call.

## Risks / Trade-offs

- [Config file doesn't exist on first run] → Fall back to env vars only; print helpful message about creating config
