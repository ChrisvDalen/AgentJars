package com.agentjars.staticsite;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Runs the static site generator from the command line and exits.
 *
 * <pre>{@code
 * java -jar agentjars.jar --agentjars.static.output=build/site --agentjars.static.base-path=/AgentJars
 * }</pre>
 *
 * <p>The application starts without a web server for this: nothing here serves requests.
 */
@Component
@ConditionalOnProperty("agentjars.static.output")
public class StaticSiteRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StaticSiteRunner.class);

    private final StaticSiteGenerator generator;
    private final ApplicationContext context;
    private final String output;
    private final String basePath;

    public StaticSiteRunner(
            StaticSiteGenerator generator,
            ApplicationContext context,
            @Value("${agentjars.static.output}") String output,
            @Value("${agentjars.static.base-path:}") String basePath) {
        this.generator = generator;
        this.context = context;
        this.output = output;
        this.basePath = basePath;
    }

    @Override
    public void run(ApplicationArguments args) {
        int written = generator.generate(Path.of(output), basePath);
        log.info("Static site ready: {} files", written);

        // Generating is the whole job. The scheduler that expires the catalog cache runs on a
        // non-daemon thread, so the JVM has to be told to stop rather than being left to idle.
        System.exit(SpringApplication.exit(context, () -> 0));
    }
}
