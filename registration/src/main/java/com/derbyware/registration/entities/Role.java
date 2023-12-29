package com.derbyware.registration.entities;

import jakarta.persistence.*;

import java.util.Objects;

@Entity
@Table(name = "ROLES")
public class Role {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String name;

	public Role() {
	}

	public Role(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Role authority = (Role) o;
		return Objects.equals(id, authority.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}
}
