/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.okapi.data.dao.*;
import org.okapi.data.exceptions.IllegalJobStateTransition;
import org.okapi.data.exceptions.UserAlreadyExistsException;
import org.okapi.data.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(classes = PostgresDaosIT.TestApplication.class)
class PostgresDaosIT {
  @Autowired private JdbcTemplate jdbc;
  @Autowired private UsersDao users;
  @Autowired private OrgDao organizations;
  @Autowired private DashboardDao dashboards;
  @Autowired private DashboardVersionDao versions;
  @Autowired private DashboardRowDao rows;
  @Autowired private DashboardPanelDao panels;
  @Autowired private DashboardVarDao variables;
  @Autowired private RelationGraphDao graph;
  @Autowired private UserEntityRelationsDao userRelations;
  @Autowired private PendingJobsDao jobs;
  @Autowired private InfraEntityNodeDao infra;

  @BeforeEach
  void resetDatabase() {
    jdbc.execute(
        """
        TRUNCATE TABLE
          infra_entity_edges,
          infra_entity_nodes,
          pending_jobs,
          entity_relations,
          user_entity_relations,
          token_metadata,
          federated_sources,
          dashboard_variables,
          dashboard_panels,
          dashboard_rows,
          dashboard_versions,
          dashboards,
          organizations,
          users
        """);
  }

  @Test
  void createsNormalizedTablesWithoutGenericRecordStorage() {
    var tables =
        jdbc.queryForList(
            """
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = current_schema()
            """,
            String.class);

    assertTrue(tables.contains("users"));
    assertTrue(tables.contains("dashboards"));
    assertTrue(tables.contains("pending_jobs"));
    assertTrue(tables.contains("infra_entity_edges"));
    assertFalse(tables.contains("data_records"));
  }

  @Test
  void persistsUsersAndOrganizationsWithUniqueEmails() throws Exception {
    var user = users.createIfNotExists("Ada", "Lovelace", "ada@example.com", "secret");
    organizations.save(
        Organization.builder()
            .orgId("org-1")
            .orgName("Analytical Engines")
            .orgCreator(user.getUserId())
            .created(Instant.parse("2026-01-02T03:04:05Z"))
            .build());

    assertEquals(user, users.getWithEmail("ADA@example.com").orElseThrow());
    assertTrue(user.getHashedPassword().startsWith("$2"));
    assertEquals("Analytical Engines", organizations.findById("org-1").orElseThrow().getOrgName());
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM users", Integer.class));
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM organizations", Integer.class));
    assertThrows(
        UserAlreadyExistsException.class,
        () -> users.createIfNotExists("Other", "User", "ada@example.com", "secret"));
  }

  @Test
  void persistsDashboardHierarchyAndVersions() {
    dashboards.save(
        Dashboard.builder()
            .orgId("org")
            .dashboardId("dash")
            .title("Overview")
            .tags(Tags.of("prod"))
            .rowOrder(ResourceOrder.from("row"))
            .build());
    versions.save(
        DashboardVersion.builder()
            .orgId("org")
            .dashboardId("dash")
            .versionId("v1")
            .status("ACTIVE")
            .createdAt(10L)
            .build());
    rows.save(
        "org",
        "dash",
        "v1",
        DashboardRow.builder()
            .rowId("row")
            .title("Services")
            .panelOrder(ResourceOrder.from("panel"))
            .build());
    panels.save(
        "org",
        "dash",
        "row",
        "v1",
        DashboardPanel.builder()
            .panelId("panel")
            .title("Latency")
            .queryConfig(
                new MultiQueryPanelConfig(
                    List.of(
                        PanelQueryConfig.builder()
                            .localId("q")
                            .query("up")
                            .expectedResultType(ExpectedResultType.TIME_VECTOR)
                            .build())))
            .build());
    variables.save(
        "org",
        "dash",
        "v1",
        DashboardVariable.builder()
            .varName("service")
            .varType(DashboardVariable.Type.TAG)
            .tag("service.name")
            .build());

    assertEquals("Overview", dashboards.get("org", "dash").orElseThrow().getTitle());
    assertEquals("v1", versions.list("org", "dash").getFirst().getVersionId());
    assertEquals(
        ResourceOrder.from("panel"), rows.getAll("org", "dash", "v1").getFirst().getPanelOrder());
    assertEquals(
        ExpectedResultType.TIME_VECTOR,
        panels
            .get("org", "dash", "row", "v1", "panel")
            .orElseThrow()
            .getQueryConfig()
            .getQueryConfigs()
            .getFirst()
            .getExpectedResultType());
    assertEquals("service.name", variables.list("org", "dash", "v1").getFirst().getTag());
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM dashboard_panels", Integer.class));
  }

