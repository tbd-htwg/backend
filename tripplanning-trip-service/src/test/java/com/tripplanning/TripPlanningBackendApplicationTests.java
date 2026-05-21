package com.tripplanning;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import org.springframework.context.annotation.Import;

import com.tripplanning.TripServiceApplication;

@SpringBootTest(classes = TripServiceApplication.class)
@Import(TestClientsConfig.class)
@ActiveProfiles("test")
class TripPlanningBackendApplicationTests {

  @Test
  void contextLoads() {}
}

