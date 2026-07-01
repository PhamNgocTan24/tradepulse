# TradePulse Deployment Architecture & CI/CD Pipeline

This document illustrates the complete workflow from the developer pushing code to GitHub, running through the **CI/CD pipeline (GitHub Actions)**, and automatically deploying to **AWS Cloud Infrastructure** managed by **Terraform**.

## 1. System Deployment Architecture (Mermaid Diagram)

```mermaid
flowchart TB
    %% CLI & Developer Section
    subgraph DevSpace ["Developer & Code Control"]
        dev["Developer (git push)"] --> |"devops/* or main branch"| github["GitHub Repository"]
    end

    %% GitHub Actions Section
    subgraph CI_CD ["CI/CD Pipeline (GitHub Actions)"]
        github --> |Triggers| ci_flow["ci.yml\n(Test & Trivy Scan)"]
        github --> |Triggers| cd_flow["deploy-services.yml\n(dorny/paths-filter)"]
        
        cd_flow --> |"If services/api-gateway/** modified"| deploy_gw["Deploy API Gateway (uses template)"]
        cd_flow --> |"If services/auth-service/** modified"| deploy_auth["Deploy Auth Service (uses template)"]
        
        deploy_gw -.-> |"OIDC Assume Role"| aws_iam["AWS IAM OIDC Role"]
        deploy_auth -.-> |"OIDC Assume Role"| aws_iam
    end

    %% AWS Cloud Section
    subgraph AWS ["AWS Cloud Platform (ap-southeast-1)"]
        aws_iam --> |"Auth & Push Image"| ecr["Amazon ECR\n(Docker Registries)"]
        
        subgraph VPC ["AWS VPC (10.0.0.0/16)"]
            
            subgraph PublicSubnets ["Public Subnets (10.0.101.0/24 & 10.0.102.0/24)"]
                igw["Internet Gateway"] <--> alb["Application Load Balancer (ALB)"]
                nat["NAT Gateway"]
            end
            
            subgraph PrivateSubnets ["Private Subnets (10.0.1.0/24 & 10.0.2.0/24)"]
                
                subgraph ECS_Cluster ["ECS Fargate Cluster"]
                    gw_task["API Gateway Task\n(port 8080)"]
                    auth_task["Auth Service Task\n(port 8081)"]
                end
                
                subgraph DataStack ["Database & Cache (Isolate)"]
                    rds["RDS PostgreSQL\n(port 5432)"]
                    redis["ElastiCache Redis\n(port 6379)"]
                end
                
                cloudmap["AWS Cloud Map\n(Service Discovery:\ntradepulse.local)"]
            end
        end
        
        sec_man["AWS Secrets Manager / KMS"]
    end

    %% External User Access
    user["Client (Browser/App)"] ==> |"HTTPS (Port 443 / 80)"| alb

    %% Routing Flow within AWS
    alb ==> |"Forward Traffic (Port 8080)"| gw_task
    gw_task ==> |"Route internal call via SD\nauth-service.tradepulse.local:8081"| auth_task
    
    %% ECS Integrations
    deploy_gw --> |"Update Task Image"| gw_task
    deploy_auth --> |"Update Task Image"| auth_task
    
    %% Secret Injection
    sec_man -.-> |"Inject DB password\n& JWT keypair"| gw_task
    sec_man -.-> |"Inject DB password\n& JWT keypair"| auth_task

    %% DB Connections
    auth_task ==> |"Read/Write User DB"| rds
    auth_task ==> |"Blacklist Tokens"| redis
    gw_task ==> |"Rate Limiting"| redis

    %% Styles
    classDef dev fill:#ececff,stroke:#9370db,stroke-width:2px;
    classDef pipeline fill:#e1f5fe,stroke:#0288d1,stroke-width:2px;
    classDef aws fill:#fff3e0,stroke:#f57c00,stroke-width:2px;
    classDef private fill:#efebe9,stroke:#5d4037,stroke-width:2px;
    
    class dev,github dev;
    class ci_flow,cd_flow,deploy_gw,deploy_auth pipeline;
    class alb,gw_task,auth_task,rds,redis,ecr aws;
    class PrivateSubnets,ECS_Cluster,DataStack private;
```

---

## 2. Component Explanations

