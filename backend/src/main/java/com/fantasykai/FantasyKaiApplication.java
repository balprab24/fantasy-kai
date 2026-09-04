package com.fantasykai;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class FantasyKaiApplication {

    public static void main(String[] args) {
        SpringApplication.run(FantasyKaiApplication.class, args);
    }

    /** Injected rather than called statically, so season boundaries are testable. */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
