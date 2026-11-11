package com.po.processor.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring task scheduling for the database-backed worker.
 */
@Configuration
@EnableScheduling
public class PoSchedulingConfig {
}
