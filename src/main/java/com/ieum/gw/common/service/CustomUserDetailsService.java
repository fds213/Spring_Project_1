package com.ieum.gw.common.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.ieum.gw.common.dao.AuthDAO;
import com.ieum.gw.common.dto.AuthEmpDTO;
import com.ieum.gw.common.dto.LoginUser;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private AuthDAO dao;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        AuthEmpDTO emp = dao.getInfo(username);

        if (emp == null) {
            throw new UsernameNotFoundException("존재하지 않는 사번: " + username);
        }

        return new LoginUser(emp);
    }
}