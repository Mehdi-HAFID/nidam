package com.derbyware.registration.services.error;

import org.springframework.web.server.ResponseStatusException;

import java.util.logging.Logger;

import static java.lang.String.format;
import static org.springframework.http.HttpStatus.CONFLICT;

public class AlreadyExistException extends ResponseStatusException {

  private Logger log = Logger.getLogger(AlreadyExistException.class.getName());

  public AlreadyExistException(String message) {
    super(CONFLICT, message);
    log.severe(message);
  }

  public AlreadyExistException(String entity, String label) {
    super(CONFLICT, format("%s already exist with same name [%s]", entity, label));
    log.severe(format("%s already exist with same name [%s]", entity, label));
  }

}
