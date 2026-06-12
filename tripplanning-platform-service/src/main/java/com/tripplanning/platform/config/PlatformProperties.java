package com.tripplanning.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "tripplanning.platform")
public class PlatformProperties {

  private String hostBase = "k8s.tbd-htwg.de";
  private String enterpriseHostBase = "enterprise.k8s.tbd-htwg.de";
  private String bootstrapAdminEmail = "admin@platform.demo";
  private Provisioning provisioning = new Provisioning();
  private Github github = new Github();
  private StandardPostgres standardPostgres = new StandardPostgres();

  @Getter
  @Setter
  public static class Provisioning {
    private boolean useStubs = true;
  }

  @Getter
  @Setter
  public static class Github {
    private String dispatchUrl = "";
    private String dispatchToken = "";
    private String standardEventType = "tenant-created-standard";
    private String enterpriseEventType = "tenant-created-enterprise";
  }

  @Getter
  @Setter
  public static class StandardPostgres {
    private String jdbcUrl = "";
    private String username = "";
    private String password = "";
  }
}
