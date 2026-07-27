package com.phrolova.asyncforge.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.phrolova.asyncforge.mapper")
public class MybatisConfig {
}
