-- 사용자·은행
CREATE TABLE customers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bank_customer_key VARCHAR(100) NOT NULL,
    login_id VARCHAR(80) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_customers_bank_customer_key (bank_customer_key),
    UNIQUE KEY uq_customers_login_id (login_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE employees (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    login_id VARCHAR(80) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_employees_login_id (login_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE check_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_code VARCHAR(80) NOT NULL,
    item_type VARCHAR(24) NOT NULL,
    stage VARCHAR(32) NOT NULL,
    sort_order INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    scope VARCHAR(16) NOT NULL,
    source_description TEXT NULL,
    rule_description TEXT NULL,
    enabled BOOLEAN NOT NULL,
    source_document VARCHAR(255) NULL,
    question TEXT NULL,
    answer_rule TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    answer_type VARCHAR(24) NULL,
    answer_options JSON NULL,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    schema_version VARCHAR(20) NOT NULL,
    UNIQUE KEY uq_check_items_code_schema (item_code, schema_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    account_number VARCHAR(40) NOT NULL,
    balance DECIMAL(18,0) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_accounts_account_number (account_number),
    CONSTRAINT ck_accounts_balance CHECK (balance >= 0),
    CONSTRAINT fk_accounts_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE auth_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NULL,
    employee_id BIGINT NULL,
    token_hash CHAR(64) NOT NULL,
    last_activity_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_auth_sessions_token_hash (token_hash),
    CONSTRAINT fk_auth_sessions_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_auth_sessions_employee FOREIGN KEY (employee_id) REFERENCES employees(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 사건·신청
CREATE TABLE incidents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    assigned_employee_id BIGINT NULL,
    incident_number VARCHAR(40) NOT NULL,
    status VARCHAR(32) NOT NULL,
    description TEXT NULL,
    recognized_at DATETIME(6) NULL,
    submitted_at DATETIME(6) NULL,
    assigned_at DATETIME(6) NULL,
    closed_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    submit_key VARCHAR(100) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    submit_request_hash CHAR(64) NULL,
    UNIQUE KEY uq_incidents_incident_number (incident_number),
    UNIQUE KEY uq_incidents_customer_submit_key (customer_id, submit_key),
    CONSTRAINT fk_incidents_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_incidents_assigned_employee FOREIGN KEY (assigned_employee_id) REFERENCES employees(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE applications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id BIGINT NOT NULL,
    application_type VARCHAR(24) NOT NULL,
    status VARCHAR(32) NOT NULL,
    submitted_at DATETIME(6) NULL,
    closed_at DATETIME(6) NULL,
    payment_stop_at DATETIME(6) NULL,
    initial_due_at DATETIME(6) NULL,
    extended_due_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    submit_key VARCHAR(100) NULL,
    submit_request_hash CHAR(64) NULL,
    UNIQUE KEY uq_applications_incident_type (incident_id, application_type),
    CONSTRAINT fk_applications_incident FOREIGN KEY (incident_id) REFERENCES incidents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE application_form_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    version_type VARCHAR(24) NOT NULL,
    form_data JSON NOT NULL,
    schema_version VARCHAR(20) NOT NULL,
    submitted_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_form_versions_application_version (application_id, version_no),
    CONSTRAINT fk_form_versions_application FOREIGN KEY (application_id) REFERENCES applications(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE compensation_calculations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    source_incident_version BIGINT NOT NULL,
    eligible_amount DECIMAL(18,0) NOT NULL,
    allocated_refund DECIMAL(18,0) NOT NULL,
    base_amount DECIMAL(18,0) NOT NULL,
    rate DECIMAL(5,2) NOT NULL,
    compensation_amount DECIMAL(18,0) NOT NULL,
    check_results JSON NOT NULL,
    calculation_data JSON NOT NULL,
    reason TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    policy_version VARCHAR(20) NOT NULL,
    UNIQUE KEY uq_comp_calc_application_version (application_id, version_no),
    CONSTRAINT ck_comp_calc_eligible_amount CHECK (eligible_amount >= 0),
    CONSTRAINT ck_comp_calc_allocated_refund CHECK (allocated_refund >= 0),
    CONSTRAINT ck_comp_calc_base_amount CHECK (base_amount >= 0),
    CONSTRAINT ck_comp_calc_rate CHECK (rate BETWEEN 0 AND 100),
    CONSTRAINT ck_comp_calc_compensation_amount CHECK (compensation_amount >= 0),
    CONSTRAINT fk_comp_calc_application FOREIGN KEY (application_id) REFERENCES applications(id),
    CONSTRAINT fk_comp_calc_employee FOREIGN KEY (employee_id) REFERENCES employees(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE decisions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    calculation_id BIGINT NULL,
    previous_decision_id BIGINT NULL,
    version_no INT NOT NULL,
    decision_type VARCHAR(32) NOT NULL,
    reason TEXT NOT NULL,
    internal_evidence JSON NULL,
    notice_text TEXT NULL,
    amount DECIMAL(18,0) NULL,
    decided_at DATETIME(6) NOT NULL,
    request_key VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    request_hash CHAR(64) NOT NULL,
    payout_account_id BIGINT NULL,
    UNIQUE KEY uq_decisions_application_version (application_id, version_no),
    UNIQUE KEY uq_decisions_request_key (request_key),
    CONSTRAINT ck_decisions_amount CHECK (amount >= 0),
    CONSTRAINT fk_decisions_application FOREIGN KEY (application_id) REFERENCES applications(id),
    CONSTRAINT fk_decisions_employee FOREIGN KEY (employee_id) REFERENCES employees(id),
    CONSTRAINT fk_decisions_calculation FOREIGN KEY (calculation_id) REFERENCES compensation_calculations(id),
    CONSTRAINT fk_decisions_previous_decision FOREIGN KEY (previous_decision_id) REFERENCES decisions(id),
    CONSTRAINT fk_decisions_payout_account FOREIGN KEY (payout_account_id) REFERENCES accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    decision_id BIGINT NULL,
    reversal_of_id BIGINT NULL,
    direction VARCHAR(16) NOT NULL,
    amount DECIMAL(18,0) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    counterparty_bank VARCHAR(20) NULL,
    counterparty_account VARCHAR(40) NULL,
    counterparty_type VARCHAR(32) NULL,
    channel VARCHAR(32) NOT NULL,
    transaction_type VARCHAR(32) NOT NULL,
    payment_stop_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_transactions_decision_id (decision_id),
    UNIQUE KEY uq_transactions_reversal_of_id (reversal_of_id),
    CONSTRAINT ck_transactions_amount CHECK (amount > 0),
    CONSTRAINT fk_transactions_account FOREIGN KEY (account_id) REFERENCES accounts(id),
    CONSTRAINT fk_transactions_decision FOREIGN KEY (decision_id) REFERENCES decisions(id),
    CONSTRAINT fk_transactions_reversal_of FOREIGN KEY (reversal_of_id) REFERENCES transactions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bank_security_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    transaction_id BIGINT NULL,
    event_type VARCHAR(40) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    event_data JSON NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_bank_sec_events_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_bank_sec_events_transaction FOREIGN KEY (transaction_id) REFERENCES transactions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE incident_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id BIGINT NOT NULL,
    transaction_id BIGINT NOT NULL,
    refund_target BOOLEAN NOT NULL,
    compensation_target BOOLEAN NOT NULL,
    review_status VARCHAR(32) NOT NULL,
    review_reason TEXT NULL,
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME(6) NULL,
    reserved_transaction_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_incident_tx_incident_transaction (incident_id, transaction_id),
    UNIQUE KEY uq_incident_tx_reserved_transaction (reserved_transaction_id),
    CONSTRAINT fk_incident_tx_incident FOREIGN KEY (incident_id) REFERENCES incidents(id),
    CONSTRAINT fk_incident_tx_transaction FOREIGN KEY (transaction_id) REFERENCES transactions(id),
    CONSTRAINT fk_incident_tx_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES employees(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE application_consents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id BIGINT NOT NULL,
    application_id BIGINT NULL,
    form_version_id BIGINT NULL,
    consent_type VARCHAR(32) NOT NULL,
    policy_version VARCHAR(40) NOT NULL,
    agreed_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    policy_text TEXT NOT NULL,
    CONSTRAINT fk_consents_incident FOREIGN KEY (incident_id) REFERENCES incidents(id),
    CONSTRAINT fk_consents_application FOREIGN KEY (application_id) REFERENCES applications(id),
    CONSTRAINT fk_consents_form_version FOREIGN KEY (form_version_id) REFERENCES application_form_versions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE status_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id BIGINT NOT NULL,
    application_id BIGINT NULL,
    employee_id BIGINT NULL,
    customer_id BIGINT NULL,
    event_type VARCHAR(32) NOT NULL,
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NULL,
    reason TEXT NULL,
    event_data JSON NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_status_history_incident FOREIGN KEY (incident_id) REFERENCES incidents(id),
    CONSTRAINT fk_status_history_application FOREIGN KEY (application_id) REFERENCES applications(id),
    CONSTRAINT fk_status_history_employee FOREIGN KEY (employee_id) REFERENCES employees(id),
    CONSTRAINT fk_status_history_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 증빙
CREATE TABLE evidence_files (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id BIGINT NOT NULL,
    document_type VARCHAR(40) NOT NULL,
    refund_target BOOLEAN NOT NULL,
    compensation_target BOOLEAN NOT NULL,
    status VARCHAR(24) NOT NULL,
    classification_source VARCHAR(24) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_evidence_files_incident FOREIGN KEY (incident_id) REFERENCES incidents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE evidence_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    evidence_file_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    extraction_status VARCHAR(24) NOT NULL,
    extracted_chunks JSON NULL,
    masked_chunks JSON NULL,
    extraction_error VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_evidence_versions_storage_key (storage_key),
    UNIQUE KEY uq_evidence_versions_file_version (evidence_file_id, version_no),
    CONSTRAINT fk_evidence_versions_file FOREIGN KEY (evidence_file_id) REFERENCES evidence_files(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- AI
CREATE TABLE ai_jobs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id BIGINT NOT NULL,
    requested_by BIGINT NULL,
    job_type VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    input_fingerprint CHAR(64) NOT NULL,
    request_key VARCHAR(100) NOT NULL,
    request_data JSON NULL,
    call_history JSON NULL,
    masking_status VARCHAR(24) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    error_code VARCHAR(60) NULL,
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    request_hash CHAR(64) NOT NULL,
    requested_customer_id BIGINT NULL,
    format_retry_count INT NOT NULL DEFAULT 0,
    result_data JSON NULL,
    UNIQUE KEY uq_ai_jobs_request_key (request_key),
    CONSTRAINT fk_ai_jobs_incident FOREIGN KEY (incident_id) REFERENCES incidents(id),
    CONSTRAINT fk_ai_jobs_requested_by FOREIGN KEY (requested_by) REFERENCES employees(id),
    CONSTRAINT fk_ai_jobs_requested_customer FOREIGN KEY (requested_customer_id) REFERENCES customers(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ai_analysis_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id BIGINT NOT NULL,
    ai_job_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    source_incident_version BIGINT NOT NULL,
    analysis_data JSON NOT NULL,
    review_data JSON NULL,
    review_status VARCHAR(24) NOT NULL,
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_ai_analysis_ai_job (ai_job_id),
    UNIQUE KEY uq_ai_analysis_incident_version (incident_id, version_no),
    CONSTRAINT fk_ai_analysis_incident FOREIGN KEY (incident_id) REFERENCES incidents(id),
    CONSTRAINT fk_ai_analysis_ai_job FOREIGN KEY (ai_job_id) REFERENCES ai_jobs(id),
    CONSTRAINT fk_ai_analysis_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES employees(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE timeline_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id BIGINT NOT NULL,
    analysis_version_id BIGINT NULL,
    bank_event_id BIGINT NULL,
    previous_event_id BIGINT NULL,
    edited_by BIGINT NULL,
    occurred_at DATETIME(6) NULL,
    time_precision VARCHAR(24) NOT NULL,
    source_type VARCHAR(24) NOT NULL,
    description TEXT NOT NULL,
    evidence_data JSON NULL,
    edit_reason TEXT NULL,
    excluded BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_timeline_incident FOREIGN KEY (incident_id) REFERENCES incidents(id),
    CONSTRAINT fk_timeline_analysis_version FOREIGN KEY (analysis_version_id) REFERENCES ai_analysis_versions(id),
    CONSTRAINT fk_timeline_bank_event FOREIGN KEY (bank_event_id) REFERENCES bank_security_events(id),
    CONSTRAINT fk_timeline_previous_event FOREIGN KEY (previous_event_id) REFERENCES timeline_events(id),
    CONSTRAINT fk_timeline_edited_by FOREIGN KEY (edited_by) REFERENCES employees(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 직원 검토
CREATE TABLE application_reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id BIGINT NOT NULL,
    application_id BIGINT NULL,
    form_version_id BIGINT NULL,
    employee_id BIGINT NOT NULL,
    review_type VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    review_data JSON NULL,
    note TEXT NULL,
    confirmed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    previous_review_id BIGINT NULL,
    version_no INT NOT NULL DEFAULT 1,
    CONSTRAINT fk_app_reviews_incident FOREIGN KEY (incident_id) REFERENCES incidents(id),
    CONSTRAINT fk_app_reviews_application FOREIGN KEY (application_id) REFERENCES applications(id),
    CONSTRAINT fk_app_reviews_form_version FOREIGN KEY (form_version_id) REFERENCES application_form_versions(id),
    CONSTRAINT fk_app_reviews_employee FOREIGN KEY (employee_id) REFERENCES employees(id),
    CONSTRAINT fk_app_reviews_previous_review FOREIGN KEY (previous_review_id) REFERENCES application_reviews(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE supplement_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    refund_target BOOLEAN NOT NULL,
    compensation_target BOOLEAN NOT NULL,
    message TEXT NOT NULL,
    required_items JSON NULL,
    status VARCHAR(24) NOT NULL,
    due_at DATETIME(6) NULL,
    sent_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    request_key VARCHAR(100) NULL,
    request_hash CHAR(64) NULL,
    UNIQUE KEY uq_supplement_requests_request_key (request_key),
    CONSTRAINT fk_supplement_requests_incident FOREIGN KEY (incident_id) REFERENCES incidents(id),
    CONSTRAINT fk_supplement_requests_employee FOREIGN KEY (employee_id) REFERENCES employees(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE supplement_responses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    supplement_request_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    message TEXT NULL,
    evidence_version_ids JSON NULL,
    form_version_ids JSON NULL,
    submitted_at DATETIME(6) NOT NULL,
    request_key VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    request_hash CHAR(64) NOT NULL,
    UNIQUE KEY uq_supplement_responses_request_key (request_key),
    CONSTRAINT fk_supplement_responses_request FOREIGN KEY (supplement_request_id) REFERENCES supplement_requests(id),
    CONSTRAINT fk_supplement_responses_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE amendment_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id BIGINT NOT NULL,
    application_id BIGINT NULL,
    customer_id BIGINT NOT NULL,
    reviewed_by BIGINT NULL,
    request_type VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    reason TEXT NOT NULL,
    change_data JSON NOT NULL,
    review_reason TEXT NULL,
    reviewed_at DATETIME(6) NULL,
    request_key VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    request_hash CHAR(64) NOT NULL,
    UNIQUE KEY uq_amendment_requests_request_key (request_key),
    CONSTRAINT fk_amendment_requests_incident FOREIGN KEY (incident_id) REFERENCES incidents(id),
    CONSTRAINT fk_amendment_requests_application FOREIGN KEY (application_id) REFERENCES applications(id),
    CONSTRAINT fk_amendment_requests_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_amendment_requests_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES employees(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 환급·배상
CREATE TABLE refund_result_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    fraud_bank_code VARCHAR(20) NOT NULL,
    fraud_account_number VARCHAR(40) NOT NULL,
    refund_amount DECIMAL(18,0) NOT NULL,
    notice_date DATE NOT NULL,
    version_no INT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_refund_results_incident_account_version (incident_id, fraud_bank_code, fraud_account_number, version_no),
    CONSTRAINT ck_refund_results_amount CHECK (refund_amount >= 0),
    CONSTRAINT fk_refund_results_incident FOREIGN KEY (incident_id) REFERENCES incidents(id),
    CONSTRAINT fk_refund_results_employee FOREIGN KEY (employee_id) REFERENCES employees(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 공통
CREATE TABLE notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    incident_id BIGINT NOT NULL,
    application_id BIGINT NULL,
    notification_type VARCHAR(32) NOT NULL,
    title VARCHAR(150) NOT NULL,
    message TEXT NOT NULL,
    read_at DATETIME(6) NULL,
    event_key VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_notifications_event_key (event_key),
    CONSTRAINT fk_notifications_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_notifications_incident FOREIGN KEY (incident_id) REFERENCES incidents(id),
    CONSTRAINT fk_notifications_application FOREIGN KEY (application_id) REFERENCES applications(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id BIGINT NULL,
    customer_id BIGINT NULL,
    employee_id BIGINT NULL,
    action VARCHAR(40) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id BIGINT NULL,
    detail JSON NULL,
    request_id VARCHAR(100) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_audit_logs_incident FOREIGN KEY (incident_id) REFERENCES incidents(id),
    CONSTRAINT fk_audit_logs_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_audit_logs_employee FOREIGN KEY (employee_id) REFERENCES employees(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
