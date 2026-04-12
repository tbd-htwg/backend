package com.tripplanning.user;

import com.tripplanning.comment.CommentEntity;
import com.tripplanning.comment.CommentRepository;
import com.tripplanning.location.LocationEntity;
import com.tripplanning.location.LocationRepository;
import com.tripplanning.trip.TripEntity;
import com.tripplanning.trip.TripRepository;
import com.tripplanning.tripLocation.TripLocationEntity;
import com.tripplanning.tripLocation.TripLocationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class UserServiceDeletionTest {

  private static final String TEST_IMAGE_URL = "https://example.com/avatar.png";
  private static final String TEST_DESCRIPTION = "Test user";

  @Autowired
  private UserService userService;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private TripRepository tripRepository;

  @Autowired
  private CommentRepository commentRepository;

  @Autowired
  private TripLocationRepository tripLocationRepository;

  @Autowired
  private LocationRepository locationRepository;

  private UserEntity newUser(String email, String name) {
    return new UserEntity(email, name, TEST_IMAGE_URL, TEST_DESCRIPTION);
  }

  private TripEntity newTrip(UserEntity owner, String title) {
    return new TripEntity(
        owner,
        title,
        "Somewhere",
        LocalDate.parse("2026-06-01"),
        "Short",
        "Long description");
  }

  @Test
  void deleteUserRemovesOwnedTripsCommentsLikesAndStopsButKeepsSharedLocations() {
    UserEntity userA = userRepository.save(newUser("user-a-deletion@example.com", "User A"));
    UserEntity userB = userRepository.save(newUser("user-b-deletion@example.com", "User B"));

    TripEntity tripOwnedByA = tripRepository.save(newTrip(userA, "Trip A"));
    TripEntity tripOwnedByB = tripRepository.save(newTrip(userB, "Trip B"));

    userA.getLikedTrips().add(tripOwnedByB);
    userRepository.save(userA);

    commentRepository.save(new CommentEntity(userA, tripOwnedByB, "A comments on B trip"));
    commentRepository.save(new CommentEntity(userB, tripOwnedByA, "B comments on A trip"));

    LocationEntity location =
        locationRepository.save(new LocationEntity("loc-deleteUserCascade-" + UUID.randomUUID()));
    tripLocationRepository.save(new TripLocationEntity(tripOwnedByA, location, null, null));

    Long userAId = userA.getUserId();
    Long tripAId = tripOwnedByA.getTripId();
    Long tripBId = tripOwnedByB.getTripId();
    long locationId = location.getLocation_id();

    assertThat(tripRepository.countLikes(tripBId)).isEqualTo(1);
    assertThat(tripLocationRepository.findByTrip_TripId(tripAId)).hasSize(1);
    assertThat(commentRepository.findByTrip_TripIdOrderByCreatedAtDesc(tripAId)).hasSize(1);
    assertThat(commentRepository.findByTrip_TripIdOrderByCreatedAtDesc(tripBId)).hasSize(1);

    userService.deleteUser(userAId);

    assertThat(userRepository.findById(userAId)).isEmpty();
    assertThat(tripRepository.findByUser_UserId(userAId)).isEmpty();
    assertThat(tripRepository.findById(tripAId)).isEmpty();
    assertThat(tripLocationRepository.findByTrip_TripId(tripAId)).isEmpty();
    assertThat(commentRepository.findByTrip_TripIdOrderByCreatedAtDesc(tripAId)).isEmpty();
    assertThat(commentRepository.findByTrip_TripIdOrderByCreatedAtDesc(tripBId)).isEmpty();

    assertThat(tripRepository.findById(tripBId)).isPresent();
    assertThat(tripRepository.countLikes(tripBId)).isZero();
    assertThat(tripRepository.findByUser_UserId(userB.getUserId())).hasSize(1);
    assertThat(locationRepository.findById(locationId)).isPresent();
  }
}
