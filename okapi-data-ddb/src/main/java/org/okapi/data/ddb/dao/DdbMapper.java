/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.ddb.dao;

import java.util.List;
import org.okapi.data.ddb.attributes.EXPECTED_RESULT_TYPE_DDB;
import org.okapi.data.ddb.attributes.MultiQueryPanelConfig;
import org.okapi.data.ddb.attributes.PanelQueryConfig;
import org.okapi.data.ddb.attributes.ResourceOrder;
import org.okapi.data.ddb.attributes.TagsList;
import org.okapi.data.dto.DashboardDdb;
import org.okapi.data.dto.OrgDtoDdb;
import org.okapi.data.dto.TokenMetaDdb;
import org.okapi.data.dto.UserDtoDdb;
import org.okapi.data.model.Dashboard;
import org.okapi.data.model.ExpectedResultType;
import org.okapi.data.model.Organization;
import org.okapi.data.model.TokenMetadata;
import org.okapi.data.model.User;

final class DdbMapper {
  private DdbMapper() {}

  static User toApi(UserDtoDdb value) {
    if (value == null) return null;
    return User.builder()
        .userId(value.getUserId())
        .email(value.getEmail())
        .status(User.Status.valueOf(value.getStatus().name()))
        .firstName(value.getFirstName())
        .lastName(value.getLastName())
        .hashedPassword(value.getHashedPassword())
        .build();
  }

  static UserDtoDdb toDdb(User value) {
    if (value == null) return null;
    return UserDtoDdb.builder()
        .userId(value.getUserId())
        .email(value.getEmail())
        .status(UserDtoDdb.UserStatus.valueOf(value.getStatus().name()))
        .firstName(value.getFirstName())
        .lastName(value.getLastName())
        .hashedPassword(value.getHashedPassword())
        .build();
  }

  static Organization toApi(OrgDtoDdb value) {
    if (value == null) return null;
    return Organization.builder()
        .orgId(value.getOrgId())
        .orgName(value.getOrgName())
        .orgCreator(value.getOrgCreator())
        .created(value.getCreated())
        .build();
  }

  static OrgDtoDdb toDdb(Organization value) {
    if (value == null) return null;
    return OrgDtoDdb.builder()
        .orgId(value.getOrgId())
        .orgName(value.getOrgName())
        .orgCreator(value.getOrgCreator())
        .created(value.getCreated())
        .build();
  }

  static Dashboard toApi(DashboardDdb value) {
    if (value == null) return null;
    return Dashboard.builder()
        .orgId(value.getOrgId())
        .dashboardId(value.getDashboardId())
        .creator(value.getCreator())
        .lastEditor(value.getLastEditor())
        .created(value.getCreated())
        .updatedTime(value.getUpdatedTime())
        .title(value.getTitle())
        .desc(value.getDesc())
        .tags(value.getTags() == null ? null : new org.okapi.data.model.Tags(value.getTags().asList()))
        .rowOrder(
            value.getRowOrder() == null
                ? null
                : new org.okapi.data.model.ResourceOrder(value.getRowOrder().asList()))
        .activeVersion(value.getActiveVersion())
        .version(value.getVersion())
        .build();
  }

  static DashboardDdb toDdb(Dashboard value) {
    if (value == null) return null;
    return DashboardDdb.builder()
        .orgId(value.getOrgId())
        .dashboardId(value.getDashboardId())
        .creator(value.getCreator())
        .lastEditor(value.getLastEditor())
        .created(value.getCreated())
        .updatedTime(value.getUpdatedTime())
        .title(value.getTitle())
        .desc(value.getDesc())
        .tags(value.getTags() == null ? null : new TagsList(value.getTags().asList()))
        .rowOrder(
            value.getRowOrder() == null ? null : new ResourceOrder(value.getRowOrder().asList()))
        .activeVersion(value.getActiveVersion())
        .version(value.getVersion())
        .build();
  }

  static org.okapi.data.model.DashboardRow toApi(org.okapi.data.dto.DashboardRow value) {
    if (value == null) return null;
    return org.okapi.data.model.DashboardRow.builder()
        .rowId(value.getRowId())
        .note(value.getNote())
        .title(value.getTitle())
        .panelOrder(
            value.getPanelOrder() == null
                ? null
                : new org.okapi.data.model.ResourceOrder(value.getPanelOrder().asList()))
        .build();
  }

  static org.okapi.data.dto.DashboardRow toDdb(org.okapi.data.model.DashboardRow value) {
    if (value == null) return null;
    return org.okapi.data.dto.DashboardRow.builder()
        .rowId(value.getRowId())
        .note(value.getNote())
        .title(value.getTitle())
        .panelOrder(
            value.getPanelOrder() == null
                ? null
                : new ResourceOrder(value.getPanelOrder().asList()))
        .build();
  }

