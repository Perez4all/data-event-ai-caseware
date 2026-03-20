# AWS Architecture Diagrams

## Option A — Custom App on AWS

```mermaid
graph TB
    subgraph "Client"
        CLI["curl / API Client"]
    end

    subgraph "AWS Cloud"
        subgraph "Networking"
            ALB["ALB<br/>TLS termination"]
        end

        subgraph "Compute — ECS Fargate"
            INGEST["Spring Boot<br/>Ingestion Service<br/>(scheduled every 30-60s)"]
            SEARCH_PROXY["Search Proxy<br/>(thin Lambda or Fargate)"]
        end

        subgraph "Database — RDS"
            RDS["RDS PostgreSQL<br/>Multi-AZ<br/>+ checkpoint table"]
            RDS_READ["Read Replica"]
        end

        subgraph "Data Lake — S3"
            S3_LAKE["S3 Bucket<br/>s3://lake/cases/yyyy-MM-dd/data.jsonl<br/>s3://lake/customers/yyyy-MM-dd/data.jsonl<br/>(versioned, SSE-KMS)"]
            S3_EVENTS["S3 Bucket<br/>s3://events/run-id.jsonl<br/>(audit log)"]
        end

        subgraph "Search — OpenSearch"
            OS["OpenSearch Service<br/>3 data nodes, Multi-AZ<br/>BM25 ranking"]
        end

        subgraph "Security"
            KMS["AWS KMS<br/>encryption keys"]
            IAM["IAM Roles<br/>per service"]
            CT["CloudTrail<br/>audit logging"]
        end

        CW["CloudWatch<br/>metrics + alarms"]
    end

    CLI -->|"POST /ingest<br/>POST /search"| ALB
    ALB -->|"/ingest"| INGEST
    ALB -->|"/search"| SEARCH_PROXY
    INGEST -->|"read delta rows"| RDS_READ
    INGEST -->|"write checkpoint"| RDS
    INGEST -->|"write JSONL partitions"| S3_LAKE
    INGEST -->|"write audit events"| S3_EVENTS
    INGEST -->|"index documents"| OS
    SEARCH_PROXY -->|"query"| OS
    RDS -.->|"replication"| RDS_READ
    S3_LAKE -.->|"encrypted by"| KMS
    RDS -.->|"encrypted by"| KMS
    OS -.->|"encrypted by"| KMS
    INGEST -.->|"logs/metrics"| CW
    OS -.->|"logs/metrics"| CW
```

## Option B — AWS Lake Formation (Managed)

```mermaid
graph TB
    subgraph "Client"
        CLI["curl / API Client"]
    end

    subgraph "AWS Cloud"
        subgraph "API Layer"
            APIGW["API Gateway"]
            LAMBDA_TRIGGER["Lambda<br/>trigger Glue workflow"]
            LAMBDA_SEARCH["Lambda<br/>search proxy"]
        end

        subgraph "Database — RDS"
            RDS["RDS PostgreSQL<br/>Multi-AZ"]
        end

        subgraph "Lake Formation"
            LF["Lake Formation<br/>permissions + governance"]
            GLUE_CRAWLER["Glue Crawler<br/>schema discovery"]
            GLUE_ETL["Glue ETL Job<br/>CDC ingestion<br/>(micro-batch 1-5 min)"]
            GLUE_CAT["Glue Data Catalog<br/>schema registry"]
            GLUE_INDEX["Glue ETL Job<br/>OpenSearch indexer"]
        end

        subgraph "Data Lake — S3"
            S3_LAKE["S3 Bucket<br/>s3://lake/cases/year=.../month=.../day=.../<br/>Parquet, partitioned<br/>(versioned, SSE-KMS)"]
        end

        subgraph "Search — OpenSearch"
            OS["OpenSearch Service<br/>3 data nodes, Multi-AZ<br/>BM25 ranking"]
        end

        subgraph "Security & Audit"
            KMS["AWS KMS"]
            IAM["IAM Roles"]
            CT["CloudTrail"]
            LF_PERMS["Lake Formation<br/>row/column security<br/>per tenant"]
        end

        CW["CloudWatch"]
    end

    CLI -->|"POST /ingest"| APIGW
    CLI -->|"POST /search"| APIGW
    APIGW -->|"/ingest"| LAMBDA_TRIGGER
    APIGW -->|"/search"| LAMBDA_SEARCH
    LAMBDA_TRIGGER -->|"start workflow"| GLUE_ETL
    LAMBDA_SEARCH -->|"query"| OS
    GLUE_ETL -->|"CDC from WAL"| RDS
    GLUE_ETL -->|"write Parquet"| S3_LAKE
    GLUE_ETL -->|"register partitions"| GLUE_CAT
    S3_LAKE -->|"new partition event"| GLUE_INDEX
    GLUE_INDEX -->|"index documents"| OS
    GLUE_CRAWLER -->|"discover schema"| RDS
    GLUE_CRAWLER -->|"update"| GLUE_CAT
    LF -->|"govern access"| S3_LAKE
    LF -->|"govern access"| GLUE_CAT
    LF_PERMS -.->|"tenant isolation"| LF
    S3_LAKE -.->|"encrypted by"| KMS
    RDS -.->|"encrypted by"| KMS
    OS -.->|"encrypted by"| KMS
    GLUE_ETL -.->|"logs/metrics"| CW
    OS -.->|"logs/metrics"| CW
```

