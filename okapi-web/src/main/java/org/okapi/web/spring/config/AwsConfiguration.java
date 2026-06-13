/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.spring.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

@Configuration
public class AwsConfiguration {
  @Bean
  public AwsCredentialsProvider credentialsProvider(
      @Autowired AwsConfigurationValues configurationValues) {
    if (configurationValues.getCreds().equals("env")) {
      return EnvironmentVariableCredentialsProvider.create();
    } else if (configurationValues.getCreds().equals("instance-profile")) {
      return InstanceProfileCredentialsProvider.create();
    } else
      throw new MisconfiguredException(
          "Value "
              + configurationValues.getCreds()
              + " is not recognized, should be one of env or instance-profile");
  }

  @Bean
  public SecretsManagerClient prodSecretsManagerClient(
      @Autowired AwsCredentialsProvider credentialsProvider,
      @Autowired AwsConfigurationValues configurationValues) {
    return SecretsManagerClient.builder()
        .endpointOverride(URI.create(configurationValues.getEndpoint()))
        .region(Region.of(configurationValues.getRegion()))
        .credentialsProvider(credentialsProvider)
        .build();
  }

  @Bean
  public S3Client s3ClientProd(
      @Autowired AwsCredentialsProvider credentialsProvider,
      @Autowired AwsConfigurationValues configurationValues) {
    return S3Client.builder()
        .endpointOverride(URI.create(configurationValues.getEndpoint()))
        .region(Region.of(configurationValues.getRegion()))
        .credentialsProvider(credentialsProvider)
        .build();
  }

}
