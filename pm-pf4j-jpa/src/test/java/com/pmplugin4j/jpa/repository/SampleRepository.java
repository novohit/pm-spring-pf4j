package com.pmplugin4j.jpa.repository;

import com.pmplugin4j.jpa.entity.SampleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SampleRepository extends JpaRepository<SampleEntity, Long> {
}
