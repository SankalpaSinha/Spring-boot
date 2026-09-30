package io.pointscore.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on @Scheduled. Without this the expiry job is a method nobody calls. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
