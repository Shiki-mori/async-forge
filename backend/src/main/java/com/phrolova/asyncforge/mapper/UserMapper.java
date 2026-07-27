package com.phrolova.asyncforge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.phrolova.asyncforge.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
