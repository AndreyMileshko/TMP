-- COL-010 / TMP-SPEC-015 §16.1–§16.3 / ADR-039
-- Reference Data Colors schema. No seed data (seed is COL-011 / V59).
CREATE SCHEMA IF NOT EXISTS reference;

CREATE TABLE reference.colors (
    id UUID PRIMARY KEY,
    code VARCHAR(31) NOT NULL,
    code_key VARCHAR(31) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID NULL,
    updated_by UUID NULL,
    CONSTRAINT uk_colors_code_key UNIQUE (code_key),
    CONSTRAINT chk_colors_code_non_blank CHECK (btrim(code) <> ''),
    CONSTRAINT chk_colors_code_no_slash CHECK (position('/' in code) = 0 AND position('\' in code) = 0),
    CONSTRAINT chk_colors_version CHECK (version >= 0)
);

CREATE TABLE reference.so7_color_mappings (
    id UUID PRIMARY KEY,
    source_text VARCHAR(64) NOT NULL,
    source_key VARCHAR(64) NOT NULL,
    inside_color_id UUID NOT NULL,
    outside_color_id UUID NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID NULL,
    updated_by UUID NULL,
    CONSTRAINT uk_so7_color_mappings_source_key UNIQUE (source_key),
    CONSTRAINT fk_so7_color_mappings_inside_color
        FOREIGN KEY (inside_color_id) REFERENCES reference.colors (id) ON DELETE RESTRICT,
    CONSTRAINT fk_so7_color_mappings_outside_color
        FOREIGN KEY (outside_color_id) REFERENCES reference.colors (id) ON DELETE RESTRICT,
    CONSTRAINT chk_so7_color_mappings_source_non_blank CHECK (btrim(source_text) <> ''),
    CONSTRAINT chk_so7_color_mappings_version CHECK (version >= 0)
);

CREATE INDEX idx_so7_color_mappings_inside_color_id
    ON reference.so7_color_mappings (inside_color_id);

CREATE INDEX idx_so7_color_mappings_outside_color_id
    ON reference.so7_color_mappings (outside_color_id);

CREATE TABLE reference.color_matching_settings (
    id SMALLINT PRIMARY KEY,
    automatic_matching_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID NULL,
    updated_by UUID NULL,
    CONSTRAINT chk_color_matching_settings_singleton CHECK (id = 1),
    CONSTRAINT chk_color_matching_settings_version CHECK (version >= 0)
);
