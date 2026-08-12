package com.agentjars.staticsite;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BasePathLinkBuilderTest {

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource(nullValues = "null", value = {
            "/AgentJars,   /AgentJars",
            "AgentJars,    /AgentJars",
            "/AgentJars/,  /AgentJars",
            "/AgentJars//, /AgentJars",
            "'  /docs  ',  /docs",
            "'',           ''",
            "/,            ''",
            "null,         ''",
    })
    @DisplayName("normalises whatever shape the base path arrives in")
    void normalises(String input, String expected) {
        assertThat(BasePathLinkBuilder.normalise(input)).isEqualTo(expected == null ? "" : expected);
    }
}
