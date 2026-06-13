/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.datasources;

import com.google.common.base.Enums;
import java.time.Instant;
import java.util.List;
import org.apache.commons.lang3.RandomStringUtils;
import org.okapi.data.dao.FederatedSourceRepo;
import org.okapi.data.model.FederatedSource;
import org.okapi.data.exceptions.ResourceNotFoundException;
import org.okapi.exceptions.BadRequestException;
import org.okapi.exceptions.UnAuthorizedException;
import org.okapi.web.ai.tools.SOURCE_TYPE;
import org.okapi.web.auth.AccessManager;
import org.okapi.web.auth.TokenManager;
import org.okapi.web.dtos.sources.CreateFederatedSourceRequest;
import org.okapi.web.dtos.sources.GetFederatedSourceResponse;
import org.okapi.web.service.AbstractValidatedCrudService;
import org.okapi.web.service.Mappers;
import org.okapi.web.service.ProtectedResourceContext;
import org.springframework.stereotype.Service;

@Service
public class FederatedSourceService
    extends AbstractValidatedCrudService<
        ProtectedResourceContext,
        FederatedResourceContext,
        CreateFederatedSourceRequest,
        Void,
        GetFederatedSourceResponse> {

  FederatedSourceRepo federatedSourceRepo;
  AccessManager accessManager;
  TokenManager tokenManager;

  public FederatedSourceService(
      FederatedSourceValidator validator,
      FederatedSourceRepo federatedSourceRepo,
      AccessManager accessManager,
      TokenManager tokenManager) {
    super(validator);
    this.federatedSourceRepo = federatedSourceRepo;
    this.accessManager = accessManager;
    this.tokenManager = tokenManager;
  }

  @Override
  public GetFederatedSourceResponse createAfterValidation(
      FederatedResourceContext resourceContext, CreateFederatedSourceRequest federatedSourceRequest)
      throws UnAuthorizedException {
    var token = RandomStringUtils.secure().nextAlphanumeric(10);
    var orgId = resourceContext.getMemberContext().getOrgId();
    var parsedEnum = Enums.getIfPresent(SOURCE_TYPE.class, federatedSourceRequest.getSourceType());
    if (!parsedEnum.isPresent()) {
      throw new BadRequestException(
          "Unrecognized source type: " + federatedSourceRequest.getSourceType());
    }
    var source =
        FederatedSource.builder()
            .orgId(orgId)
            .sourceName(federatedSourceRequest.getSourceName())
            .sourceType(parsedEnum.get().getName())
            .registrationToken(token)
            .created(Instant.now())
            .build();
    federatedSourceRepo.createSource(source);
    return Mappers.mapFederatedSourceDtoToResponse(source);
  }

  public List<GetFederatedSourceResponse> listAll(String tempToken) throws UnAuthorizedException {
    var userId = tokenManager.getUserId(tempToken);
    var orgId = tokenManager.getOrgId(tempToken);
    accessManager.checkUserHasIsOrgAdmin(userId, orgId);
    var sources = federatedSourceRepo.getAllSources(orgId);
    return sources.stream().map(Mappers::mapFederatedSourceDtoToResponse).toList();
  }

  @Override
  public GetFederatedSourceResponse readAfterValidation(FederatedResourceContext resourceContext)
      throws ResourceNotFoundException {
    var orgId = resourceContext.getMemberContext().getOrgId();
    var sourceName = resourceContext.getSourceId();
    var source = federatedSourceRepo.getSource(orgId, sourceName);
    if (source.isEmpty()) {
      throw new ResourceNotFoundException("Federated source not found: " + sourceName);
    }
    return Mappers.mapFederatedSourceDtoToResponse(source.get());
  }

  @Override
  public GetFederatedSourceResponse updateAfterValidation(
      FederatedResourceContext resourceContext, Void request) throws Exception {
    throw new UnAuthorizedException("Updating federated sources is not supported");
  }

  @Override
  public void deleteAfterValidation(FederatedResourceContext resourceContext)
      throws UnAuthorizedException {
    var orgId = resourceContext.getMemberContext().getOrgId();
    var sourceName = resourceContext.getSourceId();
    var userId = resourceContext.getMemberContext().getUserId();
    accessManager.checkUserHasIsOrgAdmin(userId, orgId);
    federatedSourceRepo.deleteSource(orgId, sourceName);
  }
}
