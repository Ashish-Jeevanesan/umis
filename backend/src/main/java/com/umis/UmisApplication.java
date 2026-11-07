package com.umis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Universal Mailbox Integration Service (UMIS)
 * Standalone, production-oriented, multi-tenant mailbox integration microservice.
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class UmisApplication {

    public static void main(String[] args) {
        SpringApplication.run(UmisApplication.class, args);
    }
}
