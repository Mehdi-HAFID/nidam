package com.derbyware.registration.proxy;

import com.derbyware.registration.services.dto.CaptchaResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "recaptcha", url = "https://www.google.com/recaptcha/api/siteverify")
public interface ReCaptchaProxy {

	@PostMapping
	public CaptchaResponse validateReCaptcha(@RequestBody MultiValueMap<String, String> requestMap);
}
