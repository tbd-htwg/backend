package com.tripplanning.config;

import jakarta.persistence.EntityManagerFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.transaction.TransactionManagerCustomizers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spring Cloud GCP registers {@link org.springframework.transaction.ReactiveTransactionManager}
 * for Firestore before JPA auto-configuration runs. Boot's JPA config declares its
 * {@code PlatformTransactionManager} only when <em>no</em> {@link org.springframework.transaction.TransactionManager}
 * bean exists, so the Firestore reactive manager prevents creation of {@code JpaTransactionManager}.
 * That breaks Spring Data REST (expects a {@code transactionManager} bean) and lets {@code @Transactional}
 * on servlet controllers pick the reactive manager. This bean restores the JPA transaction manager as primary.
 */
@Configuration
public class JpaPrimaryTransactionManagerConfig {

  @Bean(name = "transactionManager")
  @Primary
  public PlatformTransactionManager transactionManager(
      EntityManagerFactory entityManagerFactory,
      ObjectProvider<TransactionManagerCustomizers> customizers) {
    JpaTransactionManager tm = new JpaTransactionManager(entityManagerFactory);
    customizers.ifAvailable(c -> c.customize(tm));
    return tm;
  }
}
