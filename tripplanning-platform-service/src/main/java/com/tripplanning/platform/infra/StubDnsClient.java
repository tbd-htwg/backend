package com.tripplanning.platform.infra;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class StubDnsClient implements DnsClient {

  @Override
  public void registerStandardSubdomain(String slug, String hostBase) {
    log.info("[stub] Register Standard subdomain {}.{} on shared LB", slug, hostBase);
  }

  @Override
  public void registerPremiumSubdomain(String slug, String hostBase) {
    log.info("[stub] Register Premium subdomain {}.{} on dedicated LB", slug, hostBase);
  }
}
