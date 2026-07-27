package com.phrolova.asyncforge.controller;

import com.phrolova.asyncforge.common.Result;
import com.phrolova.asyncforge.dto.request.LoginRequest;
import com.phrolova.asyncforge.dto.request.RegisterRequest;
import com.phrolova.asyncforge.dto.response.LoginResponse;
import com.phrolova.asyncforge.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(userService.register(request));
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(userService.login(request));
    }
}
