package com.jhwjhw.naverworkscloneapi.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class HomeController {

	@GetMapping("/")
	public ModelAndView home() {
		// templates/index.html 화면을 선택해 브라우저에 반환.
		ModelAndView modelAndView = new ModelAndView("index");
		// Thymeleaf의 ${message} 위치에 전달할 화면 데이터.
		modelAndView.addObject("message", "Hello, Naver Works Clone API!");
		return modelAndView;
	}
}
