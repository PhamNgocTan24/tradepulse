variable "service_name" {
  description = "Name of the microservice"
  type        = string
}

variable "container_port" {
  description = "Port the container listens on"
  type        = number
}

variable "cpu" {
  description = "CPU units for the task"
  type        = string
}

variable "memory" {
  description = "Memory for the task"
  type        = string
}

variable "cluster_id" {
  description = "ECS Cluster ID"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID where the service will be deployed"
  type        = string
}

variable "private_subnets" {
  description = "List of private subnets for ECS service"
  type        = list(string)
}

variable "aws_region" {
  description = "AWS region for CloudWatch logs"
  type        = string
}

variable "target_group_arn" {
  description = "ALB Target Group ARN (null if internal service)"
  type        = string
  default     = null
}

variable "service_discovery_namespace_id" {
  description = "Namespace ID of AWS Cloud Map Service Discovery"
  type        = string
}

# DB & Cache Connection Details
variable "postgres_endpoint" {
  description = "PostgreSQL RDS connection endpoint"
  type        = string
}

variable "postgres_db_name" {
  description = "PostgreSQL database name"
  type        = string
}

variable "postgres_username" {
  description = "PostgreSQL database username"
  type        = string
}

variable "redis_endpoint" {
  description = "ElastiCache Redis endpoint address"
  type        = string
}

# Secret ARNs
variable "db_password_secret_arn" {
  description = "Secrets Manager ARN for DB password"
  type        = string
}

variable "jwt_secret_arn" {
  description = "Secrets Manager ARN for JWT keypair"
  type        = string
}

variable "kms_key_arn" {
  description = "ARN of the KMS key used for encrypting secrets"
  type        = string
}
