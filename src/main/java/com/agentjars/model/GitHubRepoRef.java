package com.agentjars.model;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A reference to a public GitHub repository, parsed from the many shapes people paste into the
 * deploy form: {@code org/repo}, an https clone url, an ssh clone url or a browser url.
 */
public record GitHubRepoRef(String org, String repo, String ref) {

    /**
     * The repository group is reluctant so that a trailing {@code .git} is consumed by the
     * optional suffix rather than becoming part of the repository name.
     */
    private static final Pattern SHORTHAND = Pattern.compile(
            "^(?:https?://(?:www\\.)?github\\.com/|git@github\\.com:)?"
                    + "([A-Za-z0-9][A-Za-z0-9._-]*?)/"
                    + "([A-Za-z0-9][A-Za-z0-9._-]*?)(?:\\.git)?"
                    + "(?:/(?:tree|commit)/([^/\\s]+))?/?$");

    public static GitHubRepoRef parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Repository is required");
        }
        Matcher matcher = SHORTHAND.matcher(input.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Expected a public GitHub repository such as 'org/repo' or "
                            + "'https://github.com/org/repo', got: " + input);
        }
        return new GitHubRepoRef(matcher.group(1), matcher.group(2), matcher.group(3));
    }

    public String slug() {
        return org + "/" + repo;
    }

    public String cloneUrl() {
        return "https://github.com/" + org + "/" + repo + ".git";
    }

    public String browseUrl() {
        return "https://github.com/" + org + "/" + repo;
    }

    public String normalisedOrg() {
        return org.toLowerCase(Locale.ROOT);
    }
}
