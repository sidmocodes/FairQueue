package com.fairqueue.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.fairqueue.gateway", "com.fairqueue.common"})
public class AdmissionGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdmissionGatewayApplication.class, args);
    }
}
