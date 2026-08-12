package com.agentjars.model;

import java.util.List;

/**
 * The metadata an {@code AGENT.md} front matter block carries.
 *
 * @param name        human readable agent name
 * @param description one paragraph describing when an orchestrator should reach for this agent
 * @param model       preferred model tier, e.g. {@code fast} or {@code reasoning}; may be null
 * @param tools       tool names the agent expects to have available
 * @param tags        free-form tags used for filtering in the registry
 * @param license     SPDX identifier resolved for the agent
 * @param homepage    canonical URL for the agent, usually its directory on GitHub
 */
public record AgentManifest(
        String name,
        String description,
        String model,
        List<String> tools,
        List<String> tags,
        String license,
        String homepage) {

    public AgentManifest {
        tools = tools == null ? List.of() : List.copyOf(tools);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public static AgentManifest named(String name) {
        return new AgentManifest(name, null, null, List.of(), List.of(), null, null);
    }

    public boolean hasDescription() {
        return description != null && !description.isBlank();
    }
}