  static org.okapi.data.model.PanelQueryConfig toApi(PanelQueryConfig value) {
    if (value == null) return null;
    return org.okapi.data.model.PanelQueryConfig.builder()
        .localId(value.getLocalId())
        .query(value.getQuery())
        .expectedResultType(
            value.getExpectedResultType() == null
                ? null
                : ExpectedResultType.valueOf(value.getExpectedResultType().name()))
        .build();
  }

  static PanelQueryConfig toDdb(org.okapi.data.model.PanelQueryConfig value) {
    if (value == null) return null;
    return PanelQueryConfig.builder()
        .localId(value.getLocalId())
        .query(value.getQuery())
        .expectedResultType(
            value.getExpectedResultType() == null
                ? null
                : EXPECTED_RESULT_TYPE_DDB.valueOf(value.getExpectedResultType().name()))
        .build();
  }

  static org.okapi.data.model.DashboardPanel toApi(org.okapi.data.dto.DashboardPanel value) {
    if (value == null) return null;
    var queryConfig =
        value.getQueryConfig() == null
            ? null
            : new org.okapi.data.model.MultiQueryPanelConfig(
                value.getQueryConfig().getQueryConfigs().stream().map(DdbMapper::toApi).toList());
    return org.okapi.data.model.DashboardPanel.builder()
        .panelId(value.getPanelId())
        .note(value.getNote())
        .title(value.getTitle())
        .queryConfig(queryConfig)
        .build();
  }

  static org.okapi.data.dto.DashboardPanel toDdb(org.okapi.data.model.DashboardPanel value) {
    if (value == null) return null;
    var queryConfig =
        value.getQueryConfig() == null
            ? null
            : new MultiQueryPanelConfig(
                value.getQueryConfig().getQueryConfigs().stream().map(DdbMapper::toDdb).toList());
    return org.okapi.data.dto.DashboardPanel.builder()
        .panelId(value.getPanelId())
        .note(value.getNote())
        .title(value.getTitle())
        .queryConfig(queryConfig)
        .build();
  }

  static org.okapi.data.model.DashboardVariable toApi(
      org.okapi.data.dto.DashboardVariable value) {
    if (value == null) return null;
    return org.okapi.data.model.DashboardVariable.builder()
        .varName(value.getVarName())
        .tag(value.getTag())
        .varType(org.okapi.data.model.DashboardVariable.Type.valueOf(value.getVarType().name()))
        .build();
  }

  static org.okapi.data.dto.DashboardVariable toDdb(
      org.okapi.data.model.DashboardVariable value) {
    if (value == null) return null;
    return org.okapi.data.dto.DashboardVariable.builder()
        .varName(value.getVarName())
        .tag(value.getTag())
        .varType(
            org.okapi.data.dto.DashboardVariable.DASHBOARD_VAR_TYPE.valueOf(
                value.getVarType().name()))
        .build();
  }

  static org.okapi.data.model.DashboardVersion toApi(
      org.okapi.data.dto.DashboardVersion value) {
    if (value == null) return null;
    return org.okapi.data.model.DashboardVersion.builder()
        .orgId(value.getOrgId())
        .dashboardId(value.getDashboardId())
        .versionId(value.getVersionId())
        .status(value.getStatus())
        .createdAt(value.getCreatedAt())
        .createdBy(value.getCreatedBy())
        .specHash(value.getSpecHash())
        .note(value.getNote())
        .build();
  }

  static org.okapi.data.dto.DashboardVersion toDdb(
      org.okapi.data.model.DashboardVersion value) {
    if (value == null) return null;
    return org.okapi.data.dto.DashboardVersion.builder()
        .orgId(value.getOrgId())
        .dashboardVersionId(
            org.okapi.data.dto.DashboardVersion.dashboardVersionId(
                value.getDashboardId(), value.getVersionId()))
        .dashboardId(value.getDashboardId())
        .versionId(value.getVersionId())
        .status(value.getStatus())
        .createdAt(value.getCreatedAt())
        .createdBy(value.getCreatedBy())
        .specHash(value.getSpecHash())
        .note(value.getNote())
        .build();
  }

  static org.okapi.data.model.FederatedSource toApi(org.okapi.data.dto.FederatedSource value) {
    if (value == null) return null;
    return org.okapi.data.model.FederatedSource.builder()
        .orgId(value.getOrgId())
        .sourceName(value.getSourceName())
        .sourceType(value.getSourceType())
        .registrationToken(value.getRegistrationToken())
        .created(value.getCreated())
        .build();
  }

  static org.okapi.data.dto.FederatedSource toDdb(
      org.okapi.data.model.FederatedSource value) {
    if (value == null) return null;
    return org.okapi.data.dto.FederatedSource.builder()
        .orgId(value.getOrgId())
        .sourceName(value.getSourceName())
        .sourceType(value.getSourceType())
        .registrationToken(value.getRegistrationToken())
        .created(value.getCreated())
        .build();
  }

