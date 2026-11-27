CREATE TABLE IF NOT EXISTS user_interactions (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    event_id   BIGINT NOT NULL,
    weight     DOUBLE PRECISION NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_user_event UNIQUE (user_id, event_id)
);
CREATE INDEX IF NOT EXISTS idx_interactions_user ON user_interactions (user_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_interactions_event ON user_interactions (event_id);

CREATE TABLE IF NOT EXISTS event_similarity (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_a    BIGINT NOT NULL,
    event_b    BIGINT NOT NULL,
    score      DOUBLE PRECISION NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_event_pair UNIQUE (event_a, event_b)
);
CREATE INDEX IF NOT EXISTS idx_similarity_a ON event_similarity (event_a);
CREATE INDEX IF NOT EXISTS idx_similarity_b ON event_similarity (event_b);
