-- Generic per-user UI preferences. Opaque string values; keyed by immutable user id.
CREATE TABLE security.user_ui_preferences (
    user_id UUID NOT NULL,
    namespace VARCHAR(128) NOT NULL,
    preference_key VARCHAR(128) NOT NULL,
    preference_version INTEGER NOT NULL,
    value_text TEXT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_user_ui_preferences PRIMARY KEY (user_id, namespace, preference_key),
    CONSTRAINT fk_user_ui_preferences_user FOREIGN KEY (user_id) REFERENCES security.users (id),
    CONSTRAINT chk_user_ui_preferences_version CHECK (preference_version >= 1)
);
