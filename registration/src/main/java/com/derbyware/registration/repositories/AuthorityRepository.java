package com.derbyware.registration.repositories;

import com.derbyware.registration.entities.Authority;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuthorityRepository extends JpaRepository<Authority, Long> {
	public Authority findAuthorityByName(String name);
}
