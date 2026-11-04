package com.ieum.gw.dao;

import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import com.ieum.gw.dto.AuthEmpDTO;
import com.ieum.gw.mapper.AuthMapper;

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
	
	public int getLockMinutes(String compcode, String empnum) {
		return mapper.getLockMinutes(compcode, empnum);
	}
}
