package com.example.agent.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/**
 * LangChain4j AI Service interface for streaming chat with Anthropic Claude.
 */
public interface AgentAssistant {

  TokenStream chat(@MemoryId String sessionId, @UserMessage String message);
}
