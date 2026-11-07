package com.billassistant.dao;

import com.billassistant.model.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    UserEntity findByUsername(@Param("username") String username);

    UserEntity findById(@Param("id") long id);

    int insert(UserEntity user);
}
