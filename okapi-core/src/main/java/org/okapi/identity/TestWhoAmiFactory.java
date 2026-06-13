/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.identity;

public class TestWhoAmiFactory {

  public static final WhoAmI DEFAULT = makeWhoAmI("default-whoami", "127.0.0.1", 8080);

  public static WhoAmI makeWhoAmI(String nodeId, String ip, int port) {
    return new WhoAmI() {
      @Override
      public String getNodeId() {
        return nodeId;
      }

      @Override
      public String getNodeIp() {
        return ip;
      }

      @Override
      public int getNodePort() {
        return port;
      }
    };
  }

  public static WhoAmI defaultTestWhoAmi() {
    return DEFAULT;
  }

  public static Member defaultMyself() {
    return new Member(DEFAULT.getNodeId(), DEFAULT.getNodeIp(), DEFAULT.getNodePort());
  }
}
