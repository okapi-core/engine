/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import org.okapi.agent.dto.QueryResult;
import org.okapi.data.bcrypt.BCrypt;
import org.okapi.data.dao.*;
import org.okapi.data.exceptions.*;
import org.okapi.data.model.*;

public final class PostgresDaos {
  private PostgresDaos() {}

  public static UsersDao users(PostgresDataStore store) {
    return new Users(store);
  }

  public static OrgDao organizations(PostgresDataStore store) {
    return new Organizations(store);
  }

  public static RelationGraphDao relationGraph(PostgresDataStore store) {
    return new Relations(store);
  }

  public static DashboardDao dashboards(PostgresDataStore store) {
    return new Dashboards(store);
  }

  public static FederatedSourceRepo federatedSources(PostgresDataStore store) {
    return new FederatedSources(store);
  }

  public static DashboardRowDao dashboardRows(PostgresDataStore store) {
    return new DashboardRows(store);
  }

  public static DashboardPanelDao dashboardPanels(PostgresDataStore store) {
    return new DashboardPanels(store);
  }

  public static UserEntityRelationsDao userEntityRelations(PostgresDataStore store) {
    return new UserEntityRelations(store);
  }

  public static PendingJobsDao pendingJobs(PostgresDataStore store, ResultUploader uploader) {
    return new PendingJobs(store, uploader);
  }

  public static TokenMetaDao tokens(PostgresDataStore store) {
    return new Tokens(store);
  }

  public static DashboardVarDao dashboardVariables(PostgresDataStore store) {
    return new DashboardVariables(store);
  }

  public static DashboardVersionDao dashboardVersions(PostgresDataStore store) {
    return new DashboardVersions(store);
  }

  public static InfraEntityNodeDao infraEntities(PostgresDataStore store) {
    return new InfraEntities(store);
  }

  private static String key(String... values) {
    return String.join("\u001f", values);
  }

  private record PathNode(int pathIndex, EntityId node) {}

  private static final class Users implements UsersDao {
    private final PostgresDataStore store;

    private Users(PostgresDataStore store) {
      this.store = store;
    }

    public Optional<User> get(String id) {
      return store.get("user", id, User.class);
    }

    public Optional<User> getWithEmail(String email) {
      return store.findByScope("user", email, User.class);
    }

    public User createIfNotExists(String first, String last, String email, String password)
        throws UserAlreadyExistsException {
      if (getWithEmail(email).isPresent()) throw new UserAlreadyExistsException();
      var hash =
          BCrypt.hashpw(password == null ? UUID.randomUUID().toString() : password, BCrypt.gensalt());
      var user =
          User.builder()
              .userId(UUID.randomUUID().toString())
              .email(email)
              .status(User.Status.ACTIVE)
              .firstName(first)
              .lastName(last)
              .hashedPassword(hash)
              .build();
      try {
        update(user);
      } catch (IllegalStateException e) {
        if (getWithEmail(email).isPresent()) throw new UserAlreadyExistsException();
        throw e;
      }
      return user;
    }

    public Iterator<User> listAllUsers() {
      return store.list("user", null, null, User.class).iterator();
    }

    public void update(User user) {
      store.put("user", user.getUserId(), user.getEmail(), null, user.getStatus().name(), null, user);
    }
  }

  private static final class Organizations implements OrgDao {
    private final PostgresDataStore store;

    private Organizations(PostgresDataStore store) {
      this.store = store;
    }

    public Optional<Organization> findById(String id) {
      return store.get("organization", id, Organization.class);
    }

    public void save(Organization organization) {
      store.put("organization", organization.getOrgId(), null, null, null, null, organization);
    }
  }

  private static final class Dashboards implements DashboardDao {
    private final PostgresDataStore store;

    private Dashboards(PostgresDataStore store) {
      this.store = store;
    }

    public Optional<Dashboard> get(String orgId, String id) {
      return store.get("dashboard", key(orgId, id), Dashboard.class);
    }

    public Dashboard save(Dashboard dashboard) {
      if (dashboard == null) throw new NullPointerException("dashboard");
      store.put(
          "dashboard",
          key(dashboard.getOrgId(), dashboard.getDashboardId()),
          dashboard.getOrgId(),
          null,
          null,
          null,
          dashboard);
      return dashboard;
    }

    public void delete(String id) throws ResourceNotFoundException {
      var dashboard =
          store.list("dashboard", null, null, Dashboard.class).stream()
              .filter(value -> id.equals(value.getDashboardId()))
              .findFirst()
              .orElseThrow(() -> new ResourceNotFoundException("Dashboard with id " + id + " not found"));
      store.delete("dashboard", key(dashboard.getOrgId(), id));
    }

