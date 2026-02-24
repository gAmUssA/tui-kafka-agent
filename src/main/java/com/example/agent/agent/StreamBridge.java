package com.example.agent.agent;

import com.example.agent.tui.ErrorMessage;
import com.example.agent.tui.StreamCompleteMessage;
import com.example.agent.tui.StreamTokenMessage;
import com.williamcallahan.tui4j.compat.bubbletea.Program;
import dev.langchain4j.service.TokenStream;

/**
 * Bridges LangChain4j TokenStream callbacks to tui4j Program messages.
 * Converts onPartialResponse/onCompleteResponse/onError into custom Messages
 * sent via Program.send() from the callback thread.
 */
public class StreamBridge {

    private final Program program;
    private final AgentAssistant assistant;
    private final String sessionId = "default";

    public StreamBridge(Program program, AgentAssistant assistant) {
        this.program = program;
        this.assistant = assistant;
    }

    /**
     * Start a streaming chat. Tokens are sent as StreamTokenMessage,
     * completion as StreamCompleteMessage, errors as ErrorMessage.
     */
    public void sendMessage(String userInput) {
        TokenStream tokenStream = assistant.chat(sessionId, userInput);

        StringBuilder fullResponse = new StringBuilder();

        tokenStream
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
