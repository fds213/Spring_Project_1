package com.ieum.gw.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ieum.gw.dto.AuthEmpDTO;

@Mapper
public interface AuthMapper {

	AuthEmpDTO getInfo(@Param("compcode") String compcode, @Param("empnum") String empnum);
	int recordFail(@Param("compcode") String compcode, @Param("empnum") String empnum);
	int resetFail(@Param("compcode") String compcode, @Param("empnum") String empnum);
	Integer getLockMinutes(@Param("compcode") String compcode, @Param("empnum") String empnum);
}
