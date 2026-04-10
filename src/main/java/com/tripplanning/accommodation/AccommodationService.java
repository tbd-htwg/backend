package com.tripplanning.accommodation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripplanning.api.dto.request.AccomRequest;
import com.tripplanning.api.dto.response.AccomResponse;

import lombok.RequiredArgsConstructor;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AccommodationService {
    private final AccomRepository accomRepository;

    @Transactional
    public AccomResponse getOrCreateAccommodation(AccomRequest request) {
        AccomEntity accommodation = accomRepository
            .findByName(request.name())
            .orElseGet(() -> accomRepository.save(
                new AccomEntity(request.name(), request.type(), request.address())
            ));

        return new AccomResponse(
            accommodation.getAccom_id(), 
            accommodation.getName(), 
            accommodation.getType(),
            accommodation.getAddress()
        );
    }

    @Transactional(readOnly = true)
    public List<AccomResponse> searchAccommodations(String query) {
        return accomRepository.findByNameContaining(query).stream()
            .map(a -> new AccomResponse(a.getAccom_id(), a.getName(), a.getType(), a.getAddress()))
            .toList();
    }


}