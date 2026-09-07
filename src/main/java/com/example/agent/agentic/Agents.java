package com.example.agent.agentic;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * The agent interfaces of the brewery system. Each specialist takes one
 * self-contained {@code task} written by the supervisor's planner; the
 * planner reads the {@code @Agent} descriptions to decide who does what.
 * <p>
 * Scope keys: {@code request} (root input — this exact name is what the
 * supervisor's planner reads, so it must not be renamed) → {@code supervisorResult}
 * (supervisor output) → {@code answer} (presenter output). Specialists take a
 * planner-written {@code task} and write {@code brewPlan}, {@code platformResult},
 * {@code flinkResult}.
 */
public final class Agents {

    private Agents() {
    }

    public static final String BREWMASTER = "brewmaster";
    public static final String CONFLUENT = "confluent";
    public static final String FLINK = "flink";
    public static final String FLINK_OPS = "flinkOps";
    public static final String SUPERVISOR = "brewops";
    public static final String PRESENTER = "presenter";
    public static final String ROOT = "brewery";

    /** Craft-brewing domain expert. No platform tools. */
    public interface BrewmasterAgent {
        @UserMessage("{{task}}")
        @Agent(name = BREWMASTER, outputKey = "brewPlan",
               description = "Craft-brewing domain expert: fermentation science, style-specific "
                       + "temperature/pH/gravity ranges, generates realistic sensor readings (JSON lines), "
                       + "defines anomaly thresholds and windows, diagnoses fermentation problems and "
                       + "gives corrective advice. Has NO access to Kafka, Schema Registry or Flink.")
        String advise(@V("task") String task);
    }

    /** Kafka / Schema Registry / governance operator on Confluent Cloud. */
    public interface ConfluentAgent {
        @UserMessage("{{task}}")
        @Agent(name = CONFLUENT, outputKey = "platformResult",
               description = "Operates Confluent Cloud: creates/lists/deletes Kafka topics, manages tags, "
                       + "registers and lists Avro schemas in Schema Registry, produces and consumes "
                       + "messages (given the exact records to produce), inspects clusters and environments. "
                       + "Does not write Flink SQL and does not invent brewing data.")
        String operate(@V("task") String task);
    }

    /**
     * Flink SQL specialist. Composed workflow (see {@link FlinkAgents}):
     * author → critic loop until the typed verdict passes, then submitter.
     */
    public interface FlinkAgent {
        @Agent(name = FLINK, outputKey = "flinkResult",
               description = "Flink SQL specialist: inspects Flink tables (DESCRIBE), writes windowed "
                       + "aggregation and anomaly-detection statements, has them reviewed, submits them "
                       + "and verifies they are RUNNING. Needs the thresholds, window length and "
                       + "source/sink topics in the task.")
        String build(@V("task") String task);
    }

    /** Manages statements that already exist: list, status, health, exceptions, delete. */
    public interface FlinkOpsAgent {
        @UserMessage("{{task}}")
        @Agent(name = FLINK_OPS, outputKey = "flinkOpsResult",
               description = "Flink operations: lists running statements, reads their status, health, "
                       + "exceptions and profiles, and deletes statements by name. Does NOT write SQL.")
        String operate(@V("task") String task);
    }

    /** LLM-planned coordinator over the specialists. */
    public interface BrewOpsSupervisor {
        @Agent(name = SUPERVISOR, outputKey = "supervisorResult",
               description = "Coordinates brewmaster, confluent and flink specialists to fulfil a request.")
        String plan(@V("request") String request);
    }

    /** Streams the audience-facing answer; must be the last agent in the system. */
    public interface Presenter {
        @UserMessage("""
                The user asked:
                {{request}}

                What the team did (supervisor summary):
                {{supervisorResult}}

                Write the answer for the user.""")
        @Agent(name = PRESENTER, outputKey = "answer",
               description = "Narrates the outcome to the user.")
        TokenStream present(@V("request") String request,
                            @V("supervisorResult") String supervisorResult);
    }

    /**
     * Root of the system: {@code sequence(supervisor, presenter)}. The
     * {@code @MemoryId} makes the {@code AgenticScope} persistent per session so
     * state survives across demo turns; {@code evictAgenticScope} clears it.
     */
    public interface BrewerySystem extends AgenticScopeAccess {
        @Agent(name = ROOT, outputKey = "answer",
               description = "Brewery monitoring team: brewing expertise plus Confluent Cloud and Flink operations.")
        TokenStream chat(@MemoryId String session, @V("request") String request);
    }
}
