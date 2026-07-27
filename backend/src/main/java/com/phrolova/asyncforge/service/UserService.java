package com.phrolova.asyncforge.service;

import com.phrolova.asyncforge.dto.request.LoginRequest;
import com.phrolova.asyncforge.dto.request.RegisterRequest;
import com.phrolova.asyncforge.dto.response.LoginResponse;

public interface UserService {

    LoginResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);
}
