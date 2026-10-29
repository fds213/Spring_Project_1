package com.ieum.gw.common.dao;

import org.springframework.stereotype.Repository;

import com.ieum.gw.common.dto.AuthEmpDTO;
import com.ieum.gw.common.mapper.AuthMapper;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AuthDAO {

	private final AuthMapper mapper;

	public AuthEmpDTO getInfo(String empnum) {
		return mapper.getInfo(empnum);
	}
}
