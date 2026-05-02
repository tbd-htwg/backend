package com.tripplanning.search;

import org.hibernate.search.backend.lucene.analysis.LuceneAnalysisConfigurationContext;
import org.hibernate.search.backend.lucene.analysis.LuceneAnalysisConfigurer;

/**
 * Lucene backend does not ship a named {@code english} analyzer (unlike Elasticsearch). Entities use
 * {@code @FullTextField(analyzer = "english")}; this configurer supplies that definition for the local profile.
 */
public class LuceneEnglishAnalysisConfigurer implements LuceneAnalysisConfigurer {

  @Override
  public void configure(LuceneAnalysisConfigurationContext context) {
    context
        .analyzer("english")
        .custom()
        .tokenizer("standard")
        .charFilter("htmlStrip")
        .tokenFilter("lowercase")
        .tokenFilter("snowballPorter")
        .param("language", "English")
        .tokenFilter("asciiFolding");

    context.normalizer("lowercase").custom().tokenFilter("lowercase");
  }
}