    public List<Dashboard> getAll(String orgId) {
      return store.list("dashboard", orgId, null, Dashboard.class);
    }
  }

  private static final class DashboardRows implements DashboardRowDao {
    private final PostgresDataStore store;

    private DashboardRows(PostgresDataStore store) {
      this.store = store;
    }

    private String scope(String org, String dashboard, String version) {
      return key(org, dashboard, version);
    }

    public Optional<DashboardRow> get(String org, String dashboard, String version, String row) {
      return store.get("dashboard-row", key(scope(org, dashboard, version), row), DashboardRow.class);
    }

    public void save(String org, String dashboard, String version, DashboardRow row) {
      var scope = scope(org, dashboard, version);
      store.put("dashboard-row", key(scope, row.getRowId()), scope, null, null, null, row);
    }

    public void delete(String org, String dashboard, String version, String row) {
      store.delete("dashboard-row", key(scope(org, dashboard, version), row));
    }

    public List<DashboardRow> getAll(String org, String dashboard, String version) {
      return store.list("dashboard-row", scope(org, dashboard, version), null, DashboardRow.class);
    }
  }

  private static final class DashboardPanels implements DashboardPanelDao {
    private final PostgresDataStore store;

    private DashboardPanels(PostgresDataStore store) {
      this.store = store;
    }

    private String scope(String org, String dashboard, String row, String version) {
      return key(org, dashboard, version, row);
    }

    public Optional<DashboardPanel> get(
        String org, String dashboard, String row, String version, String panel) {
      return store.get(
          "dashboard-panel", key(scope(org, dashboard, row, version), panel), DashboardPanel.class);
    }

    public void save(
        String org, String dashboard, String row, String version, DashboardPanel panel) {
      var scope = scope(org, dashboard, row, version);
      store.put("dashboard-panel", key(scope, panel.getPanelId()), scope, null, null, null, panel);
    }

    public void delete(
        String org, String dashboard, String row, String version, String panel) {
      store.delete("dashboard-panel", key(scope(org, dashboard, row, version), panel));
    }

    public List<DashboardPanel> getAll(
        String org, String dashboard, String row, String version) {
      return store.list(
          "dashboard-panel", scope(org, dashboard, row, version), null, DashboardPanel.class);
    }
  }

  private static final class DashboardVariables implements DashboardVarDao {
    private final PostgresDataStore store;

    private DashboardVariables(PostgresDataStore store) {
      this.store = store;
    }

    private String scope(String org, String dashboard, String version) {
      return key(org, dashboard, version);
    }

    public Optional<DashboardVariable> get(
        String org, String dashboard, String version, String name) {
      return store.get(
          "dashboard-variable",
          key(scope(org, dashboard, version), name),
          DashboardVariable.class);
    }

    public DashboardVariable save(
        String org, String dashboard, String version, DashboardVariable variable) {
      if (variable == null) throw new NullPointerException("variable");
      var scope = scope(org, dashboard, version);
      store.put(
          "dashboard-variable", key(scope, variable.getVarName()), scope, null, null, null, variable);
      return variable;
    }

    public void delete(String org, String dashboard, String version, String name) {
      store.delete("dashboard-variable", key(scope(org, dashboard, version), name));
    }

    public List<DashboardVariable> list(String org, String dashboard, String version) {
      return store.list(
          "dashboard-variable", scope(org, dashboard, version), null, DashboardVariable.class);
    }
  }

  private static final class DashboardVersions implements DashboardVersionDao {
    private final PostgresDataStore store;

    private DashboardVersions(PostgresDataStore store) {
      this.store = store;
    }

    public void save(DashboardVersion version) {
      store.put(
          "dashboard-version",
          key(version.getOrgId(), version.getDashboardId(), version.getVersionId()),
          version.getOrgId(),
          version.getDashboardId(),
          version.getStatus(),
          null,
          version);
    }

    public Optional<DashboardVersion> get(String org, String dashboard, String version) {
      return store.get(
          "dashboard-version", key(org, dashboard, version), DashboardVersion.class);
    }

    public List<DashboardVersion> list(String org, String dashboard) {
      return store.list("dashboard-version", org, dashboard, DashboardVersion.class);
    }
  }

  private static final class FederatedSources implements FederatedSourceRepo {
    private final PostgresDataStore store;

    private FederatedSources(PostgresDataStore store) {
      this.store = store;
    }

