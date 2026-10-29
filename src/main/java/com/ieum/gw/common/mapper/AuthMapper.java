package com.ieum.gw.common.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.ieum.gw.common.dto.AuthEmpDTO;

@Mapper
public interface AuthMapper {

	AuthEmpDTO getInfo(String empnum);

}
