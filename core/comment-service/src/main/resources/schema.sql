-- Комментарии к событиям
CREATE TABLE IF NOT EXISTS comments (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    text           VARCHAR(4000) NOT NULL,
    published_date TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    edited_on      TIMESTAMP WITHOUT TIME ZONE,
    author_id      BIGINT NOT NULL,
    event_id       BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_comments_event_id  ON comments (event_id);
CREATE INDEX IF NOT EXISTS idx_comments_author_id ON comments (author_id);