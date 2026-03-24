package com.tripplanning.trip;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TripRepository extends JpaRepository<TripEntity, Long> {
  List<TripEntity> findByUserId(Long userId);
  void deleteByUserId(Long userId);
}

