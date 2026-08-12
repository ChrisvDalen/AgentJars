package com.agentjars;

import com.agentjars.config.AgentJarsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
@EnableConfigurationProperties(AgentJarsProperties.class)
public class AgentJarsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentJarsApplication.class, args);
    }
}
