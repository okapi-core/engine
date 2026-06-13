/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.spring.config;

import org.okapi.data.dao.*;
import org.okapi.data.pg.PostgresDaos;
import org.okapi.data.pg.PostgresDataStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class DaoConfiguration {

  @Bean
  public PostgresDataStore postgresDataStore() {
    return PostgresDataStore.fromEnvironment();
  }

  @Bean
  public ResultUploader resultUploader(@Autowired S3Client s3Client, @Autowired S3Cfg s3Cfg) {
    return new S3ResultUploader(s3Client, s3Cfg.getBucket(), s3Cfg.getResultsPrefix());
  }

  @Bean
  public UsersDao usersDao(@Autowired PostgresDataStore store) {
    return PostgresDaos.users(store);
  }

  @Bean
  public OrgDao orgDao(@Autowired PostgresDataStore store) {
    return PostgresDaos.organizations(store);
  }

  @Bean
  public RelationGraphDao relationGraphDao(@Autowired PostgresDataStore store) {
    return PostgresDaos.relationGraph(store);
  }

  @Bean
  public DashboardDao dashboardDao(@Autowired PostgresDataStore store) {
    return PostgresDaos.dashboards(store);
  }

  @Bean
  public FederatedSourceRepo federatedSourceRepo(@Autowired PostgresDataStore store) {
    return PostgresDaos.federatedSources(store);
  }

  @Bean
  public DashboardRowDao dashboardRowDao(@Autowired PostgresDataStore store) {
    return PostgresDaos.dashboardRows(store);
  }

  @Bean
  public DashboardPanelDao dashboardPanelDao(@Autowired PostgresDataStore store) {
    return PostgresDaos.dashboardPanels(store);
  }

  @Bean
  public UserEntityRelationsDao userEntityRelationsDao(@Autowired PostgresDataStore store) {
    return PostgresDaos.userEntityRelations(store);
  }

  @Bean
  public PendingJobsDao pendingJobsDao(
      @Autowired PostgresDataStore store, @Autowired ResultUploader resultUploader) {
    return PostgresDaos.pendingJobs(store, resultUploader);
  }

  @Bean
  public TokenMetaDao tokenMetaDao(@Autowired PostgresDataStore store) {
    return PostgresDaos.tokens(store);
  }

  @Bean
  public DashboardVarDao dashboardVarDao(@Autowired PostgresDataStore store) {
    return PostgresDaos.dashboardVariables(store);
  }

  @Bean
  public DashboardVersionDao dashboardVersionDao(@Autowired PostgresDataStore store) {
    return PostgresDaos.dashboardVersions(store);
  }
}
