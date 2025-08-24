package nidam.registration.services;

import nidam.registration.proxy.ReCaptchaProxy;
import nidam.registration.services.dto.CaptchaResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.logging.Logger;

@Service
public class RecaptchaService {

	private final Logger log = Logger.getLogger(RecaptchaService.class.getName());

	@Value("${custom.recaptcha.secret}")
	private String recaptchaSecret;

	private final ReCaptchaProxy reCaptchaProxy;
	public RecaptchaService(ReCaptchaProxy reCaptchaProxy){
		this.reCaptchaProxy = reCaptchaProxy;
	}

	public boolean validateCaptcha(String key){
		MultiValueMap<String, String> requestMap = new LinkedMultiValueMap<>();
		requestMap.add("secret", recaptchaSecret);
		requestMap.add("response", key);

		CaptchaResponse captchaResponse = reCaptchaProxy.validateReCaptcha(requestMap);
//		log.info("captchaResponse.getSuccess(): " + captchaResponse.getSuccess());

		if(captchaResponse == null || captchaResponse.getSuccess() == null){
			return false;
		}
		return captchaResponse.getSuccess();
	}

}
