-- ============================================================
-- Schema DDL
-- ============================================================

CREATE TABLE IF NOT EXISTS customers (
    customer_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        TEXT        NOT NULL,
    email       TEXT        NOT NULL UNIQUE,
    country     TEXT        NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS cases (
    case_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT      NOT NULL REFERENCES customers(customer_id),
    title       TEXT        NOT NULL,
    description TEXT        NOT NULL,
    status      TEXT        NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_customers_updated_at ON customers(updated_at);
CREATE INDEX IF NOT EXISTS idx_cases_updated_at     ON cases(updated_at);
CREATE INDEX IF NOT EXISTS idx_cases_customer_id    ON cases(customer_id);

-- ============================================================
-- Seed Data – Customers (30)
-- ============================================================

INSERT INTO customers (name, email, country, updated_at) VALUES
('Alice Johnson',      'alice.johnson@example.com',      'US', now() - interval '1 day'),
('Bob Smith',          'bob.smith@example.com',          'CA', now() - interval '2 days'),
('Carlos Rivera',      'carlos.rivera@example.com',      'MX', now() - interval '3 days'),
('Diana Müller',       'diana.muller@example.com',       'DE', now() - interval '4 days'),
('Elena Petrova',      'elena.petrova@example.com',      'RU', now() - interval '5 days'),
('Fatima Al-Sayed',    'fatima.alsayed@example.com',     'EG', now() - interval '6 days'),
('George Tanaka',      'george.tanaka@example.com',      'JP', now() - interval '7 days'),
('Hannah Lee',         'hannah.lee@example.com',         'KR', now() - interval '8 days'),
('Ivan Horvat',        'ivan.horvat@example.com',        'HR', now() - interval '9 days'),
('Julia Costa',        'julia.costa@example.com',        'BR', now() - interval '10 days'),
('Kofi Mensah',        'kofi.mensah@example.com',        'GH', now() - interval '11 days'),
('Liam O''Brien',      'liam.obrien@example.com',        'IE', now() - interval '12 days'),
('Maria Garcia',       'maria.garcia@example.com',       'ES', now() - interval '13 days'),
('Nina Johansson',     'nina.johansson@example.com',     'SE', now() - interval '14 days'),
('Oscar Dubois',       'oscar.dubois@example.com',       'FR', now() - interval '15 days'),
('Priya Sharma',       'priya.sharma@example.com',       'IN', now() - interval '16 days'),
('Qiang Wei',          'qiang.wei@example.com',          'CN', now() - interval '17 days'),
('Rosa Bianchi',       'rosa.bianchi@example.com',       'IT', now() - interval '18 days'),
('Samuel Adeyemi',     'samuel.adeyemi@example.com',     'NG', now() - interval '19 days'),
('Tanya Volkov',       'tanya.volkov@example.com',       'UA', now() - interval '20 days'),
('Umar Khan',          'umar.khan@example.com',          'PK', now() - interval '21 days'),
('Victoria Nguyen',    'victoria.nguyen@example.com',    'VN', now() - interval '22 days'),
('William Brown',      'william.brown@example.com',      'GB', now() - interval '23 days'),
('Xena Papadopoulos',  'xena.papadopoulos@example.com',  'GR', now() - interval '24 days'),
('Yuki Sato',          'yuki.sato@example.com',          'JP', now() - interval '25 days'),
('Zara Okonkwo',       'zara.okonkwo@example.com',       'NG', now() - interval '26 days'),
('Anders Lindqvist',   'anders.lindqvist@example.com',   'SE', now() - interval '27 days'),
('Bianca Ferreira',    'bianca.ferreira@example.com',    'BR', now() - interval '28 days'),
('Chen Wei',           'chen.wei@example.com',           'CN', now() - interval '29 days'),
('David Kim',          'david.kim@example.com',          'KR', now() - interval '30 days');

-- ============================================================
-- Seed Data – Cases (200)
--
-- Deterministic: each case is hand-assigned a customer_id (1-30),
-- a title containing diverse keywords (billing, audit, compliance,
-- security, onboarding, refund, escalation, SLA, migration, tax,
-- fraud, access, integration, performance, license, data, export,
-- report, notification, support), a description, a status
-- (open / in_progress / closed), and an updated_at spanning
-- the last 30 days.
-- ============================================================

INSERT INTO cases (customer_id, title, description, status, updated_at) VALUES
-- Cases 1-10
(1,  'Billing discrepancy on invoice #1001',        'Customer reports overcharge on monthly billing statement.',                     'open',        now() - interval '1 day'),
(1,  'Audit log access request',                    'Customer requests full audit trail export for Q4.',                            'open',        now() - interval '1 day 2 hours'),
(2,  'Compliance review needed for GDPR',           'Annual compliance check required for EU data residency.',                      'in_progress', now() - interval '2 days'),
(2,  'Security vulnerability report',               'Customer flagged a potential XSS security vulnerability on the portal.',       'open',        now() - interval '2 days 4 hours'),
(3,  'Onboarding new team members',                 'Request to onboard 15 new users to the platform.',                             'in_progress', now() - interval '3 days'),
(3,  'Refund request for duplicate charge',         'Duplicate billing detected; refund of $250 requested.',                        'open',        now() - interval '3 days 1 hour'),
(4,  'Escalation: SLA breach on ticket #887',       'Response SLA was breached; customer demands escalation.',                      'open',        now() - interval '4 days'),
(4,  'Data migration from legacy system',           'Migrate 50k records from legacy CRM to new platform.',                         'in_progress', now() - interval '4 days 3 hours'),
(5,  'Tax calculation error',                       'Sales tax computed incorrectly for NY-based transactions.',                     'open',        now() - interval '5 days'),
(5,  'Fraud detection alert',                       'Suspicious login attempts detected from unknown IPs.',                          'open',        now() - interval '5 days 6 hours'),

-- Cases 11-20
(6,  'Access control misconfiguration',             'Several users have elevated access permissions they should not have.',           'in_progress', now() - interval '6 days'),
(6,  'Integration failure with Salesforce',         'API integration with Salesforce returning 500 errors.',                          'open',        now() - interval '6 days 2 hours'),
(7,  'Performance degradation on dashboard',        'Dashboard load times exceeding 10 seconds for large datasets.',                  'in_progress', now() - interval '7 days'),
(7,  'License renewal reminder',                    'Enterprise license expires in 30 days; renewal discussion needed.',              'open',        now() - interval '7 days 5 hours'),
(8,  'Data export request for annual report',       'Customer needs bulk data export in CSV format for annual report.',               'closed',      now() - interval '8 days'),
(8,  'Report generation failure',                   'Monthly compliance report fails to generate due to timeout.',                    'open',        now() - interval '8 days 1 hour'),
(9,  'Notification delivery issue',                 'Email notifications not being delivered to customer inbox.',                     'in_progress', now() - interval '9 days'),
(9,  'Support ticket response delay',               'Customer waiting over 48 hours for initial support response.',                   'open',        now() - interval '9 days 3 hours'),
(10, 'Billing cycle change request',                'Customer wants to switch from monthly to annual billing cycle.',                 'in_progress', now() - interval '10 days'),
(10, 'Audit finding: missing encryption',           'Internal audit found data at rest not encrypted in staging environment.',        'open',        now() - interval '10 days 2 hours'),

-- Cases 21-30
(11, 'Compliance gap in SOC2 controls',             'Gap identified in SOC2 Type II access control requirements.',                   'open',        now() - interval '11 days'),
(11, 'Security patch deployment',                   'Critical security patch needs to be applied to production servers.',             'in_progress', now() - interval '11 days 4 hours'),
(12, 'Onboarding documentation update',             'Onboarding guide needs updates for new SSO workflow.',                          'closed',      now() - interval '12 days'),
(12, 'Refund processing delay',                     'Refund approved 10 days ago but not yet reflected in account.',                 'open',        now() - interval '12 days 1 hour'),
(13, 'Escalation: critical production outage',      'Production environment down; SLA P1 escalation triggered.',                     'open',        now() - interval '13 days'),
(13, 'Data integrity check failure',                'Nightly data integrity validation job reports mismatched checksums.',            'in_progress', now() - interval '13 days 5 hours'),
(14, 'Tax report discrepancy',                      'Annual tax report shows discrepancy with invoiced amounts.',                    'open',        now() - interval '14 days'),
(14, 'Fraud investigation case #2048',              'Confirmed fraudulent transactions flagged for investigation.',                  'in_progress', now() - interval '14 days 2 hours'),
(15, 'Access provisioning for contractor',          'Temporary access required for external contractor engagement.',                 'closed',      now() - interval '15 days'),
(15, 'Integration with QuickBooks',                 'Customer requests API integration with QuickBooks for billing sync.',           'open',        now() - interval '15 days 3 hours'),

-- Cases 31-40
(16, 'Performance tuning for batch jobs',           'Nightly batch processing exceeds the 4-hour maintenance window.',               'in_progress', now() - interval '16 days'),
(16, 'License audit for compliance',                'License audit required to ensure software compliance across departments.',      'open',        now() - interval '16 days 1 hour'),
(17, 'Data retention policy review',                'Review and update data retention policy to meet new regulations.',              'open',        now() - interval '17 days'),
(17, 'Export failure on large dataset',              'CSV export times out when dataset exceeds 500k rows.',                         'in_progress', now() - interval '17 days 4 hours'),
(18, 'Report customization request',                'Customer wants to add custom KPI fields to the monthly report.',               'open',        now() - interval '18 days'),
(18, 'Notification preferences not saving',         'Users report notification preference changes are not persisted.',               'in_progress', now() - interval '18 days 2 hours'),
(19, 'Support SLA metrics dashboard',               'Request for a dashboard displaying SLA compliance metrics.',                    'open',        now() - interval '19 days'),
(19, 'Billing address update issue',                'Billing address update not reflecting on generated invoices.',                  'closed',      now() - interval '19 days 6 hours'),
(20, 'Audit trail incomplete',                      'Audit logs missing entries for admin actions in the last 7 days.',              'open',        now() - interval '20 days'),
(20, 'Compliance training overdue',                 'Multiple users have not completed mandatory compliance training.',              'in_progress', now() - interval '20 days 1 hour'),

-- Cases 41-50
(21, 'Security review for new feature',             'Security assessment required before releasing new payment feature.',            'open',        now() - interval '21 days'),
(21, 'Onboarding workflow automation',              'Automate onboarding steps to reduce manual provisioning time.',                 'in_progress', now() - interval '21 days 3 hours'),
(22, 'Refund policy clarification',                 'Customer disputes refund denial; policy interpretation needed.',                'open',        now() - interval '22 days'),
(22, 'Escalation: data breach suspected',           'Potential data breach detected; security team escalation required.',            'open',        now() - interval '22 days 5 hours'),
(23, 'Migration plan for cloud transition',         'Develop migration roadmap from on-prem to AWS cloud infrastructure.',           'in_progress', now() - interval '23 days'),
(23, 'Tax exemption certificate upload',            'Customer needs to upload tax exemption certificate for billing.',               'closed',      now() - interval '23 days 2 hours'),
(24, 'Fraud alert: unusual spending pattern',       'Anomalous spending pattern detected on enterprise account.',                    'open',        now() - interval '24 days'),
(24, 'Access review for departing employee',        'Revoke access and audit activity for departing employee.',                      'in_progress', now() - interval '24 days 4 hours'),
(25, 'Integration error with Stripe',               'Stripe payment integration returning authentication errors.',                  'open',        now() - interval '25 days'),
(25, 'Performance issue in search module',          'Full-text search queries timing out under heavy load.',                         'in_progress', now() - interval '25 days 1 hour'),

-- Cases 51-60
(26, 'License over-deployment detected',            'Usage audit shows 120 active users on a 100-seat license.',                     'open',        now() - interval '26 days'),
(26, 'Data anonymization request',                  'Customer requests anonymization of test data in staging.',                      'closed',      now() - interval '26 days 3 hours'),
(27, 'Export schedule configuration',               'Set up automated weekly data export to customer SFTP server.',                  'in_progress', now() - interval '27 days'),
(27, 'Report delivery failure',                     'Scheduled report email bouncing due to invalid SMTP config.',                   'open',        now() - interval '27 days 5 hours'),
(28, 'Notification spam complaint',                 'Customer receiving duplicate notification emails for each event.',              'in_progress', now() - interval '28 days'),
(28, 'Support ticket categorization improvement',   'Improve auto-categorization of incoming support tickets.',                      'open',        now() - interval '28 days 2 hours'),
(29, 'Billing invoice formatting',                  'Invoice PDF formatting broken when company name exceeds 50 chars.',             'open',        now() - interval '29 days'),
(29, 'Audit compliance for ISO 27001',              'Prepare documentation for ISO 27001 audit in Q2.',                              'in_progress', now() - interval '29 days 1 hour'),
(30, 'Compliance violation flagged',                'Automated scan flagged a compliance violation in data handling.',               'open',        now() - interval '30 days'),
(30, 'Security token rotation',                     'API security tokens need scheduled rotation per policy.',                       'in_progress', now() - interval '30 days 4 hours'),

-- Cases 61-70
(1,  'Onboarding checklist review',                 'Review and update the new customer onboarding checklist.',                      'closed',      now() - interval '1 day 6 hours'),
(2,  'Refund issued incorrectly',                   'Refund applied to wrong billing account; correction needed.',                   'open',        now() - interval '2 days 6 hours'),
(3,  'SLA penalty calculation dispute',             'Customer disputes the calculated SLA penalty charges.',                         'in_progress', now() - interval '3 days 4 hours'),
(4,  'Migration rollback required',                 'Data migration introduced corrupted records; rollback needed.',                 'open',        now() - interval '4 days 6 hours'),
(5,  'Tax withholding discrepancy',                 'Withholding tax amount does not match customer expectations.',                  'open',        now() - interval '5 days 2 hours'),
(6,  'Fraud case closure report',                   'Generate summary report for resolved fraud investigation cases.',              'closed',      now() - interval '6 days 5 hours'),
(7,  'Access rights bulk update',                   'Bulk update access permissions for 200 users after reorg.',                    'in_progress', now() - interval '7 days 3 hours'),
(8,  'Integration health check',                    'Scheduled integration health check for all third-party APIs.',                 'open',        now() - interval '8 days 4 hours'),
(9,  'Performance baseline assessment',             'Establish performance baselines before infrastructure upgrade.',               'in_progress', now() - interval '9 days 6 hours'),
(10, 'License true-up reconciliation',              'Reconcile license usage against purchased entitlements.',                       'open',        now() - interval '10 days 5 hours'),

-- Cases 71-80
(11, 'Data classification project',                 'Classify all stored data assets per sensitivity level.',                        'in_progress', now() - interval '11 days 2 hours'),
(12, 'Export API rate limiting',                     'Implement rate limiting on the data export API endpoints.',                     'open',        now() - interval '12 days 4 hours'),
(13, 'Report access permissions',                   'Restrict report access to authorized finance team members only.',              'in_progress', now() - interval '13 days 2 hours'),
(14, 'Notification channel addition',               'Add Slack notification channel for critical system alerts.',                    'closed',      now() - interval '14 days 5 hours'),
(15, 'Support knowledge base update',               'Update knowledge base articles for new product release.',                       'in_progress', now() - interval '15 days 1 hour'),
(16, 'Billing dispute resolution',                  'Mediate billing dispute between customer and finance team.',                    'open',        now() - interval '16 days 4 hours'),
(17, 'Audit log retention extension',               'Extend audit log retention from 90 days to 1 year.',                           'in_progress', now() - interval '17 days 2 hours'),
(18, 'Compliance dashboard creation',               'Build compliance status dashboard for management review.',                     'open',        now() - interval '18 days 5 hours'),
(19, 'Security incident response drill',            'Schedule and conduct a security incident response drill.',                     'closed',      now() - interval '19 days 2 hours'),
(20, 'Onboarding SSO configuration',                'Configure SAML SSO for new enterprise customer onboarding.',                   'in_progress', now() - interval '20 days 4 hours'),

-- Cases 81-90
(21, 'Refund automation workflow',                   'Automate refund processing for amounts under $100.',                           'open',        now() - interval '21 days 1 hour'),
(22, 'Escalation matrix update',                    'Update the escalation matrix to reflect new team structure.',                  'in_progress', now() - interval '22 days 2 hours'),
(23, 'Migration validation testing',                'Execute validation test suite after database migration.',                       'open',        now() - interval '23 days 5 hours'),
(24, 'Tax rate update for 2026',                    'Update tax rate tables for fiscal year 2026 changes.',                         'in_progress', now() - interval '24 days 1 hour'),
(25, 'Fraud pattern analysis',                      'Analyze recent fraud patterns to update detection rules.',                     'open',        now() - interval '25 days 4 hours'),
(26, 'Access control policy revision',              'Revise access control policies for least-privilege compliance.',               'in_progress', now() - interval '26 days 1 hour'),
(27, 'Integration monitoring setup',                'Set up monitoring and alerting for all integration endpoints.',                'closed',      now() - interval '27 days 2 hours'),
(28, 'Performance load test results',               'Review results from recent load test and identify bottlenecks.',               'open',        now() - interval '28 days 5 hours'),
(29, 'License expiry notification',                 'Set up automated license expiry notification 60 days ahead.',                  'in_progress', now() - interval '29 days 4 hours'),
(30, 'Data backup verification',                    'Verify data backup integrity and restore procedures.',                         'open',        now() - interval '30 days 2 hours'),

-- Cases 91-100
(1,  'Export compliance documentation',             'Export all compliance documents for external auditor review.',                  'open',        now() - interval '1 day 8 hours'),
(2,  'Report scheduling enhancement',              'Allow users to schedule reports with custom cron expressions.',                 'in_progress', now() - interval '2 days 8 hours'),
(3,  'Notification template update',                'Update email notification templates to match new branding.',                   'closed',      now() - interval '3 days 6 hours'),
(4,  'Support chatbot integration',                 'Integrate AI chatbot for first-level support ticket triage.',                  'open',        now() - interval '4 days 8 hours'),
(5,  'Billing proration logic fix',                 'Fix billing proration calculation for mid-cycle plan changes.',                'in_progress', now() - interval '5 days 8 hours'),
(6,  'Audit committee presentation',               'Prepare audit findings presentation for board committee.',                     'open',        now() - interval '6 days 8 hours'),
(7,  'Compliance certificate renewal',              'Renew annual compliance certification before expiry.',                         'in_progress', now() - interval '7 days 8 hours'),
(8,  'Security penetration test',                   'Schedule annual security penetration test with vendor.',                        'open',        now() - interval '8 days 6 hours'),
(9,  'Onboarding feedback survey',                  'Deploy customer onboarding satisfaction feedback survey.',                      'closed',      now() - interval '9 days 8 hours'),
(10, 'Refund tracking dashboard',                   'Build dashboard to track refund requests and processing times.',               'open',        now() - interval '10 days 8 hours'),

-- Cases 101-110
(11, 'SLA compliance report generation',            'Generate monthly SLA compliance report for all customers.',                    'in_progress', now() - interval '11 days 6 hours'),
(12, 'Data migration dry run',                      'Perform dry run of data migration to identify potential issues.',              'open',        now() - interval '12 days 6 hours'),
(13, 'Tax filing integration',                      'Integrate with tax filing service for automated submissions.',                 'in_progress', now() - interval '13 days 8 hours'),
(14, 'Fraud rule engine update',                    'Update fraud detection rule engine with new pattern signatures.',              'open',        now() - interval '14 days 8 hours'),
(15, 'Access log analysis',                         'Analyze access logs for unusual after-hours login patterns.',                  'closed',      now() - interval '15 days 6 hours'),
(16, 'Integration API versioning',                  'Implement API versioning strategy for integration endpoints.',                'in_progress', now() - interval '16 days 6 hours'),
(17, 'Performance monitoring dashboard',            'Deploy real-time performance monitoring dashboard.',                            'open',        now() - interval '17 days 6 hours'),
(18, 'License compliance scan',                     'Run automated scan to verify license compliance across systems.',              'in_progress', now() - interval '18 days 8 hours'),
(19, 'Data sovereignty assessment',                 'Assess data storage locations against sovereignty requirements.',              'open',        now() - interval '19 days 8 hours'),
(20, 'Export format standardization',               'Standardize export file formats across all report types.',                     'closed',      now() - interval '20 days 6 hours'),

-- Cases 111-120
(21, 'Report drill-down feature',                   'Add drill-down capability to summary compliance reports.',                     'open',        now() - interval '21 days 6 hours'),
(22, 'Notification escalation rules',               'Configure escalation notification rules for unresolved tickets.',             'in_progress', now() - interval '22 days 8 hours'),
(23, 'Support ticket merge request',                'Merge duplicate support tickets from the same customer.',                      'open',        now() - interval '23 days 8 hours'),
(24, 'Billing credit application',                  'Apply service credit to billing account per SLA breach.',                      'closed',      now() - interval '24 days 6 hours'),
(25, 'Audit scope definition',                      'Define scope for upcoming annual internal audit cycle.',                       'in_progress', now() - interval '25 days 6 hours'),
(26, 'Compliance policy distribution',              'Distribute updated compliance policies to all employees.',                     'open',        now() - interval '26 days 6 hours'),
(27, 'Security awareness training',                 'Enroll all staff in updated security awareness training module.',              'in_progress', now() - interval '27 days 8 hours'),
(28, 'Onboarding automation phase 2',               'Implement phase 2 of automated onboarding workflow.',                          'open',        now() - interval '28 days 8 hours'),
(29, 'Refund exception approval',                   'Approve exception refund exceeding standard policy limit.',                    'closed',      now() - interval '29 days 6 hours'),
(30, 'Escalation SLA review',                       'Review and adjust escalation SLA timelines based on feedback.',               'in_progress', now() - interval '30 days 6 hours'),

-- Cases 121-130
(1,  'Migration progress tracking',                 'Track and report on cloud migration project milestones.',                      'in_progress', now() - interval '1 day 10 hours'),
(2,  'Tax exemption validation',                    'Validate customer tax exemption status against certificates.',                 'open',        now() - interval '2 days 10 hours'),
(3,  'Fraud case #3072 investigation',              'Investigate suspected fraudulent account creation pattern.',                   'open',        now() - interval '3 days 8 hours'),
(4,  'Access certification campaign',               'Launch quarterly access certification campaign for all managers.',             'in_progress', now() - interval '4 days 10 hours'),
(5,  'Integration error handling improvement',      'Improve error handling and retry logic for API integrations.',                 'open',        now() - interval '5 days 10 hours'),
(6,  'Performance SLA definition',                  'Define performance SLAs for critical customer-facing services.',               'closed',      now() - interval '6 days 10 hours'),
(7,  'License downgrade request',                   'Process customer request to downgrade from enterprise to standard.',           'open',        now() - interval '7 days 10 hours'),
(8,  'Data quality assessment',                     'Assess data quality across customer records for accuracy.',                    'in_progress', now() - interval '8 days 8 hours'),
(9,  'Export audit trail',                          'Export complete audit trail for regulatory examination.',                       'open',        now() - interval '9 days 10 hours'),
(10, 'Report localization',                         'Localize report templates for non-English-speaking customers.',                'in_progress', now() - interval '10 days 10 hours'),

-- Cases 131-140
(11, 'Notification delivery audit',                 'Audit notification delivery rates and identify failures.',                     'open',        now() - interval '11 days 8 hours'),
(12, 'Support process optimization',                'Optimize support ticket routing and assignment process.',                      'closed',      now() - interval '12 days 8 hours'),
(13, 'Billing reconciliation report',               'Generate billing reconciliation report for finance review.',                   'in_progress', now() - interval '13 days 10 hours'),
(14, 'Audit evidence collection',                   'Collect and organize evidence for external audit engagement.',                 'open',        now() - interval '14 days 10 hours'),
(15, 'Compliance risk assessment',                  'Conduct compliance risk assessment for new market entry.',                     'in_progress', now() - interval '15 days 8 hours'),
(16, 'Security certificate renewal',                'Renew SSL/TLS security certificates before expiration.',                       'open',        now() - interval '16 days 8 hours'),
(17, 'Onboarding metrics reporting',                'Report on onboarding completion rates and time-to-value.',                     'closed',      now() - interval '17 days 8 hours'),
(18, 'Refund workflow optimization',                'Streamline refund approval workflow to reduce processing time.',               'in_progress', now() - interval '18 days 10 hours'),
(19, 'Escalation notification failure',             'Escalation notifications not triggering for P1 incidents.',                    'open',        now() - interval '19 days 10 hours'),
(20, 'Data archival strategy',                      'Define data archival strategy for records older than 7 years.',                'in_progress', now() - interval '20 days 8 hours'),

-- Cases 141-150
(21, 'Tax audit preparation',                       'Prepare documentation and data extracts for upcoming tax audit.',              'open',        now() - interval '21 days 8 hours'),
(22, 'Fraud monitoring enhancement',                'Enhance real-time fraud monitoring with ML-based scoring.',                    'in_progress', now() - interval '22 days 10 hours'),
(23, 'Access request workflow',                     'Implement self-service access request and approval workflow.',                 'open',        now() - interval '23 days 10 hours'),
(24, 'Integration SLA monitoring',                  'Monitor integration partner SLAs and report breaches.',                        'closed',      now() - interval '24 days 8 hours'),
(25, 'Performance capacity planning',               'Plan infrastructure capacity for projected 50% growth.',                      'in_progress', now() - interval '25 days 8 hours'),
(26, 'License allocation optimization',             'Optimize license allocation to reduce unused seat costs.',                     'open',        now() - interval '26 days 8 hours'),
(27, 'Data lineage documentation',                  'Document data lineage for all critical reporting pipelines.',                  'in_progress', now() - interval '27 days 10 hours'),
(28, 'Export permission controls',                  'Add granular permission controls to data export functionality.',               'open',        now() - interval '28 days 10 hours'),
(29, 'Report performance optimization',             'Optimize slow-running compliance report queries.',                             'closed',      now() - interval '29 days 8 hours'),
(30, 'Notification preference migration',           'Migrate notification preferences to new microservice.',                        'in_progress', now() - interval '30 days 8 hours'),

-- Cases 151-160
(1,  'Billing system upgrade planning',             'Plan billing system upgrade to support multi-currency.',                       'open',        now() - interval '1 day 12 hours'),
(2,  'Audit risk scoring model',                    'Develop risk scoring model for audit prioritization.',                         'in_progress', now() - interval '2 days 12 hours'),
(3,  'Compliance training module',                  'Create interactive compliance training module for new hires.',                 'open',        now() - interval '3 days 10 hours'),
(4,  'Security header configuration',               'Configure security headers (CSP, HSTS) on all endpoints.',                    'closed',      now() - interval '4 days 12 hours'),
(5,  'Onboarding email sequence',                   'Design automated onboarding email drip sequence.',                             'in_progress', now() - interval '5 days 12 hours'),
(6,  'Refund dispute mediation',                    'Mediate refund dispute between customer and billing department.',              'open',        now() - interval '6 days 12 hours'),
(7,  'SLA tier restructuring',                      'Restructure SLA tiers to align with new service offerings.',                   'in_progress', now() - interval '7 days 12 hours'),
(8,  'Migration compatibility testing',             'Test application compatibility with new database version.',                    'open',        now() - interval '8 days 10 hours'),
(9,  'Tax compliance international',                'Ensure tax compliance for international billing operations.',                  'closed',      now() - interval '9 days 12 hours'),
(10, 'Fraud response playbook',                     'Create incident response playbook for fraud scenarios.',                       'in_progress', now() - interval '10 days 12 hours'),

-- Cases 161-170
(11, 'Access revocation automation',                'Automate access revocation on employee termination events.',                   'open',        now() - interval '11 days 10 hours'),
(12, 'Integration partner onboarding',              'Onboard new integration partner with API credentials.',                       'in_progress', now() - interval '12 days 10 hours'),
(13, 'Performance regression detected',             'Regression detected in API response times after deployment.',                 'open',        now() - interval '13 days 12 hours'),
(14, 'License model comparison',                    'Compare per-seat vs usage-based license model options.',                       'closed',      now() - interval '14 days 12 hours'),
(15, 'Data governance framework',                   'Establish data governance framework and assign data stewards.',               'in_progress', now() - interval '15 days 10 hours'),
(16, 'Export scheduling conflicts',                 'Resolve scheduling conflicts in concurrent data exports.',                    'open',        now() - interval '16 days 10 hours'),
(17, 'Report template versioning',                  'Implement version control for report templates.',                              'in_progress', now() - interval '17 days 10 hours'),
(18, 'Notification throttling rules',               'Implement notification throttling to prevent alert fatigue.',                  'open',        now() - interval '18 days 12 hours'),
(19, 'Support escalation training',                 'Train support staff on updated escalation procedures.',                        'closed',      now() - interval '19 days 12 hours'),
(20, 'Billing dunning process',                     'Implement automated dunning process for overdue invoices.',                   'in_progress', now() - interval '20 days 10 hours'),

-- Cases 171-180
(21, 'Audit finding remediation',                   'Remediate critical findings from last internal audit cycle.',                  'open',        now() - interval '21 days 10 hours'),
(22, 'Compliance monitoring automation',            'Automate continuous compliance monitoring for key controls.',                  'in_progress', now() - interval '22 days 12 hours'),
(23, 'Security incident post-mortem',               'Conduct post-mortem analysis for recent security incident.',                  'open',        now() - interval '23 days 12 hours'),
(24, 'Onboarding partner program',                  'Launch partner onboarding program with certification track.',                 'closed',      now() - interval '24 days 10 hours'),
(25, 'Refund analytics report',                     'Create analytics report for refund trends and root causes.',                  'in_progress', now() - interval '25 days 10 hours'),
(26, 'Escalation response improvement',             'Reduce average escalation response time by 25%.',                              'open',        now() - interval '26 days 10 hours'),
(27, 'Migration data mapping',                      'Create data field mapping document for system migration.',                    'in_progress', now() - interval '27 days 12 hours'),
(28, 'Tax calculation engine update',               'Update tax calculation engine for new jurisdiction rules.',                   'open',        now() - interval '28 days 12 hours'),
(29, 'Fraud detection ML model training',           'Retrain fraud detection ML model with updated dataset.',                      'closed',      now() - interval '29 days 10 hours'),
(30, 'Access audit quarterly review',               'Conduct quarterly user access review and certification.',                     'in_progress', now() - interval '30 days 10 hours'),

-- Cases 181-190
(1,  'Integration retry mechanism',                 'Implement exponential backoff retry for failed API calls.',                    'open',        now() - interval '1 day 14 hours'),
(2,  'Performance CDN configuration',               'Configure CDN for static assets to improve load times.',                      'in_progress', now() - interval '2 days 14 hours'),
(3,  'License usage analytics',                     'Build analytics dashboard for license usage patterns.',                       'open',        now() - interval '3 days 12 hours'),
(4,  'Data privacy impact assessment',              'Conduct privacy impact assessment for new data collection.',                  'closed',      now() - interval '4 days 14 hours'),
(5,  'Export job queue management',                  'Implement job queue for managing concurrent export requests.',                'in_progress', now() - interval '5 days 14 hours'),
(6,  'Report distribution list update',             'Update automated report distribution list for Q2.',                            'open',        now() - interval '6 days 14 hours'),
(7,  'Notification opt-out compliance',             'Ensure notification opt-out mechanism meets CAN-SPAM compliance.',             'in_progress', now() - interval '7 days 14 hours'),
(8,  'Support tier restructuring',                  'Restructure support tiers based on customer contract levels.',                'open',        now() - interval '8 days 12 hours'),
(9,  'Billing multi-currency support',              'Add multi-currency support to billing module.',                                'closed',      now() - interval '9 days 14 hours'),
(10, 'Audit automation framework',                  'Implement automated audit testing framework.',                                 'in_progress', now() - interval '10 days 14 hours'),

-- Cases 191-200
(11, 'Compliance regulatory update',                'Update compliance controls for new financial regulations.',                    'open',        now() - interval '11 days 12 hours'),
(12, 'Security MFA enforcement',                    'Enforce multi-factor authentication for all admin accounts.',                 'in_progress', now() - interval '12 days 12 hours'),
(13, 'Onboarding API documentation',                'Write API onboarding documentation for developer portal.',                    'open',        now() - interval '13 days 14 hours'),
(14, 'Refund SLA definition',                       'Define and publish SLA for refund processing timelines.',                     'closed',      now() - interval '14 days 14 hours'),
(15, 'Escalation chatbot integration',              'Integrate chatbot for automated first-level escalation triage.',              'in_progress', now() - interval '15 days 12 hours'),
(16, 'Migration cutover plan',                      'Finalize cutover plan for production database migration.',                    'open',        now() - interval '16 days 12 hours'),
(17, 'Tax reporting automation',                    'Automate quarterly tax reporting and submission process.',                     'in_progress', now() - interval '17 days 12 hours'),
(18, 'Fraud alert threshold tuning',                'Tune fraud alert thresholds to reduce false positive rate.',                  'open',        now() - interval '18 days 14 hours'),
(19, 'Access governance tool evaluation',           'Evaluate tools for centralized access governance management.',                'closed',      now() - interval '19 days 14 hours'),
(20, 'Integration documentation refresh',           'Refresh all integration documentation for current API version.',              'in_progress', now() - interval '20 days 12 hours');

