-- Intra-module referential integrity for Document Engine.
-- Database Specification §12 forbids cross-module PostgreSQL FKs; both tables are in schema documents.
ALTER TABLE documents.documents
    ADD CONSTRAINT fk_documents_document_type
    FOREIGN KEY (document_type_id) REFERENCES documents.document_types (id);
