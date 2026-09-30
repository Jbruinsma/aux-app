package com.aux_app.repository;

import com.aux_app.entity.OtpCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OtpCodeRepository extends JpaRepository<OtpCodeEntity, String> {
}
