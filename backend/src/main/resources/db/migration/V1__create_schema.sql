-- WITH WON schema baseline: 27 tables / 309 columns / 63 foreign keys
-- Source: updated Codex handover DB CSVs.
-- Structure only; check_items seed data belongs in a later migration after team agreement.

CREATE TABLE customers (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  bank_customer_key VARCHAR(100) NOT NULL UNIQUE,
  login_id VARCHAR(80) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  name VARCHAR(100) NOT NULL,
  phone VARCHAR(20) NOT NULL,
  active BOOLEAN NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE employees (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  login_id VARCHAR(80) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  name VARCHAR(100) NOT NULL,
  active BOOLEAN NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE auth_sessions (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  customer_id BIGINT,
  employee_id BIGINT,
  token_hash CHAR(64) NOT NULL UNIQUE,
  last_activity_at DATETIME(6) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  revoked_at DATETIME(6),
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE accounts (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  customer_id BIGINT NOT NULL,
  account_number VARCHAR(40) NOT NULL UNIQUE,
  balance DECIMAL(18,0) NOT NULL CHECK(balance >= 0),
  active BOOLEAN NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE transactions (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  account_id BIGINT NOT NULL,
  decision_id BIGINT UNIQUE,
  reversal_of_id BIGINT UNIQUE,
  direction VARCHAR(16) NOT NULL,
  amount DECIMAL(18,0) NOT NULL CHECK(amount > 0),
  occurred_at DATETIME(6) NOT NULL,
  counterparty_bank VARCHAR(20),
  counterparty_account VARCHAR(40),
  counterparty_type VARCHAR(32),
  channel VARCHAR(32) NOT NULL,
  transaction_type VARCHAR(32) NOT NULL,
  payment_stop_at DATETIME(6),
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE bank_security_events (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  customer_id BIGINT NOT NULL,
  transaction_id BIGINT,
  event_type VARCHAR(40) NOT NULL,
  occurred_at DATETIME(6) NOT NULL,
  event_data JSON NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE incidents (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  customer_id BIGINT NOT NULL,
  assigned_employee_id BIGINT,
  incident_number VARCHAR(40) NOT NULL UNIQUE,
  status VARCHAR(32) NOT NULL,
  description TEXT,
  recognized_at DATETIME(6),
  submitted_at DATETIME(6),
  assigned_at DATETIME(6),
  closed_at DATETIME(6),
  version BIGINT NOT NULL DEFAULT 0,
  submit_key VARCHAR(100),
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  submit_request_hash CHAR(64)
);

CREATE TABLE incident_transactions (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  incident_id BIGINT NOT NULL,
  transaction_id BIGINT NOT NULL,
  refund_target BOOLEAN NOT NULL,
  compensation_target BOOLEAN NOT NULL,
  review_status VARCHAR(32) NOT NULL,
  review_reason TEXT,
  reviewed_by BIGINT,
  reviewed_at DATETIME(6),
  reserved_transaction_id BIGINT UNIQUE,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE applications (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  incident_id BIGINT NOT NULL,
  application_type VARCHAR(24) NOT NULL,
  status VARCHAR(32) NOT NULL,
  submitted_at DATETIME(6),
  closed_at DATETIME(6),
  payment_stop_at DATETIME(6),
  initial_due_at DATETIME(6),
  extended_due_at DATETIME(6),
  version BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  submit_key VARCHAR(100),
  submit_request_hash CHAR(64)
);

CREATE TABLE application_form_versions (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  application_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  version_type VARCHAR(24) NOT NULL,
  form_data JSON NOT NULL,
  schema_version VARCHAR(20) NOT NULL,
  submitted_at DATETIME(6),
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE application_consents (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  incident_id BIGINT NOT NULL,
  application_id BIGINT,
  form_version_id BIGINT,
  consent_type VARCHAR(32) NOT NULL,
  policy_version VARCHAR(40) NOT NULL,
  agreed_at DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  policy_text TEXT NOT NULL
);

CREATE TABLE status_history (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  incident_id BIGINT NOT NULL,
  application_id BIGINT,
  employee_id BIGINT,
  customer_id BIGINT,
  event_type VARCHAR(32) NOT NULL,
  from_status VARCHAR(32),
  to_status VARCHAR(32),
  reason TEXT,
  event_data JSON,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE evidence_files (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  incident_id BIGINT NOT NULL,
  document_type VARCHAR(40) NOT NULL,
  refund_target BOOLEAN NOT NULL,
  compensation_target BOOLEAN NOT NULL,
  status VARCHAR(24) NOT NULL,
  classification_source VARCHAR(24) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE evidence_versions (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  evidence_file_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  storage_key VARCHAR(500) NOT NULL UNIQUE,
  original_name VARCHAR(255) NOT NULL,
  mime_type VARCHAR(100) NOT NULL,
  size_bytes BIGINT NOT NULL,
  sha256 CHAR(64) NOT NULL,
  status VARCHAR(24) NOT NULL,
  extraction_status VARCHAR(24) NOT NULL,
  extracted_chunks JSON,
  masked_chunks JSON,
  extraction_error VARCHAR(255),
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE ai_jobs (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  incident_id BIGINT NOT NULL,
  requested_by BIGINT,
  job_type VARCHAR(32) NOT NULL,
  status VARCHAR(24) NOT NULL,
  input_fingerprint CHAR(64) NOT NULL,
  request_key VARCHAR(100) NOT NULL UNIQUE,
  request_data JSON,
  call_history JSON,
  masking_status VARCHAR(24) NOT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  error_code VARCHAR(60),
  started_at DATETIME(6),
  finished_at DATETIME(6),
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  request_hash CHAR(64) NOT NULL,
  requested_customer_id BIGINT,
  format_retry_count INT NOT NULL DEFAULT 0,
  result_data JSON
);

CREATE TABLE ai_analysis_versions (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  incident_id BIGINT NOT NULL,
  ai_job_id BIGINT NOT NULL UNIQUE,
  version_no INT NOT NULL,
  source_incident_version BIGINT NOT NULL,
  analysis_data JSON NOT NULL,
  review_data JSON,
  review_status VARCHAR(24) NOT NULL,
  reviewed_by BIGINT,
  reviewed_at DATETIME(6),
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE timeline_events (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  incident_id BIGINT NOT NULL,
  analysis_version_id BIGINT,
  bank_event_id BIGINT,
  previous_event_id BIGINT,
  edited_by BIGINT,
  occurred_at DATETIME(6),
  time_precision VARCHAR(24) NOT NULL,
  source_type VARCHAR(24) NOT NULL,
  description TEXT NOT NULL,
  evidence_data JSON,
  edit_reason TEXT,
  excluded BOOLEAN NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE application_reviews (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  incident_id BIGINT NOT NULL,
  application_id BIGINT,
  form_version_id BIGINT,
  employee_id BIGINT NOT NULL,
  review_type VARCHAR(32) NOT NULL,
  status VARCHAR(24) NOT NULL,
  review_data JSON,
  note TEXT,
  confirmed_at DATETIME(6),
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  previous_review_id BIGINT,
  version_no INT NOT NULL DEFAULT 1
);

CREATE TABLE supplement_requests (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  incident_id BIGINT NOT NULL,
  employee_id BIGINT NOT NULL,
  refund_target BOOLEAN NOT NULL,
  compensation_target BOOLEAN NOT NULL,
  message TEXT NOT NULL,
  required_items JSON,
  status VARCHAR(24) NOT NULL,
  due_at DATETIME(6),
  sent_at DATETIME(6),
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  request_key VARCHAR(100) UNIQUE,
  request_hash CHAR(64)
);

CREATE TABLE supplement_responses (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  supplement_request_id BIGINT NOT NULL,
  customer_id BIGINT NOT NULL,
  message TEXT,
  evidence_version_ids JSON,
  form_version_ids JSON,
  submitted_at DATETIME(6) NOT NULL,
  request_key VARCHAR(100) NOT NULL UNIQUE,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  request_hash CHAR(64) NOT NULL
);

CREATE TABLE amendment_requests (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  incident_id BIGINT NOT NULL,
  application_id BIGINT,
  customer_id BIGINT NOT NULL,
  reviewed_by BIGINT,
  request_type VARCHAR(32) NOT NULL,
  status VARCHAR(24) NOT NULL,
  reason TEXT NOT NULL,
  change_data JSON NOT NULL,
  review_reason TEXT,
  reviewed_at DATETIME(6),
  request_key VARCHAR(100) NOT NULL UNIQUE,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  request_hash CHAR(64) NOT NULL
);

CREATE TABLE refund_result_records (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  incident_id BIGINT NOT NULL,
  employee_id BIGINT NOT NULL,
  fraud_bank_code VARCHAR(20) NOT NULL,
  fraud_account_number VARCHAR(40) NOT NULL,
  refund_amount DECIMAL(18,0) NOT NULL CHECK(refund_amount >= 0),
  notice_date DATE NOT NULL,
  version_no INT NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE check_items (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  item_code VARCHAR(80) NOT NULL,
  item_type VARCHAR(24) NOT NULL,
  stage VARCHAR(32) NOT NULL,
  sort_order INT NOT NULL,
  title VARCHAR(255) NOT NULL,
  scope VARCHAR(16) NOT NULL,
  source_description TEXT,
  rule_description TEXT,
  enabled BOOLEAN NOT NULL,
  source_document VARCHAR(255),
  question TEXT,
  answer_rule TEXT,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  answer_type VARCHAR(24),
  answer_options JSON,
  required BOOLEAN NOT NULL DEFAULT FALSE,
  schema_version VARCHAR(20) NOT NULL
);

CREATE TABLE compensation_calculations (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  application_id BIGINT NOT NULL,
  employee_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  source_incident_version BIGINT NOT NULL,
  eligible_amount DECIMAL(18,0) NOT NULL CHECK(eligible_amount >= 0),
  allocated_refund DECIMAL(18,0) NOT NULL CHECK(allocated_refund >= 0),
  base_amount DECIMAL(18,0) NOT NULL CHECK(base_amount >= 0),
  rate DECIMAL(5,2) NOT NULL CHECK(rate BETWEEN 0 AND 100),
  compensation_amount DECIMAL(18,0) NOT NULL CHECK(compensation_amount >= 0),
  check_results JSON NOT NULL,
  calculation_data JSON NOT NULL,
  reason TEXT NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  policy_version VARCHAR(20) NOT NULL
);

CREATE TABLE decisions (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  application_id BIGINT NOT NULL,
  employee_id BIGINT NOT NULL,
  calculation_id BIGINT,
  previous_decision_id BIGINT,
  version_no INT NOT NULL,
  decision_type VARCHAR(32) NOT NULL,
  reason TEXT NOT NULL,
  internal_evidence JSON,
  notice_text TEXT,
  amount DECIMAL(18,0) CHECK(amount >= 0),
  decided_at DATETIME(6) NOT NULL,
  request_key VARCHAR(100) NOT NULL UNIQUE,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  request_hash CHAR(64) NOT NULL,
  payout_account_id BIGINT
);

CREATE TABLE notifications (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  customer_id BIGINT NOT NULL,
  incident_id BIGINT NOT NULL,
  application_id BIGINT,
  notification_type VARCHAR(32) NOT NULL,
  title VARCHAR(150) NOT NULL,
  message TEXT NOT NULL,
  read_at DATETIME(6),
  event_key VARCHAR(100) NOT NULL UNIQUE,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE audit_logs (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  incident_id BIGINT,
  customer_id BIGINT,
  employee_id BIGINT,
  action VARCHAR(40) NOT NULL,
  target_type VARCHAR(40) NOT NULL,
  target_id BIGINT,
  detail JSON,
  request_id VARCHAR(100),
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

-- Foreign keys are added after table creation to support circular/self references.
ALTER TABLE auth_sessions ADD CONSTRAINT fk_auth_sessions_customer_id FOREIGN KEY (customer_id) REFERENCES customers(id);
ALTER TABLE auth_sessions ADD CONSTRAINT fk_auth_sessions_employee_id FOREIGN KEY (employee_id) REFERENCES employees(id);
ALTER TABLE accounts ADD CONSTRAINT fk_accounts_customer_id FOREIGN KEY (customer_id) REFERENCES customers(id);
ALTER TABLE transactions ADD CONSTRAINT fk_transactions_account_id FOREIGN KEY (account_id) REFERENCES accounts(id);
ALTER TABLE transactions ADD CONSTRAINT fk_transactions_decision_id FOREIGN KEY (decision_id) REFERENCES decisions(id);
ALTER TABLE transactions ADD CONSTRAINT fk_transactions_reversal_of_id FOREIGN KEY (reversal_of_id) REFERENCES transactions(id);
ALTER TABLE bank_security_events ADD CONSTRAINT fk_bank_security_events_customer_id FOREIGN KEY (customer_id) REFERENCES customers(id);
ALTER TABLE bank_security_events ADD CONSTRAINT fk_bank_security_events_transaction_id FOREIGN KEY (transaction_id) REFERENCES transactions(id);
ALTER TABLE incidents ADD CONSTRAINT fk_incidents_customer_id FOREIGN KEY (customer_id) REFERENCES customers(id);
ALTER TABLE incidents ADD CONSTRAINT fk_incidents_assigned_employee_id FOREIGN KEY (assigned_employee_id) REFERENCES employees(id);
ALTER TABLE incident_transactions ADD CONSTRAINT fk_incident_transactions_incident_id FOREIGN KEY (incident_id) REFERENCES incidents(id);
ALTER TABLE incident_transactions ADD CONSTRAINT fk_incident_transactions_transaction_id FOREIGN KEY (transaction_id) REFERENCES transactions(id);
ALTER TABLE incident_transactions ADD CONSTRAINT fk_incident_transactions_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES employees(id);
ALTER TABLE applications ADD CONSTRAINT fk_applications_incident_id FOREIGN KEY (incident_id) REFERENCES incidents(id);
ALTER TABLE application_form_versions ADD CONSTRAINT fk_application_form_versions_application_id FOREIGN KEY (application_id) REFERENCES applications(id);
ALTER TABLE application_consents ADD CONSTRAINT fk_application_consents_incident_id FOREIGN KEY (incident_id) REFERENCES incidents(id);
ALTER TABLE application_consents ADD CONSTRAINT fk_application_consents_application_id FOREIGN KEY (application_id) REFERENCES applications(id);
ALTER TABLE application_consents ADD CONSTRAINT fk_application_consents_form_version_id FOREIGN KEY (form_version_id) REFERENCES application_form_versions(id);
ALTER TABLE status_history ADD CONSTRAINT fk_status_history_incident_id FOREIGN KEY (incident_id) REFERENCES incidents(id);
ALTER TABLE status_history ADD CONSTRAINT fk_status_history_application_id FOREIGN KEY (application_id) REFERENCES applications(id);
ALTER TABLE status_history ADD CONSTRAINT fk_status_history_employee_id FOREIGN KEY (employee_id) REFERENCES employees(id);
ALTER TABLE status_history ADD CONSTRAINT fk_status_history_customer_id FOREIGN KEY (customer_id) REFERENCES customers(id);
ALTER TABLE evidence_files ADD CONSTRAINT fk_evidence_files_incident_id FOREIGN KEY (incident_id) REFERENCES incidents(id);
ALTER TABLE evidence_versions ADD CONSTRAINT fk_evidence_versions_evidence_file_id FOREIGN KEY (evidence_file_id) REFERENCES evidence_files(id);
ALTER TABLE ai_jobs ADD CONSTRAINT fk_ai_jobs_incident_id FOREIGN KEY (incident_id) REFERENCES incidents(id);
ALTER TABLE ai_jobs ADD CONSTRAINT fk_ai_jobs_requested_by FOREIGN KEY (requested_by) REFERENCES employees(id);
ALTER TABLE ai_jobs ADD CONSTRAINT fk_ai_jobs_requested_customer_id FOREIGN KEY (requested_customer_id) REFERENCES customers(id);
ALTER TABLE ai_analysis_versions ADD CONSTRAINT fk_ai_analysis_versions_incident_id FOREIGN KEY (incident_id) REFERENCES incidents(id);
ALTER TABLE ai_analysis_versions ADD CONSTRAINT fk_ai_analysis_versions_ai_job_id FOREIGN KEY (ai_job_id) REFERENCES ai_jobs(id);
ALTER TABLE ai_analysis_versions ADD CONSTRAINT fk_ai_analysis_versions_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES employees(id);
ALTER TABLE timeline_events ADD CONSTRAINT fk_timeline_events_incident_id FOREIGN KEY (incident_id) REFERENCES incidents(id);
ALTER TABLE timeline_events ADD CONSTRAINT fk_timeline_events_analysis_version_id FOREIGN KEY (analysis_version_id) REFERENCES ai_analysis_versions(id);
ALTER TABLE timeline_events ADD CONSTRAINT fk_timeline_events_bank_event_id FOREIGN KEY (bank_event_id) REFERENCES bank_security_events(id);
ALTER TABLE timeline_events ADD CONSTRAINT fk_timeline_events_previous_event_id FOREIGN KEY (previous_event_id) REFERENCES timeline_events(id);
ALTER TABLE timeline_events ADD CONSTRAINT fk_timeline_events_edited_by FOREIGN KEY (edited_by) REFERENCES employees(id);
ALTER TABLE application_reviews ADD CONSTRAINT fk_application_reviews_incident_id FOREIGN KEY (incident_id) REFERENCES incidents(id);
ALTER TABLE application_reviews ADD CONSTRAINT fk_application_reviews_application_id FOREIGN KEY (application_id) REFERENCES applications(id);
ALTER TABLE application_reviews ADD CONSTRAINT fk_application_reviews_form_version_id FOREIGN KEY (form_version_id) REFERENCES application_form_versions(id);
ALTER TABLE application_reviews ADD CONSTRAINT fk_application_reviews_employee_id FOREIGN KEY (employee_id) REFERENCES employees(id);
ALTER TABLE application_reviews ADD CONSTRAINT fk_application_reviews_previous_review_id FOREIGN KEY (previous_review_id) REFERENCES application_reviews(id);
ALTER TABLE supplement_requests ADD CONSTRAINT fk_supplement_requests_incident_id FOREIGN KEY (incident_id) REFERENCES incidents(id);
ALTER TABLE supplement_requests ADD CONSTRAINT fk_supplement_requests_employee_id FOREIGN KEY (employee_id) REFERENCES employees(id);
ALTER TABLE supplement_responses ADD CONSTRAINT fk_supplement_responses_supplement_request_id FOREIGN KEY (supplement_request_id) REFERENCES supplement_requests(id);
ALTER TABLE supplement_responses ADD CONSTRAINT fk_supplement_responses_customer_id FOREIGN KEY (customer_id) REFERENCES customers(id);
ALTER TABLE amendment_requests ADD CONSTRAINT fk_amendment_requests_incident_id FOREIGN KEY (incident_id) REFERENCES incidents(id);
ALTER TABLE amendment_requests ADD CONSTRAINT fk_amendment_requests_application_id FOREIGN KEY (application_id) REFERENCES applications(id);
ALTER TABLE amendment_requests ADD CONSTRAINT fk_amendment_requests_customer_id FOREIGN KEY (customer_id) REFERENCES customers(id);
ALTER TABLE amendment_requests ADD CONSTRAINT fk_amendment_requests_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES employees(id);
ALTER TABLE refund_result_records ADD CONSTRAINT fk_refund_result_records_incident_id FOREIGN KEY (incident_id) REFERENCES incidents(id);
ALTER TABLE refund_result_records ADD CONSTRAINT fk_refund_result_records_employee_id FOREIGN KEY (employee_id) REFERENCES employees(id);
ALTER TABLE compensation_calculations ADD CONSTRAINT fk_compensation_calculations_application_id FOREIGN KEY (application_id) REFERENCES applications(id);
ALTER TABLE compensation_calculations ADD CONSTRAINT fk_compensation_calculations_employee_id FOREIGN KEY (employee_id) REFERENCES employees(id);
ALTER TABLE decisions ADD CONSTRAINT fk_decisions_application_id FOREIGN KEY (application_id) REFERENCES applications(id);
ALTER TABLE decisions ADD CONSTRAINT fk_decisions_employee_id FOREIGN KEY (employee_id) REFERENCES employees(id);
ALTER TABLE decisions ADD CONSTRAINT fk_decisions_calculation_id FOREIGN KEY (calculation_id) REFERENCES compensation_calculations(id);
ALTER TABLE decisions ADD CONSTRAINT fk_decisions_previous_decision_id FOREIGN KEY (previous_decision_id) REFERENCES decisions(id);
ALTER TABLE decisions ADD CONSTRAINT fk_decisions_payout_account_id FOREIGN KEY (payout_account_id) REFERENCES accounts(id);
ALTER TABLE notifications ADD CONSTRAINT fk_notifications_customer_id FOREIGN KEY (customer_id) REFERENCES customers(id);
ALTER TABLE notifications ADD CONSTRAINT fk_notifications_incident_id FOREIGN KEY (incident_id) REFERENCES incidents(id);
ALTER TABLE notifications ADD CONSTRAINT fk_notifications_application_id FOREIGN KEY (application_id) REFERENCES applications(id);
ALTER TABLE audit_logs ADD CONSTRAINT fk_audit_logs_incident_id FOREIGN KEY (incident_id) REFERENCES incidents(id);
ALTER TABLE audit_logs ADD CONSTRAINT fk_audit_logs_customer_id FOREIGN KEY (customer_id) REFERENCES customers(id);
ALTER TABLE audit_logs ADD CONSTRAINT fk_audit_logs_employee_id FOREIGN KEY (employee_id) REFERENCES employees(id);

-- Composite uniqueness and domain constraints from the updated DB design.
ALTER TABLE auth_sessions ADD CONSTRAINT ck_auth_sessions_owner CHECK ((customer_id IS NOT NULL) <> (employee_id IS NOT NULL));
ALTER TABLE incidents ADD CONSTRAINT uk_incidents_customer_submit_key UNIQUE (customer_id, submit_key);
ALTER TABLE incident_transactions ADD CONSTRAINT uk_incident_transactions_incident_transaction UNIQUE (incident_id, transaction_id);
ALTER TABLE applications ADD CONSTRAINT uk_applications_incident_type UNIQUE (incident_id, application_type);
ALTER TABLE application_form_versions ADD CONSTRAINT uk_application_form_versions_application_version UNIQUE (application_id, version_no);
ALTER TABLE ai_analysis_versions ADD CONSTRAINT uk_ai_analysis_versions_incident_version UNIQUE (incident_id, version_no);
ALTER TABLE refund_result_records ADD CONSTRAINT uk_refund_results_incident_account_version UNIQUE (incident_id, fraud_bank_code, fraud_account_number, version_no);
ALTER TABLE check_items ADD CONSTRAINT uk_check_items_code_version UNIQUE (item_code, schema_version);
ALTER TABLE compensation_calculations ADD CONSTRAINT uk_compensation_calculations_application_version UNIQUE (application_id, version_no);
ALTER TABLE decisions ADD CONSTRAINT uk_decisions_application_version UNIQUE (application_id, version_no);

-- Baseline indexes from the updated design rules.
CREATE INDEX idx_incidents_customer_status ON incidents (customer_id, status);
CREATE INDEX idx_incidents_assignee_status_submitted ON incidents (assigned_employee_id, status, submitted_at);
CREATE INDEX idx_transactions_account_occurred ON transactions (account_id, occurred_at);
CREATE INDEX idx_ai_jobs_incident_status ON ai_jobs (incident_id, status);
CREATE INDEX idx_notifications_customer_read ON notifications (customer_id, read_at);
CREATE INDEX idx_audit_logs_incident_created ON audit_logs (incident_id, created_at);


