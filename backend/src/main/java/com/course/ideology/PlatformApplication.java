package com.course.ideology;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.course.ideology")
@EnableScheduling
public class PlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }
}

