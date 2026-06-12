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
      case ENTERPRISE -> enterpriseSteps(0, null);
      default -> List.of();
    };
  }

  public static List<TenantDtos.ProvisioningStepDto> standardSteps(
      int doneThrough, String runningOrFailedKey) {
    List<TenantDtos.ProvisioningStepDto> steps = new ArrayList<>();
    steps.add(step("registry", "Registry entry", doneThrough, 0, runningOrFailedKey));
    steps.add(
        step("identity_platform", "Identity Platform tenant", doneThrough, 1, runningOrFailedKey));
    steps.add(
        step(
            "terraform_infra",
            "Terraform DNS + DB + secrets",
            doneThrough,
            2,
            runningOrFailedKey));
    steps.add(
        step("gitops", "API router + tenant config", doneThrough, 3, runningOrFailedKey));
    steps.add(
        step("search_index", "Search index bootstrap", doneThrough, 4, runningOrFailedKey));
    return steps;
  }

  public static List<TenantDtos.ProvisioningStepDto> enterpriseSteps(
      int doneThrough, String runningOrFailedKey) {
    List<TenantDtos.ProvisioningStepDto> steps = new ArrayList<>();
    steps.add(step("registry", "Registry entry", doneThrough, 0, runningOrFailedKey));
    steps.add(
        step("identity_platform", "Identity Platform tenant", doneThrough, 1, runningOrFailedKey));
    steps.add(
        step(
            "terraform_infra",
            "Terraform DNS + Cloud SQL + bucket",
            doneThrough,
            2,
            runningOrFailedKey));
    steps.add(
        step("gitops", "Namespace + HelmRelease + LB", doneThrough, 3, runningOrFailedKey));
    steps.add(step("database", "Dedicated Postgres ready", doneThrough, 4, runningOrFailedKey));
    steps.add(
        step("search_index", "Dedicated OpenSearch ready", doneThrough, 5, runningOrFailedKey));
    steps.add(
        step("gcp_resources", "Firestore + GCS bucket", doneThrough, 6, runningOrFailedKey));
    return steps;
  }

  public static List<TenantDtos.ProvisioningStepDto> completed(TenantTier tier) {
    return switch (tier) {
      case STANDARD -> standardSteps(5, null);
      case ENTERPRISE -> enterpriseSteps(7, null);
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
