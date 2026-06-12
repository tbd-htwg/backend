package com.tripplanning.platform.infra;

public interface LoadBalancerRegistrar {

  void registerStandardHost(String slug, String hostBase);

  void registerPremiumHost(String slug, String hostBase);
}
