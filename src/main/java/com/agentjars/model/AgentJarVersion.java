package com.agentjars.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A published version of an AgentJar.
 *
 * <p>Versions follow {@code YYYY_MM_DD-<short-commit>}: the date the agent was packaged plus the
 * commit it was packaged from. That keeps versions sortable while still pointing at an exact
 * revision of the source repository.
 *
 * @param version   the version string
 * @param published when the version was published to Maven Central
 * @param commit    the commit hash embedded in the version, when it can be parsed
 */
public record AgentJarVersion(String version, Instant published, String commit) {

    private static final Pattern VERSION_PATTERN =
            Pattern.compile("^(\\d{4})_(\\d{2})_(\\d{2})-([0-9a-fA-F]{7,40})$");

    private static final DateTimeFormatter VERSION_DATE = DateTimeFormatter.ofPattern("yyyy_MM_dd");

    public static AgentJarVersion of(String version, Instant published) {
        return new AgentJarVersion(version, published, parseCommit(version).orElse(null));
    }

    /** Builds the version string used for a packaging run. */
    public static String format(LocalDate date, String commitHash) {
        String shortHash = commitHash == null ? "unknown" : commitHash.substring(0, Math.min(7, commitHash.length()));
        return VERSION_DATE.format(date) + "-" + shortHash;
    }

    public static Optional<String> parseCommit(String version) {
        if (version == null) {
            return Optional.empty();
        }
        Matcher matcher = VERSION_PATTERN.matcher(version);
        return matcher.matches() ? Optional.of(matcher.group(4)) : Optional.empty();
    }

    /** The packaging date encoded in the version, when the version follows the convention. */
    public Optional<LocalDate> packagedOn() {
        if (version == null) {
            return Optional.empty();
        }
        Matcher matcher = VERSION_PATTERN.matcher(version);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(LocalDate.of(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3))));
    }

    public boolean hasCommit() {
        return commit != null && !commit.isBlank();
    }
}
