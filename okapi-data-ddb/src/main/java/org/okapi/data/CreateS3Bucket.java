/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data;

import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

@Slf4j
public class CreateS3Bucket {
  public static void createS3Bucket(S3Client s3Client, String s3Bucket) {
    var buckets = s3Client.listBuckets().buckets();
    var bucketExists = buckets.stream().anyMatch(b -> b.name().equals(s3Bucket));
    if (bucketExists) {
      log.info("S3 bucket {} already exists, skipping creation", s3Bucket);
      return;
    }
    log.info("Creating S3 bucket {}", s3Bucket);
    try {
      s3Client.createBucket(CreateBucketRequest.builder().bucket(s3Bucket).build());
    } catch (AwsServiceException e) {
      // skip if bucket already exists
      if (e.awsErrorDetails().errorCode().equals("BucketAlreadyOwnedByYou")) {
        log.info("S3 bucket {} already exists, skipping creation", s3Bucket);
      } else {
        throw e;
      }
    }
  }

  public static S3Client getS3Client(String endpoint, String region) {
    var credentials = EnvironmentVariableCredentialsProvider.create();
    return S3Client.builder()
        .endpointOverride(URI.create(endpoint))
        .region(Region.of(region))
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .credentialsProvider(credentials)
        .build();
  }

  public static void main(String[] args) {
    var env = args[0];
    var bucket = args[1];
    String endpoint =
        System.getenv().getOrDefault("OKAPI_AWS_ENDPOINT", "http://127.0.0.1:4566");

    if (env.equals("prod")) {
      endpoint = "https://s3.eu-west-2.amazonaws.com";
    }
    // var s3Client = getS3Client(endpoint, "eu-west-2");
    // createS3Bucket(s3Client, bucket);
  }
}
