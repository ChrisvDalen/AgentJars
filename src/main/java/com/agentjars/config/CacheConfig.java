package com.agentjars.config;

import com.agentjars.catalog.CatalogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * In-memory caching for the catalog.
 *
 * <p>The registry is read-mostly and its upstream is a public repository, so a plain map cache
 * with a scheduled sweep is enough; nothing here needs to survive a restart. The whole catalog is
 * fetched as one unit, so it expires as one unit rather than per entry.
 */
@Configuration
@EnableScheduling
public class CacheConfig {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(CatalogService.CATALOG_CACHE, CatalogService.JAR_CACHE);
    }

    @Bean
    CatalogExpiry catalogExpiry() {
        return new CatalogExpiry();
    }

    /** Clears the catalog caches once the configured time-to-live elapses. */
    public static class CatalogExpiry {

        @Scheduled(
                fixedRateString = "${agentjars.cache.catalog-ttl:PT1H}",
                initialDelayString = "${agentjars.cache.catalog-ttl:PT1H}")
        @CacheEvict(
                value = {CatalogService.CATALOG_CACHE, CatalogService.JAR_CACHE},
                allEntries = true)
        public void expire() {
            log.debug("Catalog cache expired on schedule");
        }
    }
}
