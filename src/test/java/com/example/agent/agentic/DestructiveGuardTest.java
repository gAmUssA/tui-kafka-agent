package com.example.agent.agentic;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DestructiveGuardTest {

    @AfterEach
    void clear() {
        DestructiveGuard.beginTurn(null);
    }

    @Test
    void readOnlyQuestionHidesDeleteToolsFromFlinkOps() {
        // The rehearsal question that deleted the running alert statement.
        DestructiveGuard.beginTurn("Did the alerts fire?");
        assertFalse(ToolScopes.FLINK_OPS.test("delete-flink-statements"));
        assertTrue(ToolScopes.FLINK_OPS.test("list-flink-statements"));
    }

    @Test
    void readOnlyQuestionHidesDeleteToolsFromConfluent() {
        DestructiveGuard.beginTurn("What's the current state of all fermenters?");
        assertFalse(ToolScopes.CONFLUENT.test("delete-topics"));
        assertFalse(ToolScopes.CONFLUENT.test("remove-tag-from-entity"));
        assertTrue(ToolScopes.CONFLUENT.test("consume-messages"));
    }

    @Test
    void demoResetPromptOffersDeleteTools() {
        DestructiveGuard.beginTurn("Reset the brewery demo. First have flinkOps delete every Flink statement");
        assertTrue(ToolScopes.FLINK_OPS.test("delete-flink-statements"));
        assertTrue(ToolScopes.CONFLUENT.test("delete-topics"));
    }

    @Test
    void explicitRemovalRequestOffersDeleteTools() {
        DestructiveGuard.beginTurn("please remove the brewery-alerts topic");
        assertTrue(ToolScopes.CONFLUENT.test("delete-topics"));
    }

    @Test
    void guardNeverWidensAScope() {
        DestructiveGuard.beginTurn("delete everything");
        assertFalse(ToolScopes.FLINK_OPS.test("delete-topics"));
    }
}
