package com.example.agent.agentic;

import com.example.agent.agentic.FlinkTypes.CriticResult;
import com.example.agent.agentic.FlinkTypes.FlinkStatement;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * The Flink specialist is itself a small workflow:
 * <pre>
 *   flink = sequence( loop( sqlAuthor → sqlCritic ) until criticResult.successful, sqlSubmitter )
 * </pre>
 * Scope keys: {@code task} (from the supervisor) → {@code statement}
 * (author) → {@code criticResult} (critic) → {@code flinkResult} (submitter).
 */
public final class FlinkAgents {

    private FlinkAgents() {
    }

    public static final String AUTHOR = "sqlAuthor";
    public static final String CRITIC = "sqlCritic";
    public static final String SUBMITTER = "sqlSubmitter";

    /** Writes (or rewrites) the statement. Read-only Flink tools + the flink-sql skill. */
    public interface SqlAuthor {
        @UserMessage("""
                Task:
                {{task}}

                Previous review of your last attempt (ignore if it says nothing was reviewed yet):
                {{criticResult}}

                Produce the statement.""")
        @Agent(name = AUTHOR, outputKey = "statement",
               description = "Writes a Flink SQL statement for the task after inspecting the tables.")
        FlinkStatement write(@V("task") String task, @V("criticResult") CriticResult criticResult);
    }

    /** Verifies the statement against DESCRIBE output and the Confluent Flink rules. */
    public interface SqlCritic {
        @UserMessage("""
                Review this Flink SQL statement strictly. Verify every column against DESCRIBE.

                {{statement}}""")
        @Agent(name = CRITIC, outputKey = "criticResult",
               description = "Verifies a Flink SQL statement and returns a typed pass/fail verdict with feedback.")
        CriticResult verify(@V("statement") FlinkStatement statement);
    }

    /** Submits the approved statement and confirms it is running. Write tools. */
    public interface SqlSubmitter {
        @UserMessage("""
                Submit this approved Flink SQL statement, then verify it is RUNNING.

                {{statement}}""")
        @Agent(name = SUBMITTER, outputKey = "flinkResult",
               description = "Submits a reviewed Flink SQL statement and verifies it is running.")
        String submit(@V("statement") FlinkStatement statement);
    }
}
