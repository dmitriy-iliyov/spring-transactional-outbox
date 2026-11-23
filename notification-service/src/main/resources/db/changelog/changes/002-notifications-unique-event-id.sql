--liquibase formatted sql

--changeset notification-service:002-notifications-unique-event-id
ALTER TABLE notifications
    ADD CONSTRAINT notifications_event_id_uk UNIQUE (event_id);
