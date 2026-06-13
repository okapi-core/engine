/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.spring.configs;

import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.okapi.spring.configs.properties.AwsCfg;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;

@Slf4j
@Profile(Profiles.PROFILE_OKAPI_ENGINE)
@Configuration
public class AwsConfiguration {
  @Bean
  public AwsCredentialsProvider credentialsProvider() {
    return EnvironmentVariableCredentialsProvider.create();
  }

  @Bean
  @Profile("test")
  public AwsCredentialsProvider credentialsProviderLocalstack() {
    log.info("Using Localstack AWS Credentials Provider");
    return new AwsCredentialsProvider() {
      @Override
      public AwsCredentials resolveCredentials() {
        return new AwsCredentials() {
          @Override
          public String accessKeyId() {
            return "test-key";
          }

          @Override
          public String secretAccessKey() {
            return "test-secret";
          }
        };
      }
    };
  }

  @Bean
  public S3Client s3Client(
      @Autowired AwsCredentialsProvider credentialsProvider, @Autowired AwsCfg awsCfg) {
    var client =
        S3Client.builder()
            .credentialsProvider(credentialsProvider)
            .region(Region.of(awsCfg.getRegion()));

    if (awsCfg.getEndpoint() != null && !awsCfg.getEndpoint().isBlank()) {
      log.info("Using custom S3 endpoint: {}", awsCfg.getEndpoint());
      client.endpointOverride(URI.create(awsCfg.getEndpoint()));
      log.info("Using path style access for custom S3 endpoint");
      client.forcePathStyle(true);
    }
    return client.build();
  }

  @Bean
  public DynamoDbClient dynamoDbClient(
      @Autowired AwsCredentialsProvider credentialsProvider, @Autowired AwsCfg awsCfg) {
    var builder =
        DynamoDbClient.builder()
            .region(Region.of(awsCfg.getRegion()))
            .credentialsProvider(credentialsProvider);
    if (awsCfg.getEndpoint() != null && !awsCfg.getEndpoint().isBlank()) {
      log.info("Using custom DynamoDB endpoint: {}", awsCfg.getEndpoint());
      builder.endpointOverride(URI.create(awsCfg.getEndpoint()));
    }
    return builder.build();
  }

  @Bean
  public DynamoDbEnhancedClient enhancedClient(@Autowired DynamoDbClient client) {
    return DynamoDbEnhancedClient.builder().dynamoDbClient(client).build();
  }
}