    public Optional<FederatedSource> getSource(String tenant, String source) {
      return store.get("federated-source", key(tenant, source), FederatedSource.class);
    }

    public List<FederatedSource> getAllSources(String tenant) {
      return store.list("federated-source", tenant, null, FederatedSource.class);
    }

    public void createSource(FederatedSource source) {
      store.put(
          "federated-source",
          key(source.getOrgId(), source.getSourceName()),
          source.getOrgId(),
          null,
          null,
          null,
          source);
    }

    public void deleteSource(String tenant, String source) {
      store.delete("federated-source", key(tenant, source));
    }
  }

  private static final class Tokens implements TokenMetaDao {
    private final PostgresDataStore store;

    private Tokens(PostgresDataStore store) {
      this.store = store;
    }

    public void createTokenMetadata(TokenMetadata token) {
      store.put(
          "token",
          key(token.getOrgId(), token.getTokenId()),
          token.getOrgId(),
          null,
          token.getTokenStatus().name(),
          null,
          token);
    }

    public TokenMetadata getTokenMetadata(String org, String token) {
      return store.get("token", key(org, token), TokenMetadata.class).orElse(null);
    }

    public void updateTokenStatus(String org, String token, TokenStatus status) {
      var metadata = getTokenMetadata(org, token);
      if (metadata == null) return;
      metadata.setTokenStatus(status);
      createTokenMetadata(metadata);
    }

    public List<TokenMetadata> listTokensByOrgAndStatus(String org, TokenStatus status) {
      return store.listByStatus("token", org, null, status.name(), Integer.MAX_VALUE, TokenMetadata.class);
    }
  }

  private static final class UserEntityRelations implements UserEntityRelationsDao {
    private final PostgresDataStore store;

    private UserEntityRelations(PostgresDataStore store) {
      this.store = store;
    }

    private String edge(EntityRelationId edge) {
      return key(edge.entityType().name(), edge.entityId(), edge.relationType().name());
    }

    public Optional<UserEntityRelation> getRelation(String user, EntityRelationId edge) {
      return store.get("user-entity-relation", key(user, edge(edge)), UserEntityRelation.class);
    }

    public List<UserEntityRelation> listUserRelations(String user) {
      return store.list("user-entity-relation", user, null, UserEntityRelation.class);
    }

    public void createRelation(UserEntityRelation relation) {
      store.put(
          "user-entity-relation",
          key(relation.getUserId(), edge(relation.getEdgeId())),
          relation.getUserId(),
          null,
          null,
          null,
          relation);
    }

    public void deleteRelation(String user, EntityRelationId edge) {
      store.delete("user-entity-relation", key(user, edge(edge)));
    }
  }

  private static final class Relations implements RelationGraphDao {
    private final PostgresDataStore store;

    private Relations(PostgresDataStore store) {
      this.store = store;
    }

    private String id(EntityId value) {
      return value.toString();
    }

    private List<RelationGraphNode> all(EntityId value) {
      return store.list("relation", id(value), null, RelationGraphNode.class);
    }

    public Optional<RelationGraphNode> getRelationsBetween(EntityId left, EntityId right) {
      return store.get("relation", key(id(left), id(right)), RelationGraphNode.class);
    }

    public boolean hasRelationBetween(EntityId left, EntityId right, RelationType type) {
      return getRelationsBetween(left, right)
          .map(node -> node.getRelationships().contains(type))
          .orElse(false);
    }

    public void removeAllRelations(EntityId left, EntityId right) {
      store.delete("relation", key(id(left), id(right)));
      store.delete("relation", key(id(right), id(left)));
    }

    public void removeRelation(EntityId left, EntityId right, RelationType type) {
      getRelationsBetween(left, right)
          .ifPresent(
              node -> {
                node.getRelationships().remove(type);
                save(node);
              });
    }

    public RelationGraphNode addRelationship(EntityId left, EntityId right, RelationType type) {
      return addAllRelationships(left, right, List.of(type));
    }

    public RelationGraphNode addAllRelationships(
        EntityId left, EntityId right, List<RelationType> types) {
      var node =
          getRelationsBetween(left, right)
              .orElseGet(
                  () ->
                      RelationGraphNode.builder()
                          .entityId(id(left))
                          .relatedEntity(id(right))
                          .relatedEntityType(right.type())
                          .relationships(new ArrayList<>())
                          .build());
      types.stream()
          .filter(type -> !node.getRelationships().contains(type))
          .forEach(node.getRelationships()::add);
      save(node);
      return node;
    }

