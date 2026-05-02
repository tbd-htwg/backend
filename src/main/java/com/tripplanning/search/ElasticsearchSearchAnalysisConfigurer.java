package com.tripplanning.search;

import org.hibernate.search.backend.elasticsearch.analysis.ElasticsearchAnalysisConfigurationContext;
import org.hibernate.search.backend.elasticsearch.analysis.ElasticsearchAnalysisConfigurer;

/**
 * Elasticsearch keyword normalizers aligned with {@link LuceneEnglishAnalysisConfigurer} for local
 * development.
 */
public class ElasticsearchSearchAnalysisConfigurer implements ElasticsearchAnalysisConfigurer {

    @Override
    public void configure(ElasticsearchAnalysisConfigurationContext context) {
        context.normalizer("lowercase").custom().tokenFilters("lowercase");
    }
}
