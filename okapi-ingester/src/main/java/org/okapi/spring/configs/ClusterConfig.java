/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.spring.configs;

import org.okapi.identity.WhoAmI;
import org.okapi.nodes.Ec2IpSupplier;
import org.okapi.nodes.FixedIpSupplier;
import org.okapi.nodes.IpSupplier;
import org.okapi.nodes.NodeIdSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile(Profiles.PROFILE_OKAPI_ENGINE)
public class ClusterConfig {

  @Bean
  public WhoAmI defaultWhoAmI(
      @Value("${server.port}") Integer serverPort,
      @Autowired NodeIdSupplier nodeIdSupplier,
      @Autowired IpSupplier ipSupplier) {
    return new WhoAmI() {
      @Override
      public String getNodeId() {
        return nodeIdSupplier.getNodeId();
      }

      @Override
      public String getNodeIp() {
        return ipSupplier.getIp();
      }

      @Override
      public int getNodePort() {
        return serverPort;
      }
    };
  }

  @Bean
  @Profile("test")
  public IpSupplier ipSupplier() {
    return new FixedIpSupplier(
        System.getenv().getOrDefault("OKAPI_NODE_IP", "127.0.0.1"));
  }

  @Bean
  @Profile("ec2")
  public IpSupplier ec2IpSupplier() {
    return new Ec2IpSupplier();
  }
}
