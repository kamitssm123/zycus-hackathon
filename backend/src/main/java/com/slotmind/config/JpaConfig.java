package com.slotmind.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Enables @CreatedDate / @LastModifiedDate on entities. */
@Configuration
@EnableJpaAuditing
public class JpaConfig {}
