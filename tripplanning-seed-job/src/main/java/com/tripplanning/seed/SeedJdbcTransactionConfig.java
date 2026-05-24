package com.tripplanning.seed;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Firestore starter registers a {@code ReactiveFirestoreTransactionManager} that becomes
 * primary and breaks {@link Transactional} on JDBC services. SQL seeding must use JDBC.
 */
@Configuration
public class SeedJdbcTransactionConfig {

    @Bean
    @Primary
    PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