  static TokenMetadata toApi(TokenMetaDdb value) {
    if (value == null) return null;
    return TokenMetadata.builder()
        .orgId(value.getOrgId())
        .creatorId(value.getCreatorId())
        .tokenId(value.getTokenId())
        .createdAt(value.getCreatedAt())
        .tokenStatus(org.okapi.data.model.TokenStatus.valueOf(value.getTokenStatus().name()))
        .build();
  }

  static TokenMetaDdb toDdb(TokenMetadata value) {
    if (value == null) return null;
    return new TokenMetaDdb(
        value.getOrgId(),
        value.getCreatorId(),
        value.getTokenId(),
        value.getCreatedAt(),
        org.okapi.data.dto.TOKEN_STATUS.valueOf(value.getTokenStatus().name()));
  }

  static org.okapi.data.model.PendingJob toApi(org.okapi.data.dto.PendingJobDdb value) {
    if (value == null) return null;
    return org.okapi.data.model.PendingJob.builder()
        .orgId(value.getOrgId())
        .jobId(value.getJobId())
        .resultLocation(value.getResultS3())
        .errorLocation(value.getErrorS3())
        .jobStatus(org.okapi.data.model.JobStatus.valueOf(value.getJobStatus().name()))
        .sourceId(value.getSourceId())
        .query(
            value.getQuery() == null
                ? null
                : new org.okapi.data.model.DataSourceQuery(
                    value.getQuery().getQuery(), value.getQuery().getSourceId()))
        .attemptCount(value.getAttemptCount())
        .createdAt(value.getCreatedAt())
        .assignedAt(value.getAssignedAt())
        .build();
  }

  static org.okapi.data.dto.PendingJobDdb toDdb(org.okapi.data.model.PendingJob value) {
    if (value == null) return null;
    var status = org.okapi.data.dto.JOB_STATUS.valueOf(value.getJobStatus().name());
    return org.okapi.data.dto.PendingJobDdb.builder()
        .orgId(value.getOrgId())
        .jobId(value.getJobId())
        .resultS3(value.getResultLocation())
        .errorS3(value.getErrorLocation())
        .jobStatus(status)
        .sourceId(value.getSourceId())
        .query(
            value.getQuery() == null
                ? null
                : new org.okapi.data.dto.DataSourceQuery(
                    value.getQuery().getQuery(), value.getQuery().getSourceId()))
        .attemptCount(value.getAttemptCount())
        .createdAt(value.getCreatedAt())
        .assignedAt(value.getAssignedAt())
        .orgSourceStatusKey(
            value.getSourceId() == null
                ? null
                : org.okapi.data.dto.PendingJobDdb.buildOrgSourceStatusKey(
                    value.getOrgId(), value.getSourceId(), status))
        .build();
  }

  static org.okapi.data.model.UserEntityRelation toApi(
      org.okapi.data.dto.UserEntityRelations value) {
    if (value == null) return null;
    var edgeId = value.getEdgeId();
    var attributes = value.getEdgeAttributes();
    return org.okapi.data.model.UserEntityRelation.builder()
        .userId(value.getUserId())
        .edgeId(
            new org.okapi.data.model.EntityRelationId(
                org.okapi.data.model.EntityType.valueOf(edgeId.getEntityType().name()),
                edgeId.getEntityId(),
                org.okapi.data.model.UserRelationType.valueOf(edgeId.getRelationType().name())))
        .edgeAttributes(
            attributes == null
                ? null
                : new org.okapi.data.model.EdgeAttributes(
                    attributes.getTimestamp(),
                    attributes.getStringValue(),
                    attributes.isBooleanValue()))
        .build();
  }

  static org.okapi.data.dto.UserEntityRelations toDdb(
      org.okapi.data.model.UserEntityRelation value) {
    if (value == null) return null;
    var edgeId = value.getEdgeId();
    var attributes = value.getEdgeAttributes();
    return org.okapi.data.dto.UserEntityRelations.builder()
        .userId(value.getUserId())
        .edgeId(
            new org.okapi.data.ddb.attributes.EntityRelationId(
                org.okapi.data.ddb.attributes.ENTITY_TYPE.valueOf(edgeId.entityType().name()),
                edgeId.entityId(),
                org.okapi.data.ddb.attributes.USER_RELATION_TYPE.valueOf(
                    edgeId.relationType().name())))
        .edgeAttributes(
            attributes == null
                ? null
                : new org.okapi.data.ddb.attributes.EdgeAttributes(
                    attributes.getTimestamp(),
                    attributes.getStringValue(),
                    attributes.isBooleanValue()))
        .build();
  }
}
