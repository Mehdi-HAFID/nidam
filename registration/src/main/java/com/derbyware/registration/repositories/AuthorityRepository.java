package com.derbyware.registration.repositories;

import com.derbyware.registration.entities.Authority;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigInteger;

@Repository
public interface AuthorityRepository extends JpaRepository<Authority, BigInteger> {
	public Authority findAuthorityByName(String name);
}
