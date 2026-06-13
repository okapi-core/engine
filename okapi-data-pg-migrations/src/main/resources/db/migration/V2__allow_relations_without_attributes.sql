ALTER TABLE user_entity_relations
    ALTER COLUMN edge_timestamp DROP NOT NULL,
    ALTER COLUMN edge_boolean_value DROP NOT NULL;
