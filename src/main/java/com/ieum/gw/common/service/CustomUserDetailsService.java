package com.ieum.gw.common.service;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.ieum.gw.common.dao.AuthDAO;
import com.ieum.gw.common.dto.AuthEmpDTO;
import com.ieum.gw.common.dto.LoginUser;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private AuthDAO dao;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        
        HttpServletRequest request =
            ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();

        
        String compcode = request.getParameter("companyCode");

        AuthEmpDTO emp = dao.getInfo(compcode, username);

        if (emp == null) {
            throw new UsernameNotFoundException("존재하지 않는 계정: " + compcode + " / " + username);
        }

        return new LoginUser(emp);
    }
}