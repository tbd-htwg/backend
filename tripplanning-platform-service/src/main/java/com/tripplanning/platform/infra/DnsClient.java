package com.tripplanning.platform.infra;

public interface DnsClient {

  void registerStandardSubdomain(String slug, String hostBase);

  void registerPremiumSubdomain(String slug, String hostBase);
}
