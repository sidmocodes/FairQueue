package com.fairqueue.admission;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.fairqueue.admission", "com.fairqueue.common"})
public class AdmissionSlotServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdmissionSlotServiceApplication.class, args);
    }
}
