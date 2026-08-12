package com.agentjars.staticsite;

import java.util.Map;

import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.linkbuilder.StandardLinkBuilder;

/**
 * Resolves {@code @{/...}} links against a fixed base path.
 *
 * <p>Thymeleaf normally takes the prefix from the servlet context path, which does not exist when
 * pages are rendered ahead of time. A project site on GitHub Pages is served from
 * {@code /<repository>/}, so every absolute link needs that prefix baked in at build time.
 */
public class BasePathLinkBuilder extends StandardLinkBuilder {

    private final String basePath;

    /**
     * @param basePath the prefix to apply, with no trailing slash; empty for a site served from
     *                 the domain root
     */
    public BasePathLinkBuilder(String basePath) {
        this.basePath = normalise(basePath);
    }

    /** Trims whitespace and any trailing slash, and guarantees a leading one. */
    static String normalise(String basePath) {
        if (basePath == null) {
            return "";
        }
        String trimmed = basePath.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    @Override
    protected String computeContextPath(
            IExpressionContext context, String base, Map<String, Object> parameters) {
        return basePath;
    }
}
