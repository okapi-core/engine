/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.spring.config;

import org.okapi.data.dao.ResultUploader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3ResultUploaderConfiguration {
  @Bean
  ResultUploader resultUploader(S3Client s3Client, S3Cfg s3Cfg) {
    return new S3ResultUploader(s3Client, s3Cfg.getBucket(), s3Cfg.getResultsPrefix());
  }
}
