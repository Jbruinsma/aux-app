package com.aux.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aux.entity.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity, String> {

    boolean existsByUsername(String username);
}