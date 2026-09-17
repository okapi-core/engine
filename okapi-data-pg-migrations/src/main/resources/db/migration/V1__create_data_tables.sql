DROP TABLE IF EXISTS data_records;

CREATE TABLE organizations (
    org_id TEXT PRIMARY KEY,
    org_name TEXT NOT NULL,
    org_creator TEXT,
    created_at TIMESTAMPTZ
);

CREATE TABLE users (
    user_id TEXT PRIMARY KEY,
    email TEXT NOT NULL,
    status TEXT NOT NULL,
    first_name TEXT,
    last_name TEXT,
    hashed_password TEXT NOT NULL,
    org_id TEXT NOT NULL REFERENCES organizations (org_id)
);
CREATE UNIQUE INDEX users_email_lower_uq ON users (lower(email));

CREATE TABLE dashboards (
    org_id TEXT NOT NULL,
    dashboard_id TEXT NOT NULL,
    creator TEXT,
    last_editor TEXT,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    title TEXT,
    description TEXT,
    tags JSONB,
    row_order JSONB,
    active_version TEXT,
    dashboard_vars JSONB,
    version BIGINT,
    PRIMARY KEY (org_id, dashboard_id)
);
CREATE INDEX dashboards_dashboard_id_idx ON dashboards (dashboard_id);

CREATE TABLE dashboard_versions (
    org_id TEXT NOT NULL,
    dashboard_id TEXT NOT NULL,
    version_id TEXT NOT NULL,
    status TEXT,
    created_at BIGINT,
    created_by TEXT,
    spec_hash TEXT,
    note TEXT,
    PRIMARY KEY (org_id, dashboard_id, version_id)
);

CREATE TABLE dashboard_rows (
    org_id TEXT NOT NULL,
    dashboard_id TEXT NOT NULL,
    version_id TEXT NOT NULL,
    row_id TEXT NOT NULL,
    note TEXT,
    title TEXT,
    panel_order JSONB,
    PRIMARY KEY (org_id, dashboard_id, version_id, row_id)
);

CREATE TABLE dashboard_panels (
    org_id TEXT NOT NULL,
    dashboard_id TEXT NOT NULL,
    version_id TEXT NOT NULL,
    row_id TEXT NOT NULL,
    panel_id TEXT NOT NULL,
    note TEXT,
    title TEXT,
    query_config JSONB,
    PRIMARY KEY (org_id, dashboard_id, version_id, row_id, panel_id)
);

CREATE TABLE dashboard_variables (
    org_id TEXT NOT NULL,
    dashboard_id TEXT NOT NULL,
    version_id TEXT NOT NULL,
    var_name TEXT NOT NULL,
    tag TEXT,
    var_type TEXT NOT NULL,
    PRIMARY KEY (org_id, dashboard_id, version_id, var_name)
);

CREATE TABLE federated_sources (
    org_id TEXT NOT NULL,
    source_name TEXT NOT NULL,
    source_type TEXT,
    registration_token TEXT,
    created_at TIMESTAMPTZ,
    PRIMARY KEY (org_id, source_name)
);

CREATE TABLE user_entity_relations (
    user_id TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    relation_type TEXT NOT NULL,
    edge_timestamp BIGINT,
    edge_string_value TEXT,
    edge_boolean_value BOOLEAN,
    PRIMARY KEY (user_id, entity_type, entity_id, relation_type)
);

CREATE TABLE entity_relations (
    source_type TEXT NOT NULL,
    source_id TEXT NOT NULL,
    target_type TEXT NOT NULL,
    target_id TEXT NOT NULL,
    relation_type TEXT NOT NULL,
    PRIMARY KEY (source_type, source_id, target_type, target_id, relation_type)
);
CREATE INDEX entity_relations_target_idx
    ON entity_relations (target_type, target_id);

CREATE TABLE pending_jobs (
    org_id TEXT NOT NULL,
    job_id TEXT NOT NULL,
    result_location TEXT,
    error_location TEXT,
    status TEXT NOT NULL,
    source_id TEXT,
    query_text TEXT,
    query_source_id TEXT,
    attempt_count INTEGER NOT NULL,
    created_at BIGINT,
    assigned_at BIGINT,
    PRIMARY KEY (org_id, job_id)
);
CREATE INDEX pending_jobs_org_status_idx ON pending_jobs (org_id, status);
CREATE INDEX pending_jobs_org_source_status_idx
    ON pending_jobs (org_id, source_id, status);

CREATE TABLE infra_entity_nodes (
    tenant_id TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    attributes TEXT,
    PRIMARY KEY (tenant_id, entity_type, entity_id)
);

CREATE TABLE infra_entity_edges (
    source_tenant_id TEXT NOT NULL,
    source_entity_type TEXT NOT NULL,
    source_entity_id TEXT NOT NULL,
    target_tenant_id TEXT NOT NULL,
    target_entity_type TEXT NOT NULL,
    target_entity_id TEXT NOT NULL,
    dependency_type TEXT NOT NULL,
    edge_attributes TEXT,
    PRIMARY KEY (
        source_tenant_id, source_entity_type, source_entity_id,
        target_tenant_id, target_entity_type, target_entity_id, dependency_type
    ),
    FOREIGN KEY (source_tenant_id, source_entity_type, source_entity_id)
        REFERENCES infra_entity_nodes (tenant_id, entity_type, entity_id)
        ON DELETE CASCADE,
    FOREIGN KEY (target_tenant_id, target_entity_type, target_entity_id)
        REFERENCES infra_entity_nodes (tenant_id, entity_type, entity_id)
        ON DELETE CASCADE
);
