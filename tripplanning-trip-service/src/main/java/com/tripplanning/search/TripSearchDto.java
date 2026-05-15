package com.tripplanning.search;

import java.time.LocalDate;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TripSearchDto {
    private Long id;
    private Long userId;
    private String title;
    private String author;
    private String shortDescription;
    private String destination;
    private LocalDate startDate;
    private List<String> locations;
    private List<String> accommodationNames;
    private List<String> transportTypes;
}