    private void save(RelationGraphNode node) {
      store.put(
          "relation",
          key(node.getEntityId(), node.getRelatedEntity()),
          node.getEntityId(),
          node.getRelatedEntityType().name(),
          null,
          node.getRelatedEntity(),
          node);
    }

    public boolean isPathBetween(EntityId start, EntityId end, EdgeSequence acceptedPath) {
      Queue<PathNode> nodes = new ArrayDeque<>();
      nodes.add(new PathNode(0, start));
      while (!nodes.isEmpty()) {
        var current = nodes.remove();
        if (current.pathIndex() >= acceptedPath.accepted().size()) continue;
        var expected = acceptedPath.accepted().get(current.pathIndex());
        for (var relation : all(current.node())) {
          if (relation.getRelatedEntityType() != expected.outgoingNodeType()
              || !relation.getRelationships().contains(expected.relationType())) continue;
          var next = EntityId.parse(relation.getRelatedEntity());
          if (next.isEmpty()) continue;
          if (current.pathIndex() == acceptedPath.accepted().size() - 1
              && next.get().equals(end)) return true;
          nodes.add(new PathNode(current.pathIndex() + 1, next.get()));
        }
      }
      return false;
    }

    public boolean isAnyPathBetween(EntityId start, EntityId end, List<EdgeSequence> paths) {
      return paths.stream().anyMatch(path -> isPathBetween(start, end, path));
    }

    public List<RelationGraphNode> getAllRelationsOfType(
        EntityId entity, EntityType type, RelationType relationType) {
      return all(entity).stream()
          .filter(
              relation ->
                  relation.getRelatedEntityType() == type
                      && relation.getRelationships().contains(relationType))
          .toList();
    }

    public List<RelationGraphNode> getAllRelationsOfNodeType(EntityId entity, EntityType type) {
      return all(entity).stream()
          .filter(relation -> relation.getRelatedEntityType() == type)
          .toList();
    }

    public void deleteEntity(EntityId entity) {
      var id = id(entity);
      for (var relation : all(entity)) {
        store.delete("relation", key(relation.getRelatedEntity(), id));
      }
      store.deleteByScope("relation", id);
      store.deleteBySource("relation", id);
    }
  }

  private static final class PendingJobs implements PendingJobsDao {
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private final PostgresDataStore store;
    private final ResultUploader uploader;

    private PendingJobs(PostgresDataStore store, ResultUploader uploader) {
      this.store = store;
      this.uploader = uploader;
    }

    public Optional<PendingJob> getPendingJob(String org, String job) {
      return store.get("pending-job", key(org, job), PendingJob.class);
    }

    public List<PendingJob> getPendingJobsByTenantAndStatus(String org, JobStatus status) {
      return store.listByStatus(
          "pending-job", org, null, status.name(), Integer.MAX_VALUE, PendingJob.class);
    }

    public void createPendingJob(PendingJob job) {
      save(job);
    }

    public void updatePendingJob(PendingJob job) throws IllegalJobStateTransition {
      var existing =
          getPendingJob(job.getOrgId(), job.getJobId())
              .orElseThrow(() -> new JobNotFoundException("Job not found for update"));
      if (existing.getJobStatus() != job.getJobStatus()
          && !canTransition(existing.getJobStatus(), job.getJobStatus())) {
        throw new IllegalJobStateTransition(
            "Invalid state transition from "
                + existing.getJobStatus()
                + " to "
                + job.getJobStatus());
      }
      save(job);
    }

    private void save(PendingJob job) {
      store.put(
          "pending-job",
          key(job.getOrgId(), job.getJobId()),
          job.getOrgId(),
          null,
          job.getJobStatus().name(),
          job.getSourceId(),
          job);
    }

    private boolean canTransition(JobStatus from, JobStatus to) {
      return switch (from) {
        case PENDING -> to == JobStatus.IN_PROGRESS || to == JobStatus.CANCELLED;
        case IN_PROGRESS ->
            to == JobStatus.COMPLETED || to == JobStatus.FAILED || to == JobStatus.CANCELLED;
        case FAILED -> to == JobStatus.PENDING;
        case CANCELLED, COMPLETED -> false;
      };
    }

    public void deletePendingJob(String org, String job) {
      store.delete("pending-job", key(org, job));
    }

    public void retryJob(String org, String job)
        throws TooManyRetriesException, IllegalJobStateTransition {
      var value =
          getPendingJob(org, job).orElseThrow(() -> new JobNotFoundException("Job not found for retry"));
      if (value.getAttemptCount() >= MAX_RETRY_ATTEMPTS) {
        throw new TooManyRetriesException(
            "Max retry attempts reached for job: " + org + "/" + job);
      }
      value.setJobStatus(JobStatus.PENDING);
      value.setSourceId(null);
      value.setAssignedAt(null);
      value.setAttemptCount(value.getAttemptCount() + 1);
      updatePendingJob(value);
    }

