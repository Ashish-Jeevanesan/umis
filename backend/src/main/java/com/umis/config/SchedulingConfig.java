package com.umis.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Task scheduler configuration for background mailbox synchronization and outbox relay.
 */
@Configuration
public class SchedulingConfig {

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10);
        scheduler.setThreadNamePrefix("umis-scheduler-");
        scheduler.setErrorHandler(throwable ->
                org.slf4j.LoggerFactory.getLogger(SchedulingConfig.class)
                        .error("Scheduled background task error", throwable));
        scheduler.initialize();
        return scheduler;
    }
}
