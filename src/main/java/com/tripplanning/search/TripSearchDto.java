package com.tripplanning.search;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class TripSearchDto {
    private Long id;
    private String title;
    private String author;
    private String shortDescription;
    private List<String> locations;
}
