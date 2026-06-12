package com.tripplanning.platform.provisioning;

import java.util.ArrayList;
import java.util.List;

import com.tripplanning.platform.tenant.TenantDtos;
import com.tripplanning.platform.tenant.TenantTier;

public final class ProvisioningStepDefinitions {

  private ProvisioningStepDefinitions() {}

  public static List<TenantDtos.ProvisioningStepDto> initialSteps(TenantTier tier) {
    return switch (tier) {
      case STANDARD -> standardSteps(0, null);
      case PREMIUM -> premiumSteps(0, null);
      default -> List.of();
    };
  }

  public static List<TenantDtos.ProvisioningStepDto> standardSteps(
      int doneThrough, String runningOrFailedKey) {
    List<TenantDtos.ProvisioningStepDto> steps = new ArrayList<>();
    steps.add(step("registry", "Registry entry", doneThrough, 0, runningOrFailedKey));
    steps.add(step("database", "Create database", doneThrough, 1, runningOrFailedKey));
    steps.add(step("search_index", "Create search index", doneThrough, 2, runningOrFailedKey));
    return steps;
  }

  public static List<TenantDtos.ProvisioningStepDto> premiumSteps(
      int doneThrough, String runningOrFailedKey) {
    List<TenantDtos.ProvisioningStepDto> steps = new ArrayList<>();
    steps.add(step("registry", "Registry entry", doneThrough, 0, runningOrFailedKey));
    steps.add(
        step(
            "entry_routing",
            "Identity Platform + DNS + load balancer",
            doneThrough,
            1,
            runningOrFailedKey));
    steps.add(step("gitops", "Flux GitOps namespace", doneThrough, 2, runningOrFailedKey));
    steps.add(step("database", "Dedicated Postgres", doneThrough, 3, runningOrFailedKey));
    steps.add(step("search_index", "Dedicated OpenSearch", doneThrough, 4, runningOrFailedKey));
    steps.add(
        step("gcp_resources", "Firestore + GCS bucket", doneThrough, 5, runningOrFailedKey));
    return steps;
  }

  public static List<TenantDtos.ProvisioningStepDto> completed(TenantTier tier) {
    return switch (tier) {
      case STANDARD -> standardSteps(3, null);
      case PREMIUM -> premiumSteps(6, null);
      default -> List.of();
    };
  }

  private static TenantDtos.ProvisioningStepDto step(
      String key, String label, int doneThrough, int index, String failedKey) {
    String status;
    if (failedKey != null && failedKey.equals(key)) {
      status = "failed";
    } else if (index < doneThrough) {
      status = "done";
    } else if (index == doneThrough) {
      status = "running";
    } else {
      status = "pending";
    }
    return new TenantDtos.ProvisioningStepDto(key, label, status);
  }
}
