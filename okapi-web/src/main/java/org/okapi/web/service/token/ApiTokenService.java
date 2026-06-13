/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.token;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.okapi.data.dao.TokenMetaDao;
import org.okapi.data.model.TokenStatus;
import org.okapi.data.model.TokenMetadata;
import org.okapi.exceptions.NotFoundException;
import org.okapi.web.auth.AccessManager;
import org.okapi.web.auth.ApiTokenManager;
import org.okapi.web.auth.TokenManager;
import org.okapi.web.dtos.token.ApiToken;
import org.okapi.web.dtos.token.GetTokenResponse;
import org.okapi.web.dtos.token.ListTokensResponse;
import org.okapi.web.dtos.token.UpdateTokenRequest;
import org.okapi.web.service.Mappers;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class ApiTokenService {

  TokenMetaDao tokenMetaDao;
  AccessManager accessManager;
  TokenManager tokenManager;
  ApiTokenManager apiTokenManager;

  public ListTokensResponse listTokens(String tempToken) {
    var context = accessManager.checkUserIsOrgMember(tokenManager.getAuthContext(tempToken));
    var orgTokens = tokenMetaDao.listTokensByOrgAndStatus(context.orgId(), TokenStatus.ACTIVE);
    var filterByCreator =
        orgTokens.stream()
            .filter(t -> t.getCreatorId().equals(context.userId()))
            .map(Mappers::mapTokenMetaToResponse)
            .toList();

    return new ListTokensResponse(filterByCreator);
  }

  public GetTokenResponse updateToken(String tempToken, UpdateTokenRequest updateTokenRequest)
      throws NotFoundException {
    var context = accessManager.checkUserIsOrgMember(tokenManager.getAuthContext(tempToken));
    var tokenId = updateTokenRequest.getTokenId();
    var tokenMeta = tokenMetaDao.getTokenMetadata(context.orgId(), tokenId);
    if (tokenMeta == null || !tokenMeta.getCreatorId().equals(context.userId())) {
      throw new NotFoundException("Token not found");
    }

    tokenMetaDao.updateTokenStatus(
        context.orgId(), tokenId, TokenStatus.valueOf(updateTokenRequest.getStatus()));
    var updatedTokenMeta = tokenMetaDao.getTokenMetadata(context.orgId(), tokenId);
    return Mappers.mapTokenMetaToResponse(updatedTokenMeta);
  }

  public ApiToken createApiSourceToken(String tempToken) {
    var context = accessManager.checkUserIsOrgMember(tokenManager.getAuthContext(tempToken));
    var newToken =
        apiTokenManager.createApiToken(
            context.orgId(), List.of(Permissions.AGENT_JOBS_UPDATE, Permissions.AGENT_JOBS_READ));

    var metadata = new TokenMetadata();
    metadata.setOrgId(context.orgId());
    metadata.setCreatorId(context.userId());
    metadata.setTokenId(UUID.randomUUID().toString());
    metadata.setCreatedAt(System.currentTimeMillis());
    metadata.setTokenStatus(TokenStatus.ACTIVE);
    tokenMetaDao.createTokenMetadata(metadata);

    return new ApiToken(newToken);
  }
}