## Data Flow — Option A (Custom App)

```mermaid
sequenceDiagram
    participant Scheduler as ECS Scheduled Task
    participant App as Spring Boot (Fargate)
    participant DB as RDS PostgreSQL
    participant S3 as S3 Data Lake
    participant OS as OpenSearch

    Scheduler->>App: trigger every 30-60s
    App->>DB: read checkpoint from checkpoints table
    App->>DB: SELECT * FROM cases WHERE updated_at > checkpoint
    App->>DB: SELECT * FROM customers WHERE updated_at > checkpoint
    App->>S3: PUT s3://lake/cases/2026-03-20/data.jsonl
    App->>S3: PUT s3://lake/customers/2026-03-20/data.jsonl
    App->>S3: PUT s3://events/run-abc123.jsonl
    App->>DB: UPDATE checkpoints SET value = now()
    App->>OS: POST /_bulk (index cases)
    Note over App,OS: Idempotent: same key overwrites,<br/>same case_id upserts
```

## Data Flow — Option B (Lake Formation)

```mermaid
sequenceDiagram
    participant API as API Gateway
    participant Lambda as Lambda Trigger
    participant Glue as Glue ETL (CDC)
    participant DB as RDS PostgreSQL
    participant S3 as S3 Data Lake
    participant Cat as Glue Catalog
    participant Indexer as Glue Indexer
    participant OS as OpenSearch

    API->>Lambda: POST /ingest
    Lambda->>Glue: start CDC workflow
    Glue->>DB: read WAL changes (since bookmark)
    Glue->>S3: write Parquet partitions
    Glue->>Cat: register new partitions
    Glue->>Glue: update bookmark
    S3-->>Indexer: S3 event notification
    Indexer->>S3: read new Parquet files
    Indexer->>OS: bulk index documents
    Note over Glue,OS: Idempotent: bookmark prevents<br/>double processing, S3 overwrites
```

## Option C — Hardened Custom App + Kinesis (Streaming)

