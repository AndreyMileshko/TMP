-- Stage 2 Document Engine schema. Domain-independent document infrastructure.
CREATE SCHEMA IF NOT EXISTS documents;

CREATE TABLE documents.document_types (
    id VARCHAR(128) PRIMARY KEY,
    display_name VARCHAR(255) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE documents.documents (
    id UUID PRIMARY KEY,
    document_type_id VARCHAR(128) NOT NULL,
    document_number VARCHAR(64) NOT NULL,
    title VARCHAR(512) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    posted_at TIMESTAMP WITH TIME ZONE,
    closed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_document_status CHECK (status IN ('DRAFT', 'POSTED', 'CLOSED'))
);

CREATE UNIQUE INDEX uk_documents_number ON documents.documents (document_number);
CREATE INDEX idx_documents_type_status ON documents.documents (document_type_id, status);
CREATE INDEX idx_documents_created_at ON documents.documents (created_at DESC);

CREATE TABLE documents.document_lifecycle_journal (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    details TEXT NOT NULL DEFAULT ''
);

CREATE INDEX idx_document_lifecycle_journal_document_id
    ON documents.document_lifecycle_journal (document_id, occurred_at DESC);

CREATE TABLE documents.document_versions (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL,
    version_number BIGINT NOT NULL,
    title VARCHAR(512) NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_document_versions_document_version UNIQUE (document_id, version_number)
);

CREATE TABLE documents.document_files (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL,
    path VARCHAR(1024) NOT NULL,
    mime_type VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL,
    checksum VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_document_files_document_id ON documents.document_files (document_id);
