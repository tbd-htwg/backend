package com.tripplanning.customfield;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.tripplanning.common.auth.AppJwtDecoderConfiguration;
import com.tripplanning.common.auth.AuthProperties;
import com.tripplanning.common.client.RestTripServiceClient;
import com.tripplanning.common.client.ServiceClientConfig;
import com.tripplanning.common.client.TripServiceClient;
import com.tripplanning.common.config.ServiceClientProperties;

@SpringBootApplication(scanBasePackages = {"com.tripplanning.customfield", "com.tripplanning.common"})
@EnableConfigurationProperties({AuthProperties.class, ServiceClientProperties.class})
@Import({ServiceClientConfig.class, AppJwtDecoderConfiguration.class})
public class CustomFieldServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(CustomFieldServiceApplication.class, args);
  }

  @Bean
  TripServiceClient tripServiceClient(
      org.springframework.web.client.RestTemplate serviceRestTemplate,
      ServiceClientProperties properties) {
    return new RestTripServiceClient(serviceRestTemplate, properties);
  }
}
