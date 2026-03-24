package com.tripplanning.api.controller;

import com.tripplanning.api.dto.UserCreateRequest;
import com.tripplanning.api.dto.UserDetailsResponse;
import com.tripplanning.api.dto.UserPatchRequest;
import com.tripplanning.api.dto.UserPutRequest;
import com.tripplanning.api.dto.UserResponse;
import com.tripplanning.user.UserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class UserController {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @PostMapping("/users")
  public ResponseEntity<UserResponse> register(@Valid @RequestBody UserCreateRequest request) {
    UserResponse created = userService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @GetMapping("/users")
  public ResponseEntity<List<UserResponse>> listUsers() {
    return ResponseEntity.ok(userService.listUsers());
  }

  @GetMapping("/users/{id}")
  public ResponseEntity<UserDetailsResponse> getUserById(@PathVariable Long id) {
    return ResponseEntity.ok(userService.getUserByIdWithTrips(id));
  }

  @PutMapping("/users/{id}")
  public ResponseEntity<UserResponse> replaceUser(
      @PathVariable Long id,
      @Valid @RequestBody UserPutRequest request
  ) {
    return ResponseEntity.ok(userService.replaceUser(id, request));
  }

  @PatchMapping("/users/{id}")
  public ResponseEntity<UserResponse> updateUserPartially(
      @PathVariable Long id,
      @Valid @RequestBody UserPatchRequest request
  ) {
    return ResponseEntity.ok(userService.patchUser(id, request));
  }

  @DeleteMapping("/users/{id}")
  public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
    userService.deleteUser(id);
    return ResponseEntity.noContent().build();
  }
}

