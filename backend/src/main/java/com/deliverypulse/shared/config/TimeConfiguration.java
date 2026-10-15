package com.deliverypulse.shared.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfiguration {

    /**
     * The zone decides which calendar date is "today" when no analysisDate is supplied.
     * Timestamps are still stored and returned as UTC instants.
     */
    @Bean
    Clock systemClock(@Value("${app.time-zone:UTC}") String timeZone) {
        return Clock.system(ZoneId.of(timeZone));
    }
}
