package com.ieum.gw.common.dao;

import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import com.ieum.gw.common.dto.AuthEmpDTO;
import com.ieum.gw.common.mapper.AuthMapper;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AuthDAO {

	private final AuthMapper mapper;

	public AuthEmpDTO getInfo(String compcode, String empnum) {
		return mapper.getInfo(compcode, empnum);
	}
	
	public int recordFail(String compcode, String empnum) {
		return mapper.recordFail(compcode, empnum);
	}
	
	public int resetFail(String compcode, String empnum) {
		return mapper.resetFail(compcode, empnum);
	}
}
