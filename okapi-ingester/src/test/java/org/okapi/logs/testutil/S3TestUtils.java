/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.logs.testutil;

import java.net.URI;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

public final class S3TestUtils {
  private S3TestUtils() {}

  public static S3Client createS3Client() {
    return S3Client.builder()
        .endpointOverride(
            URI.create(
                System.getenv()
                    .getOrDefault(
                        "OKAPI_TEST_LOCALSTACK_ENDPOINT", "http://127.0.0.1:4566")))
        .region(Region.EU_WEST_2)
        .forcePathStyle(true)
        .overrideConfiguration(ClientOverrideConfiguration.builder().build())
        .build();
  }

  /** Create bucket if missing; no-op if it already exists. */
  public static void ensureBucketExists(S3Client s3, String bucket) {
    try {
      s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
    } catch (S3Exception e) {
      // 409 Conflict means bucket already exists (or you already own it)
      if (e.statusCode() != 409) throw e;
    }
  }
}
