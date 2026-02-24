package com.example.agent.agent;

import com.example.agent.tui.ErrorMessage;
import com.example.agent.tui.StreamCompleteMessage;
import com.example.agent.tui.StreamTokenMessage;
import com.example.agent.tui.ToolCompleteMessage;
import com.example.agent.tui.ToolExecutingMessage;
import com.williamcallahan.tui4j.compat.bubbletea.Program;
import dev.langchain4j.service.TokenStream;

/**
 * Bridges LangChain4j TokenStream callbacks to tui4j Program messages.
 * Converts onPartialResponse/onCompleteResponse/onError into custom Messages
 * sent via Program.send() from the callback thread.
 */
public class StreamBridge {

    private final Program program;
    private volatile AgentAssistant assistant;
    private final String sessionId = "default";

    public StreamBridge(Program program, AgentAssistant assistant) {
        this.program = program;
        this.assistant = assistant;
    }

    /**
     * Replace the assistant (e.g., after rebuilding with MCP tools).
     */
    public void setAssistant(AgentAssistant assistant) {
        this.assistant = assistant;
    }

    /**
     * Start a streaming chat. Tokens are sent as StreamTokenMessage,
     * completion as StreamCompleteMessage, errors as ErrorMessage.
     * Tool executions are surfaced via ToolExecutingMessage and ToolCompleteMessage.
     */
    public void sendMessage(String userInput) {
        TokenStream tokenStream = assistant.chat(sessionId, userInput);

        StringBuilder fullResponse = new StringBuilder();

        tokenStream
                .beforeToolExecution(beforeToolExecution -> {
                    program.send(new ToolExecutingMessage(beforeToolExecution.request().name()));
                })
                .onToolExecuted(toolExecution -> {
                    program.send(new ToolCompleteMessage(
                            toolExecution.request().name(),
                            toolExecution.result()));
                })
                .onPartialResponse(token -> {
                    fullResponse.append(token);
                    program.send(new StreamTokenMessage(token));
                })
                .onCompleteResponse(response -> {
                    program.send(new StreamCompleteMessage(fullResponse.toString()));
                })
                .onError(error -> {
                    program.send(new ErrorMessage(error.getMessage()));
                })
                .start();
    }
}
