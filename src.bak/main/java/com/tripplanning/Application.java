package com.tripplanning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import com.google.cloud.spring.data.firestore.repository.config.EnableReactiveFirestoreRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = {
    "com.tripplanning.trip",
    "com.tripplanning.user",
    "com.tripplanning.accommodation",
    "com.tripplanning.transport",
    "com.tripplanning.location",
    "com.tripplanning.tripLocation"
})
@EnableReactiveFirestoreRepositories(basePackages = "com.tripplanning.social")
public class Application {

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}

