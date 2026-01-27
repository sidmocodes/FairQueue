package com.fairqueue.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.fairqueue.booking", "com.fairqueue.common"})
public class BookingGateApplication {
    public static void main(String[] args) {
        SpringApplication.run(BookingGateApplication.class, args);
    }
}
