package com.agentjars.deploy;

import java.util.List;

import com.agentjars.model.Coordinates;

/**
 * The outcome of a deployment run.
 *
 * @param repository the repository that was packaged, as {@code org/repo}
 * @param commit     the commit that was packaged
 * @param packaged   agents that produced artifacts
 * @param skipped    agents that were found but not packaged, with the reason
 */
public record DeployResult(
        String repository,
        String commit,
        List<PackagedAgent> packaged,
        List<SkippedAgent> skipped) {

    public DeployResult {
        packaged = packaged == null ? List.of() : List.copyOf(packaged);
        skipped = skipped == null ? List.of() : List.copyOf(skipped);
    }

    public boolean isEmpty() {
        return packaged.isEmpty();
    }

    public int total() {
        return packaged.size() + skipped.size();
    }

    /**
     * @param coordinates coordinates the agent was published under
     * @param agentPath   path of the agent inside the source repository
     * @param license     resolved SPDX identifier
     * @param bundlePath  local path of the upload bundle
     */
    public record PackagedAgent(
            Coordinates coordinates, String agentPath, String license, String bundlePath) {
    }

    /**
     * @param agentPath path of the agent inside the source repository
     * @param reason    why the agent was not packaged
     */
    public record SkippedAgent(String agentPath, String reason) {
    }
}
