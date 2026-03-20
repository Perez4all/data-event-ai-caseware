-- ============================================================
-- Change Script – applied between two ingestion runs
-- ============================================================

-- 1) Update 5 existing cases: change status and set updated_at=now()
UPDATE cases SET status = 'closed',      updated_at = now() WHERE case_id = 1;   -- was open
UPDATE cases SET status = 'closed',      updated_at = now() WHERE case_id = 3;   -- was in_progress
UPDATE cases SET status = 'in_progress', updated_at = now() WHERE case_id = 9;   -- was open
UPDATE cases SET status = 'closed',      updated_at = now() WHERE case_id = 17;  -- was in_progress
UPDATE cases SET status = 'in_progress', updated_at = now() WHERE case_id = 30;  -- was open

-- 2) Insert 2 new customers with updated_at=now()
INSERT INTO customers (name, email, country, updated_at) VALUES
('Emma Johansson', 'emma.johansson@example.com', 'SE', now()),
('Rafael Santos',  'rafael.santos@example.com',  'BR', now());

-- 3) Insert 10 new cases linked to existing and new customers with updated_at=now()
INSERT INTO cases (customer_id, title, description, status, updated_at) VALUES
-- 4 cases for new customers (customer_id 31 and 32)
(31, 'Billing setup for new account',            'Configure billing profile and payment method for new customer.',          'open',        now()),
(31, 'Compliance onboarding review',             'Initial compliance review required for new customer onboarding.',        'open',        now()),
(32, 'Security baseline assessment',             'Conduct security baseline assessment for newly onboarded customer.',     'in_progress', now()),
(32, 'Tax registration verification',            'Verify tax registration details for new customer account.',              'open',        now()),
-- 6 cases for existing customers
(5,  'Fraud alert: credential stuffing attempt', 'Automated credential stuffing attack detected on customer portal.',      'open',        now()),
(10, 'Audit preparation for SOX compliance',     'Gather evidence and documentation for upcoming SOX audit.',              'in_progress', now()),
(15, 'Escalation: recurring integration failure','Recurring integration failures with payment gateway require escalation.', 'open',        now()),
(20, 'Data export for regulatory request',       'Regulatory body requested full data export within 48 hours.',            'open',        now()),
(25, 'License upgrade evaluation',               'Evaluate license upgrade options to support additional modules.',        'in_progress', now()),
(30, 'Refund batch processing error',            'Batch refund processing job failing with timeout errors.',               'open',        now());

