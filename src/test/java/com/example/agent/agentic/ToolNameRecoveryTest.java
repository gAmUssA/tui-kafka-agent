package com.example.agent.agentic;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolNameRecoveryTest {

    @Test
    void snakeCaseGuessIsPointedAtTheKebabCaseTool() {
        var request = ToolExecutionRequest.builder().id("call-1").name("describe_flink_table").arguments("{}").build();
        var reply = ToolNameRecovery.reply(request);
        assertEquals("call-1", reply.id());
        assertTrue(reply.text().contains("try 'describe-flink-table'"), reply.text());
    }

    @Test
    void unknownNameWithoutUnderscoreGetsNoSpellingHint() {
        String text = ToolNameRecovery.message("teleport");
        assertTrue(text.startsWith("Error: there is no tool named 'teleport'"), text);
        assertFalse(text.contains("try '"), text);
    }
}
