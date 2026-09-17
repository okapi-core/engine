/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.spring.configs.ch;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import lombok.*;
import org.okapi.spring.configs.ConfigSections;
import org.okapi.spring.configs.Profiles;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(ConfigSections.CH_CONFIG)
@Component
@Profile(Profiles.PROFILE_CH)
@Validated
@Data
@NoArgsConstructor
public class ChConfig {

  @NotNull String host;

  @Min(value = 1, message = "port should be non-zero.")
  int port;

  boolean secure;

  @NotBlank(message = "ch username should be provided.")
  @NotNull
  String userName;

  String password;

  Path chMetricsWal;

  WalConfig chMetricsWalCfg;

  @NotNull(message = "ch logs wal path should be specified.")
  Path chLogsWal;

  @NotNull(message = "ch logs wal config should be specified.")
  WalConfig chLogsCfg;

  @NotNull(message = "ch traces wal path should be specified.")
  Path chTracesWal;

  @NotNull(message = "ch traces wal config should be specified.")
  WalConfig chTracesWalCfg;

  @Data
  public static class WalConfig {
    long segmentSize = 1024 * 1024;
  }
}