    public PendingJob updateJobStatus(String org, String job, JobStatus status)
        throws IllegalJobStateTransition {
      var value = getPendingJob(org, job).orElse(null);
      if (value == null) return null;
      value.setJobStatus(status);
      updatePendingJob(value);
      return value;
    }

    public List<PendingJob> getJobsBySourceAndStatus(
        String org, String source, JobStatus status, int limit) {
      return store.listByStatus("pending-job", org, source, status.name(), limit, PendingJob.class);
    }

    public PendingJob updateJobResult(String org, String job, String result)
        throws IllegalJobStateTransition {
      var value =
          getPendingJob(org, job).orElseThrow(() -> new JobNotFoundException("Job not found"));
      value.setResultLocation(uploader.uploadResult(org, job, result));
      value.setJobStatus(JobStatus.COMPLETED);
      updatePendingJob(value);
      return value;
    }

    public PendingJob updateJobError(String org, String job, String error)
        throws IllegalJobStateTransition {
      var value =
          getPendingJob(org, job).orElseThrow(() -> new JobNotFoundException("Job not found"));
      value.setErrorLocation(uploader.uploadResult(org, job, error));
      value.setJobStatus(JobStatus.FAILED);
      updatePendingJob(value);
      return value;
    }

    public Optional<QueryResult> getRawResult(String org, String job) {
      var value =
          getPendingJob(org, job).orElseThrow(() -> new JobNotFoundException("Job not found"));
      if (value.getJobStatus() != JobStatus.COMPLETED
          && value.getJobStatus() != JobStatus.FAILED) return Optional.empty();
      return Optional.of(store.gson().fromJson(uploader.getRawResult(org, job), QueryResult.class));
    }
  }

  private static final class InfraEntities implements InfraEntityNodeDao {
    private final PostgresDataStore store;

    private InfraEntities(PostgresDataStore store) {
      this.store = store;
    }

    private String key(InfraEntityId id) {
      return PostgresDaos.key(id.tenantId(), id.entityType().name(), id.id());
    }

    public void createNode(InfraEntityNode node) {
      if (node != null)
        store.put("infra-entity", key(node.getInfraEntityId()), node.getInfraEntityId().tenantId(), null, null, null, node);
    }

    public <T> void updateNodeAttributes(InfraEntityId id, T attributes, Class<T> clazz) {
      var node = getNode(id).orElseGet(() -> new InfraEntityNode(id, null, new ArrayList<>()));
      node.setAttributes(store.gson().toJson(attributes));
      createNode(node);
    }

    public void deleteNode(InfraEntityId id) {
      store.delete("infra-entity", key(id));
    }

    public void addOutgoingEdge(InfraEntityId id, InfraNodeOutgoingEdge edge)
        throws EntityDoesNotExistException {
      var node =
          getNode(id)
              .orElseThrow(() -> new EntityDoesNotExistException("Cannot add edge FROM non-existent node"));
      if (getNode(edge.targetNodeId()).isEmpty())
        throw new EntityDoesNotExistException("Cannot create edge TO non-existent node");
      var edges =
          node.getOutgoingEdges() == null
              ? new ArrayList<InfraNodeOutgoingEdge>()
              : new ArrayList<>(node.getOutgoingEdges());
      edges.add(edge);
      node.setOutgoingEdges(edges);
      createNode(node);
    }

    public void removeEdge(InfraEntityId id, InfraEntityId target) {
      getNode(id)
          .ifPresent(
              node -> {
                var edges = new ArrayList<>(node.getOutgoingEdges());
                edges.removeIf(edge -> edge.targetNodeId().equals(target));
                node.setOutgoingEdges(edges);
                createNode(node);
              });
    }

    public List<InfraNodeOutgoingEdge> getEdgesByType(InfraEntityId id, DependencyType type) {
      return getAllOutgoingEdges(id).stream().filter(edge -> edge.depType() == type).toList();
    }

    public List<InfraNodeOutgoingEdge> getAllOutgoingEdges(InfraEntityId id) {
      return getNode(id)
          .map(InfraEntityNode::getOutgoingEdges)
          .map(ArrayList::new)
          .orElseGet(ArrayList::new);
    }

    public Optional<InfraEntityNode> getNode(InfraEntityId id) {
      return store.get("infra-entity", key(id), InfraEntityNode.class);
    }
  }
}
