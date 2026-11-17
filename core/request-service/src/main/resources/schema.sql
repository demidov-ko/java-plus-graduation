-- Заявки на участие в событии
CREATE TABLE IF NOT EXISTS participation_requests (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id     BIGINT      NOT NULL,
    requester_id BIGINT      NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created      TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT uq_request UNIQUE (event_id, requester_id)
);

CREATE INDEX IF NOT EXISTS idx_requests_event_id ON participation_requests (event_id);