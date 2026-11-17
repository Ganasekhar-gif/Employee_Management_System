-- entity_id is nullable because an export is not tied to a single row.
CREATE TABLE audit_logs (
    id          BIGSERIAL   PRIMARY KEY,
    actor_id    BIGINT,
    entity_type VARCHAR(50) NOT NULL,
    entity_id   BIGINT,
    action      VARCHAR(50) NOT NULL,
    details     JSONB,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_id) REFERENCES users (id)
);

CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);