  @Test
  void traversesRelationGraphAndDeletesBothSides() {
    var user = EntityId.of(EntityType.USER, "user");
    var org = EntityId.of(EntityType.ORG, "org");
    var dashboard = EntityId.of(EntityType.DASHBOARD, "dashboard");

    graph.addRelationship(user, org, RelationType.ORG_MEMBER);
    graph.addRelationship(org, dashboard, RelationType.DASHBOARD_READ);

    assertTrue(
        graph.isPathBetween(
            user,
            dashboard,
            new EdgeSequence(
                List.of(
                    OutgoingEdge.of(EntityType.ORG, RelationType.ORG_MEMBER),
                    OutgoingEdge.of(EntityType.DASHBOARD, RelationType.DASHBOARD_READ)))));
    graph.deleteEntity(org);
    assertFalse(graph.hasRelationBetween(user, org, RelationType.ORG_MEMBER));
  }

  @Test
  void persistsUserRelationsWithoutOptionalAttributes() {
    var edge =
        new EntityRelationId(EntityType.DASHBOARD, "dashboard", UserRelationType.DASHBOARD_FAVE);
    userRelations.createRelation(
        UserEntityRelation.builder().userId("user").edgeId(edge).edgeAttributes(null).build());

    var relation = userRelations.getRelation("user", edge).orElseThrow();
    assertNull(relation.getEdgeAttributes());
  }

  @Test
  void enforcesPendingJobTransitionsAndQueriesBySource() throws Exception {
    var job =
        PendingJob.builder()
            .orgId("org")
            .jobId("job")
            .jobStatus(JobStatus.PENDING)
            .sourceId("source")
            .query(new DataSourceQuery("up", "source"))
            .build();
    jobs.createPendingJob(job);

    assertEquals(1, jobs.getJobsBySourceAndStatus("org", "source", JobStatus.PENDING, 10).size());
    jobs.updateJobStatus("org", "job", JobStatus.IN_PROGRESS);
    var completed = jobs.updateJobResult("org", "job", "{\"resultType\":\"NONE\"}");
    assertEquals(JobStatus.COMPLETED, completed.getJobStatus());
    assertThrows(
        IllegalJobStateTransition.class,
        () -> jobs.updateJobStatus("org", "job", JobStatus.PENDING));
  }

  @Test
  void validatesAndPersistsInfrastructureEdges() throws Exception {
    var source = new InfraEntityId("org", InfraEntityType.SERVICE, "api");
    var target = new InfraEntityId("org", InfraEntityType.HOST, "host");
    infra.createNode(new InfraEntityNode(source, "{}", new ArrayList<>()));
    infra.createNode(new InfraEntityNode(target, "{}", new ArrayList<>()));

    infra.addOutgoingEdge(
        source, new InfraNodeOutgoingEdge(target, "{\"port\":443}", DependencyType.RUNS_ON));

    assertEquals(
        target, infra.getEdgesByType(source, DependencyType.RUNS_ON).getFirst().targetNodeId());
    infra.removeEdge(source, target);
    assertTrue(infra.getAllOutgoingEdges(source).isEmpty());
  }

  private static final class MemoryUploader implements ResultUploader {
    private final Map<String, String> values = new HashMap<>();

    public String uploadResult(String orgId, String jobId, String resultData) {
      var key = orgId + "/" + jobId;
      values.put(key, resultData);
      return key;
    }

    public String getRawResult(String orgId, String jobId) {
      return values.get(orgId + "/" + jobId);
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class TestApplication {
    @Bean
    ResultUploader resultUploader() {
      return new MemoryUploader();
    }
  }
}
