package com.tripplanning.api.controller;

import com.tripplanning.api.dto.UserCreateRequest;
import com.tripplanning.api.dto.UserResponse;
import com.tripplanning.user.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
}

