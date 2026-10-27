package com.ieum.gw.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TestController {

	@GetMapping(value="/admin/test")
	public String login() {
		
		return "home";
	}
	
	@GetMapping("/it/test")
	public String it() {
	    return "home";
	}
}
