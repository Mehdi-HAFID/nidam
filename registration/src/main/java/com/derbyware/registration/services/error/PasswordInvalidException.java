package com.derbyware.registration.services.error;

import org.springframework.web.server.ResponseStatusException;

import java.util.logging.Logger;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

public class PasswordInvalidException extends ResponseStatusException {

  private Logger log = Logger.getLogger(PasswordInvalidException.class.getName());

  public PasswordInvalidException(String message) {
    super(BAD_REQUEST, message);
    log.severe(message);
  }

}
