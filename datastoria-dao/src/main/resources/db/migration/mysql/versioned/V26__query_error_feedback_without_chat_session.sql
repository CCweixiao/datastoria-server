-- Query AI-fix uses an ephemeral Agent session: neither ds_chat_session nor ds_chat_message is
-- persisted. Feedback is still tenant/user scoped by the application and uses the generated
-- session/message ids as an idempotency key, but it cannot have a chat-session foreign key.
ALTER TABLE ds_feedback_event
    DROP FOREIGN KEY fk_feedback_session;
