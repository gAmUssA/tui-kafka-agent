## ADDED Requirements

### Requirement: StreamBridge sends tokens as Messages
`StreamBridge` SHALL convert `TokenStream.onNext()` callbacks into `StreamTokenMessage` sent via `Program.send()`.

#### Scenario: Token received
- **WHEN** a token arrives from TokenStream
- **THEN** `Program.send(new StreamTokenMessage(token))` is called

### Requirement: StreamBridge sends completion
`StreamBridge` SHALL send `StreamCompleteMessage` when the TokenStream completes.

#### Scenario: Stream completes
- **WHEN** `TokenStream.onComplete()` fires
- **THEN** `Program.send(new StreamCompleteMessage(fullResponse))` is called

### Requirement: StreamBridge sends errors
`StreamBridge` SHALL send `ErrorMessage` when the TokenStream errors.

#### Scenario: Stream error
- **WHEN** `TokenStream.onError()` fires
- **THEN** `Program.send(new ErrorMessage(error.getMessage()))` is called
