package com.ieum.gw.auth.repository;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.ieum.gw.auth.model.AuthEmpDto;

@Repository          
public class AuthDAO {

    @Autowired
    private SqlSessionTemplate template;     

	public AuthEmpDto getInfo(String empnum) {
	    return template.selectOne("auth.getInfo", empnum);
	}
}