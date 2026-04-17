package com.tripplanning.search;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.tripplanning.trip.TripEntity;

@Component 
@RequiredArgsConstructor
@Slf4j
public class SearchIndexInitializer implements CommandLineRunner {

    private final EntityManager entityManager;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("Elasticsearch-Indizierung wird gestartet...");
        
        SearchSession searchSession = Search.session(entityManager);

        searchSession.massIndexer(TripEntity.class)
            .threadsToLoadObjects(4) // Parallelisierung für Tempo
            .startAndWait(); // Wartet, bis alles fertig ist
            
        log.info("Elasticsearch ist bereit! DB Einträge wurden indiziert.");
    }
}