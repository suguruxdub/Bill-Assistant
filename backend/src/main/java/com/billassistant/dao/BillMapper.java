package com.billassistant.dao;

import com.billassistant.model.BillEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BillMapper {

    List<BillEntity> findAll(@Param("uid") long uid);

    int insert(BillEntity bill);
}
