package com.jobbeacon.controller;

import com.jobbeacon.dto.response.ResponseGeneral;
import com.jobbeacon.dto.response.UserResponse;
import com.jobbeacon.entity.User;
import com.jobbeacon.security.CurrentUser;
import com.jobbeacon.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @GetMapping("/me")
  public ResponseGeneral<UserResponse> getCurrentUser(@CurrentUser User user) {
    return ResponseGeneral.ofSuccess("Get current user success", userService.toResponse(user));
  }
}
