package com.derbyware.registration.services;

import com.derbyware.registration.proxy.ReCaptchaProxy;
import com.derbyware.registration.services.dto.CaptchaResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.logging.Logger;

@Service
public class RecaptchaService {

	private Logger log = Logger.getLogger(RecaptchaService.class.getName());

	private ReCaptchaProxy reCaptchaProxy;
	public RecaptchaService(ReCaptchaProxy reCaptchaProxy){
		this.reCaptchaProxy = reCaptchaProxy;
	}

	public boolean validateCaptcha(String key){
		MultiValueMap<String, String> requestMap = new LinkedMultiValueMap<>();
		requestMap.add("secret", "6LcyyEMpAAAAANm0qZB6kzDQRT5Li4FSGCTe8ESf");
		requestMap.add("response", key);

		CaptchaResponse captchaResponse = reCaptchaProxy.validateReCaptcha(requestMap);
//		log.info("captchaResponse.getSuccess(): " + captchaResponse.getSuccess());

		if(captchaResponse == null || captchaResponse.getSuccess() == null){
			return false;
		}
		return captchaResponse.getSuccess();
	}

}
