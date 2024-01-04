package com.derbyware.registration.controllers;

import com.derbyware.registration.services.RecaptchaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.logging.Logger;

@RestController
public class RecaptchaController {

	private Logger log = Logger.getLogger(RecaptchaController.class.getName());

	private RecaptchaService recaptchaService;

	public RecaptchaController(RecaptchaService recaptchaService){
		this.recaptchaService = recaptchaService;
	}

	@PostMapping("recaptcha")
	public ResponseEntity<Boolean> checkKey(@RequestBody String key) {
//		log.info("captcha key: " + key);
		boolean result = recaptchaService.validateCaptcha(key);
		return ResponseEntity.ok()
//				.headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, key))
				.body(result);
	}
}