### A. CI/CD Pipeline Flow (GitHub Actions)
1. **Continuous Integration (CI):** Triggered on pushes to any branch. `ci.yml` runs maven tests and scans dependencies using **Trivy** to find vulnerabilities.
2. **Continuous Delivery (CD):** Triggered on `main` or `devops/*` branches. `deploy-services.yml` uses `dorny/paths-filter` to determine which service changed code:
   - Reusable template `deploy-ecs-template.yml` is called to dynamically authenticate with AWS via **IAM OIDC** (role-based authentication).
   - Builds the Docker image, tags it with the commit SHA, and pushes it to **Amazon ECR**.
   - Registers a new **ECS Task Definition** revision and initiates a rolling deploy on **AWS ECS Fargate**.

### B. Network Traffic Routing Flow (VPC Subnets)
1. **External Access:** End-users reach the public **Application Load Balancer (ALB)** positioned in the Public Subnets.
2. **Internal Proxying:** ALB terminates SSL (HTTPS) and routes traffic over HTTP to the **API Gateway** task running inside Private Subnets.
3. **Internal Service Discovery:** The API Gateway inspects the HTTP request path and routes internal microservice requests to `auth-service` via **AWS Cloud Map** (Service Discovery) using internal DNS resolution (`auth-service.tradepulse.local:8081`).

### C. Databases, Cache & Security
- **Isolation:** **RDS PostgreSQL** (port 5432) and **ElastiCache Redis** (port 6379) are completely isolated in private subnets, only allowing internal traffic from ECS task security groups.
- **Secrets Management:** When containers launch, AWS Secrets Manager injects database credentials and JWT keypairs directly into the ECS task as secure environment variables, utilizing **AWS KMS** for encryption at rest.

## 3. AWS Infrastructure Topology (Network & Security)

This diagram focuses purely on the AWS Networking and High Availability setup across Multiple Availability Zones (Multi-AZ).

```mermaid
flowchart TB
    Internet((Internet))

    subgraph AWS ["AWS Region (ap-southeast-1)"]
        subgraph VPC ["VPC (10.0.0.0/16)"]
            igw([Internet Gateway])
            
            subgraph AZ1 ["Availability Zone A (ap-southeast-1a)"]
                subgraph PubSub1 ["Public Subnet 1 (10.0.101.0/24)"]
                    alb1([ALB Node A])
                    nat1([NAT Gateway])
                end
                subgraph PrivSub1 ["Private Subnet 1 (10.0.1.0/24)"]
                    ecs1_gw[API Gateway Task]
                    ecs1_auth[Auth Service Task]
                    rds_master[(RDS Postgres Primary)]
                    redis1[(ElastiCache Redis)]
                end
            end
            
            subgraph AZ2 ["Availability Zone B (ap-southeast-1b)"]
                subgraph PubSub2 ["Public Subnet 2 (10.0.102.0/24)"]
                    alb2([ALB Node B])
                end
                subgraph PrivSub2 ["Private Subnet 2 (10.0.2.0/24)"]
                    ecs2_gw[API Gateway Task]
                    ecs2_auth[Auth Service Task]
                    rds_standby[(RDS Postgres Standby)]
                    redis2[(ElastiCache Redis)]
                end
            end
        end
    end

    %% Network Flow
    Internet <--> igw
    igw <--> alb1 & alb2
    
    alb1 -.-> ecs1_gw & ecs2_gw
    alb2 -.-> ecs1_gw & ecs2_gw
    
    ecs1_gw -. "Service Discovery" .-> ecs1_auth & ecs2_auth
    ecs2_gw -. "Service Discovery" .-> ecs1_auth & ecs2_auth
    
    ecs1_auth & ecs2_auth ==> rds_master
    
    ecs1_gw & ecs1_auth & ecs2_gw & ecs2_auth -.-> redis1 & redis2
    
    ecs1_auth & ecs2_auth -. "Outbound Internet" .-> nat1 -.-> igw
    
    %% Styles
    classDef region fill:#f9f9f9,stroke:#ff9900,stroke-width:2px,stroke-dasharray: 5 5,color:#333;
    classDef vpc fill:#ffffff,stroke:#00a4a6,stroke-width:2px,color:#333;
    classDef az fill:#f4f4f4,stroke:#00a4a6,stroke-width:1px,stroke-dasharray: 5 5,color:#333;
    classDef pub fill:#e6f9e6,stroke:#2ca02c,stroke-width:1px,color:#333;
    classDef priv fill:#e6f2ff,stroke:#1f77b4,stroke-width:1px,color:#333;
    
    class AWS region;
    class VPC vpc;
    class AZ1,AZ2 az;
    class PubSub1,PubSub2 pub;
    class PrivSub1,PrivSub2 priv;
```
