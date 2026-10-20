package com.aux_app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// Async and scheduling are for live events: LiveEventService.onUpdate runs off the request thread, heartbeat on a timer
@EnableAsync
@EnableScheduling
@SpringBootApplication
public class AuxApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuxApplication.class, args);
    }
}
