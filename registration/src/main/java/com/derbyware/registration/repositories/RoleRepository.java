package com.derbyware.registration.repositories;

import com.derbyware.registration.entities.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
	public Role findRoleByName(String name);
}
