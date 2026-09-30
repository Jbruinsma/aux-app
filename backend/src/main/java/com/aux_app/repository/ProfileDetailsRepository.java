package com.aux_app.repository;

import com.aux_app.entity.ProfileDetailsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileDetailsRepository extends JpaRepository<ProfileDetailsEntity, String> {

    ProfileDetailsEntity findByUserId(String userId);

}
