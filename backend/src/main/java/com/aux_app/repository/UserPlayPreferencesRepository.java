package com.aux_app.repository;

import com.aux_app.entity.UserPlayPreferencesEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPlayPreferencesRepository extends JpaRepository<UserPlayPreferencesEntity, String> {
}
