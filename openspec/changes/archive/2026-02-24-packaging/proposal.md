## Why

Users need a fast-starting, easy-to-install binary. GraalVM native-image or jlink reduces startup time from seconds to milliseconds and eliminates the JDK dependency for distribution.

## What Changes

- Add GraalVM native-image Gradle plugin as primary packaging option
- Add jlink configuration as fallback if native-image has compatibility issues
- Create startup shell script for PATH installation
- Document installation steps

## Non-goals

- No Homebrew formula or package manager distribution
- No cross-platform installers (Windows MSI, macOS DMG)
- No Docker image

## Capabilities

### New Capabilities
- `native-packaging`: GraalVM native-image or jlink packaging for fast startup and distribution

### Modified Capabilities
