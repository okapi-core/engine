/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ddb;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.okapi.data.CreateDynamoDBTables;
import org.okapi.data.dao.TokenMetaDao;
import org.okapi.data.ddb.dao.TokenMetaDaoDdbImpl;
import org.okapi.data.model.TokenStatus;
import org.okapi.data.model.TokenMetadata;
import org.okapi.testutils.OkapiTestUtils;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;

public class TokenMetaDaoDdbImplIT {

  private TokenMetaDao dao;
  private String orgId;

  @BeforeEach
  public void setup() {
    CreateDynamoDBTables.createTables(OkapiTestUtils.getLocalStackDynamoDbClient());
    var enhanced =
        DynamoDbEnhancedClient.builder()
            .dynamoDbClient(OkapiTestUtils.getLocalStackDynamoDbClient())
            .build();
    dao = new TokenMetaDaoDdbImpl(enhanced);
    orgId = OkapiTestUtils.getTestId(getClass()) + ":" + UUID.randomUUID();
  }

  private TokenMetadata newToken(String tokenId, TokenStatus status) {
    var meta = new TokenMetadata();
    meta.setOrgId(orgId);
    meta.setTokenId(tokenId);
    meta.setTokenStatus(status);
    meta.setCreatedAt(System.currentTimeMillis());
    meta.setCreatorId("creator-" + tokenId);
    return meta;
  }

  @Test
  public void createAndGetTokenMetadata() {
    var tokenId = "t-" + UUID.randomUUID();
    var meta = newToken(tokenId, TokenStatus.ACTIVE);

    dao.createTokenMetadata(meta);

    var stored = dao.getTokenMetadata(orgId, tokenId);
    assertNotNull(stored);
    assertEquals(orgId, stored.getOrgId());
    assertEquals(tokenId, stored.getTokenId());
    assertEquals(TokenStatus.ACTIVE, stored.getTokenStatus());
    assertEquals("creator-" + tokenId, stored.getCreatorId());
  }

  @Test
  public void updateTokenStatusChangesStatus() {
    var tokenId = "update-" + UUID.randomUUID();
    dao.createTokenMetadata(newToken(tokenId, TokenStatus.ACTIVE));

    dao.updateTokenStatus(orgId, tokenId, TokenStatus.INACTIVE);

    var stored = dao.getTokenMetadata(orgId, tokenId);
    assertNotNull(stored);
    assertEquals(TokenStatus.INACTIVE, stored.getTokenStatus());
  }

  @Test
  public void listTokensByOrgAndStatusFiltersCorrectly() {
    var activeTokens = List.of("a1-" + UUID.randomUUID(), "a2-" + UUID.randomUUID());
    var inactiveTokens = List.of("i1-" + UUID.randomUUID());
    for (var id : activeTokens) {
      dao.createTokenMetadata(newToken(id, TokenStatus.ACTIVE));
    }
    for (var id : inactiveTokens) {
      dao.createTokenMetadata(newToken(id, TokenStatus.INACTIVE));
    }

    // add another org to ensure isolation
    var other = new TokenMetadata();
    other.setOrgId("other-org");
    other.setTokenId("other-token");
    other.setTokenStatus(TokenStatus.ACTIVE);
    dao.createTokenMetadata(other);

    var activeResult = dao.listTokensByOrgAndStatus(orgId, TokenStatus.ACTIVE);
    var ids = activeResult.stream().map(TokenMetadata::getTokenId).toList();
    assertTrue(ids.containsAll(activeTokens));
    assertFalse(ids.containsAll(inactiveTokens));
    assertFalse(ids.contains("other-token"));

    var inactiveResult = dao.listTokensByOrgAndStatus(orgId, TokenStatus.INACTIVE);
    var inactiveIds = inactiveResult.stream().map(TokenMetadata::getTokenId).toList();
    assertTrue(inactiveIds.containsAll(inactiveTokens));
    assertTrue(inactiveIds.stream().noneMatch(activeTokens::contains));
  }
}
