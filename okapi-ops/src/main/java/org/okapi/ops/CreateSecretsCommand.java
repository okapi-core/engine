/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ops;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.ResourceExistsException;

import java.net.URI;
import java.security.SecureRandom;
import java.util.concurrent.Callable;

@Slf4j
@Command(
    name = "create-secrets",
    description = "Create Secrets required by okapi",
    header = "Example: okapi-ops create-secrets --env local --endpoint http://127.0.0.1:4566")
public class CreateSecretsCommand implements Callable<Integer> {
  private static final String SECRET_NAME = "/okapi/secrets";
  private static final String DESCRIPTION = "Okapi test secrets";
  private static final int SECRET_BYTES = 16;

  @Option(names = "--endpoint", description = "Secrets manager endpoint override.", required = true)
  private String endpoint;

  @Option(names = "--region", description = "AWS region.", defaultValue = "eu-west-2")
  private String region;

  @Override
  public Integer call() {
    EndpointWaiter.waitForEndpoint(endpoint);
    try (var secretsManager = buildClient()) {
      var secretString = buildSecretString();
      var request =
          CreateSecretRequest.builder()
              .name(SECRET_NAME)
              .description(DESCRIPTION)
              .secretString(secretString)
              .build();
      try {
        secretsManager.createSecret(request);
      } catch (ResourceExistsException e) {
        log.info("Secret already exists. Moving on.");
      }
    }
    return 0;
  }

  private SecretsManagerClient buildClient() {
    return SecretsManagerClient.builder()
        .endpointOverride(URI.create(endpoint))
        .region(Region.of(region))
        .build();
  }

  private static String buildSecretString() {
    var hmacKey = randomHex(SECRET_BYTES);
    var apiKey = randomHex(SECRET_BYTES);
    return "{\"hmacKey\":\"" + hmacKey + "\",\"apiKey\":\"" + apiKey + "\"}";
  }

  private static String randomHex(int bytes) {
    var data = new byte[bytes];
    new SecureRandom().nextBytes(data);
    var out = new StringBuilder(bytes * 2);
    for (byte b : data) {
      out.append(String.format("%02x", b));
    }
    return out.toString();
  }
}
