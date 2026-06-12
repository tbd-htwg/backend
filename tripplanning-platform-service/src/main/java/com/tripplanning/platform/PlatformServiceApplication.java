package com.tripplanning.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
@SpringBootApplication(
    scanBasePackages = {"com.tripplanning.platform", "com.tripplanning.common.security"})
@ConfigurationPropertiesScan
public class PlatformServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(PlatformServiceApplication.class, args);
  }
}
