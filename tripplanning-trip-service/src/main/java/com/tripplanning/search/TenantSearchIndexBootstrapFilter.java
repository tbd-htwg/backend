package com.tripplanning.search;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.tripplanning.common.tenant.TenantContext;
import com.tripplanning.common.tenant.TenantContextHolder;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Lazily mass-indexes each tenant's search index on first request when datasource routing is
 * enabled.
 */
@Component
@Order(25)
@ConditionalOnProperty(name = "tripplanning.tenant.datasource-routing.enabled", havingValue = "true")
@Slf4j
public class TenantSearchIndexBootstrapFilter extends OncePerRequestFilter {

  private final SearchIndexCoordinationService coordinationService;
  private final Set<String> scheduledTenants = ConcurrentHashMap.newKeySet();
  private final ExecutorService executor =
      Executors.newCachedThreadPool(
          r -> {
            Thread t = new Thread(r, "tenant-search-bootstrap");
            t.setDaemon(true);
            return t;
          });

  public TenantSearchIndexBootstrapFilter(SearchIndexCoordinationService coordinationService) {
    this.coordinationService = coordinationService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, java.io.IOException {
    String slug = TenantContextHolder.slugOrDefault();
    if (!"free".equals(slug) && scheduledTenants.add(slug)) {
      TenantContext context = TenantContextHolder.get();
      executor.submit(
          () -> {
            if (context != null) {
              TenantContextHolder.set(context);
            }
            try {
              coordinationService.ensureIndexPopulatedForCurrentTenant();
            } catch (Exception e) {
              scheduledTenants.remove(slug);
              log.warn("Tenant search bootstrap failed for {}: {}", slug, e.getMessage());
            } finally {
              TenantContextHolder.clear();
            }
          });
    }
    filterChain.doFilter(request, response);
  }
}