```mermaid
graph TB
    subgraph "Client"
        CLI["curl / API Client<br/>(JWT with tenant_id)"]
    end

    subgraph "AWS Cloud"
        subgraph "Networking"
            ALB["ALB<br/>TLS termination"]
        end

        subgraph "Database — RDS"
            RDS["RDS PostgreSQL<br/>Multi-AZ<br/>+ tenant_permissions table"]
        end

        subgraph "CDC Pipeline"
            DMS["DMS<br/>CDC from WAL<br/>(continuous replication)"]
            KDS["Kinesis Data Streams<br/>6 shards<br/>(5,000+ records/sec)"]
            KDF["Kinesis Data Firehose<br/>JSON → Parquet<br/>auto-partitioned"]
        end

        subgraph "Compute — ECS Fargate"
            CONSUMER["Kinesis Consumer<br/>(bulk upsert to OpenSearch)"]
            SEARCH_PROXY["Search Proxy<br/>(tenant context from JWT)"]
        end

        subgraph "Data Lake — S3"
            S3_LAKE["S3 Bucket<br/>s3://lake/cases/tenant={id}/dt=yyyy-MM-dd/<br/>Parquet, tenant-partitioned<br/>(versioned, SSE-KMS)"]
            S3_EVENTS["S3 Bucket<br/>s3://events/run-id.jsonl<br/>(audit log)"]
        end

        subgraph "Search — OpenSearch"
            OS["OpenSearch Service<br/>3 data nodes, Multi-AZ<br/>BM25 ranking"]
            DLS["Document-Level Security<br/>tenant_id filter per JWT<br/>(zero-trust isolation)"]
        end

        subgraph "Security"
            KMS["AWS KMS<br/>encryption keys"]
            SM["Secrets Manager<br/>RDS + OpenSearch creds"]
            IAM["IAM Roles<br/>single task role"]
            CT["CloudTrail<br/>audit logging"]
        end

        CW["CloudWatch<br/>metrics + alarms<br/>(DMS lag, Kinesis age,<br/>OS latency)"]
    end

    CLI -->|"POST /search"| ALB
    ALB -->|"/search"| SEARCH_PROXY
    SEARCH_PROXY -->|"query + DLS"| OS
    DLS -.->|"enforces"| OS
    RDS -->|"WAL stream"| DMS
    DMS -->|"change records"| KDS
    KDS -->|"fan-out"| KDF
    KDS -->|"fan-out"| CONSUMER
    KDF -->|"Parquet files"| S3_LAKE
    CONSUMER -->|"bulk upsert by doc ID"| OS
    S3_LAKE -.->|"encrypted by"| KMS
    RDS -.->|"encrypted by"| KMS
    OS -.->|"encrypted by"| KMS
    KDS -.->|"encrypted by"| KMS
    RDS -.->|"credentials"| SM
    DMS -.->|"logs/metrics"| CW
    KDS -.->|"iterator age"| CW
    CONSUMER -.->|"logs/metrics"| CW
    OS -.->|"logs/metrics"| CW
```

## Data Flow — Option C (Hardened + Kinesis)

```mermaid
sequenceDiagram
    participant DB as RDS PostgreSQL
    participant DMS as DMS (CDC)
    participant KDS as Kinesis Streams
    participant KDF as Firehose
    participant S3 as S3 Lake (Parquet)
    participant Consumer as ECS Consumer
    participant OS as OpenSearch (DLS)
    participant Client as API Client
    participant Proxy as Search Proxy

    Note over DB,DMS: Continuous — no polling
    DB->>DMS: WAL changes (every commit)
    DMS->>KDS: put record (tenant_id, case/customer data)
    
    par Lake write path
        KDS->>KDF: fan-out delivery
        KDF->>S3: write Parquet to<br/>tenant={id}/dt=yyyy-MM-dd/
    and Search index path
        KDS->>Consumer: read shard iterator
        Consumer->>OS: POST /_bulk (upsert by doc ID)
    end

    Note over KDF,S3: Idempotent: same partition key<br/>= same file overwritten

    Client->>Proxy: POST /search (JWT with tenant_id)
    Proxy->>OS: query (DLS appends tenant_id filter)
    OS->>Proxy: ranked results (tenant-scoped)
    Proxy->>Client: response

    Note over Consumer,OS: Replay: restart DMS from WAL LSN<br/>Lake overwrites, OS upserts, no duplicates
```

## Option A → C: What Changed

```mermaid
graph LR
    subgraph "Removed from A"
        POLL["Polling<br/>(WHERE updated_at > ?)"]
        JSONL["JSONL format"]
        DATE_PART["Date-only partitioning<br/>(cases/yyyy-MM-dd/)"]
        IAM_PER["5,000 IAM policies"]
        APP_FILTER["App-code search filtering"]
    end

    subgraph "Added in C"
        CDC["DMS CDC<br/>(WAL streaming)"]
        PARQUET["Parquet via Firehose"]
        TENANT_PART["Tenant-first partitioning<br/>(tenant={id}/dt=...)"]
        SINGLE_IAM["Single IAM role<br/>+ app-layer isolation"]
        DLS_OS["OpenSearch DLS<br/>(zero-trust per tenant)"]
    end

    POLL -->|"replaced by"| CDC
    JSONL -->|"replaced by"| PARQUET
    DATE_PART -->|"replaced by"| TENANT_PART
    IAM_PER -->|"replaced by"| SINGLE_IAM
    APP_FILTER -->|"replaced by"| DLS_OS
```
