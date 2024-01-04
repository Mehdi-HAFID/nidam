package com.derbyware.registration.services.error;

import org.springframework.web.server.ResponseStatusException;

import java.util.logging.Logger;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

public class ReCaptchaException extends ResponseStatusException {

  private Logger log = Logger.getLogger(ReCaptchaException.class.getName());

  public ReCaptchaException(String message) {
    super(BAD_REQUEST, message);
    log.severe(message);
  }

}
