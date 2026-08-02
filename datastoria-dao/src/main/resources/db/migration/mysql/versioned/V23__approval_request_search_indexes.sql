ALTER TABLE ds_approval_request
    ADD KEY idx_approval_request_created
        (tenant_id, created_at DESC, id DESC),
    ADD KEY idx_approval_request_status_created
        (tenant_id, status, created_at DESC, id DESC),
    ADD KEY idx_approval_request_type_created
        (tenant_id, work_order_type_key, created_at DESC, id DESC),
    ADD KEY idx_approval_request_applicant_created
        (tenant_id, applicant_user_id, created_at DESC, id DESC);
