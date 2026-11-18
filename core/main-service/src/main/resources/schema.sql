-- Категории событий
CREATE TABLE IF NOT EXISTS categories (
    id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    CONSTRAINT uq_category_name UNIQUE (name)
);

-- События
CREATE TABLE IF NOT EXISTS events (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    annotation         VARCHAR(2000) NOT NULL,
    category_id        BIGINT        NOT NULL REFERENCES categories (id),
    description        VARCHAR(7000) NOT NULL,
    event_date         TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    initiator_id       BIGINT        NOT NULL,
    lat                FLOAT,
    lon                FLOAT,
    paid               BOOLEAN       NOT NULL DEFAULT FALSE,
    participant_limit  INTEGER       NOT NULL DEFAULT 0,
    request_moderation BOOLEAN       NOT NULL DEFAULT TRUE,
    title              VARCHAR(120)  NOT NULL,
    state              VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
    created_on         TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    published_on       TIMESTAMP WITHOUT TIME ZONE
);

-- Подборки событий
CREATE TABLE IF NOT EXISTS compilations (
    id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title  VARCHAR(50) NOT NULL,
    pinned BOOLEAN     NOT NULL DEFAULT FALSE
);

-- Таблица-связка подборок и событий (many-to-many)
CREATE TABLE IF NOT EXISTS compilation_events (
    compilation_id BIGINT NOT NULL REFERENCES compilations (id) ON DELETE CASCADE,
    event_id       BIGINT NOT NULL REFERENCES events (id),
    PRIMARY KEY (compilation_id, event_id)
);

-- Функция реализации работы с координатами
--Эта функция принимает на вход координаты (градусы широты и долготы) двух точек и вычисляет дистанцию между ними.
--Её можно использовать для упрощённой проверки, попадает ли локация проведения события в заданную область.
--Если дистанция от локации события до центра окружности (выбранной области) не превышает радиуса этой окружности,
--значит, оно проходит в выборку.
--CREATE OR REPLACE FUNCTION distance(lat1 float, lon1 float, lat2 float, lon2 float)
--    RETURNS float
--AS
--'
--declare
--    dist float = 0;
--    rad_lat1 float;
--    rad_lat2 float;
--    theta float;
--    rad_theta float;
--BEGIN
--    IF lat1 = lat2 AND lon1 = lon2
--    THEN
--        RETURN dist;
--    ELSE
--        -- переводим градусы широты в радианы
--        rad_lat1 = pi() * lat1 / 180;
--        -- переводим градусы долготы в радианы
--        rad_lat2 = pi() * lat2 / 180;
--        -- находим разность долгот
--        theta = lon1 - lon2;
--        -- переводим градусы в радианы
--        rad_theta = pi() * theta / 180;
--        -- находим длину ортодромии
--        dist = sin(rad_lat1) * sin(rad_lat2) + cos(rad_lat1) * cos(rad_lat2) * cos(rad_theta);
--
--        IF dist > 1
--            THEN dist = 1;
--        END IF;
--
--        dist = acos(dist);
--        -- переводим радианы в градусы
--        dist = dist * 180 / pi();
--        -- переводим градусы в километры
--        dist = dist * 60 * 1.8524;
--
--        RETURN dist;
--    END IF;
--END;
--'
--LANGUAGE PLPGSQL;
