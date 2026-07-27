package com.phrolova.asyncforge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.phrolova.asyncforge.auth.JwtUtil;
import com.phrolova.asyncforge.auth.UserContext;
import com.phrolova.asyncforge.common.ErrorCode;
import com.phrolova.asyncforge.dto.request.LoginRequest;
import com.phrolova.asyncforge.dto.request.RegisterRequest;
import com.phrolova.asyncforge.dto.response.LoginResponse;
import com.phrolova.asyncforge.entity.User;
import com.phrolova.asyncforge.exception.BusinessException;
import com.phrolova.asyncforge.mapper.UserMapper;
import com.phrolova.asyncforge.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public LoginResponse register(RegisterRequest request) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        userMapper.insert(user);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return new LoginResponse(token, user.getId(), user.getUsername());
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return new LoginResponse(token, user.getId(), user.getUsername());
    }
}
