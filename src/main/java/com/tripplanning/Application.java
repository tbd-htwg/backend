package com.tripplanning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = {
    "com.tripplanning.trip",
    "com.tripplanning.user",
    "com.tripplanning.accommodation",
    "com.tripplanning.transport",
    "com.tripplanning.location",
    "com.tripplanning.tripLocation"
})
@EnableMongoRepositories(basePackages = "com.tripplanning.social")
public class Application {

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}

