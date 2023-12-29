package com.derbyware.registration.repositories;

import com.derbyware.registration.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigInteger;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, BigInteger> {

	Optional<User> findUserByEmail(String email);
}