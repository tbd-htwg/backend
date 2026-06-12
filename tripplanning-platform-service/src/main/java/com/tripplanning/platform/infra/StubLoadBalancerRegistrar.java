package com.tripplanning.platform.infra;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class StubLoadBalancerRegistrar implements LoadBalancerRegistrar {

  @Override
  public void registerStandardHost(String slug, String hostBase) {
    log.info("[stub] Add host {}.{} to shared Standard GCP LB", slug, hostBase);
  }

  @Override
  public void registerPremiumHost(String slug, String hostBase) {
    log.info("[stub] Provision dedicated Premium GCP LB for {}.{}", slug, hostBase);
  }
}
