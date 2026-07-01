
# Initialize the shared ECS Cluster
resource "aws_ecs_cluster" "main" {
  name = "${lower(var.project_name)}-cluster"
}

# Cloud Map Private DNS Namespace for Service Discovery (e.g. auth-service.tradepulse.local)
resource "aws_service_discovery_private_dns_namespace" "private" {
  name        = "${lower(var.project_name)}.local"
  description = "Service discovery namespace for TradePulse microservices"
  vpc         = module.vpc.vpc_id
}

# Define the list of microservices and their configurations
locals {
  microservices = {
    "tradepulse-api-gateway" = {
      port             = 8080
      cpu              = "512"
      memory           = "1024"
      target_group_arn = aws_lb_target_group.api_gateway.arn # Bind to public ALB
    }
    "tradepulse-auth-service" = {
      port             = 8081
      cpu              = "256"
      memory           = "512"
      target_group_arn = null # Internal service, no public ALB binding
    }
  }
}

# Use for_each loop to dynamically deploy all services from the shared module
module "ecs_services" {
  source   = "../../modules/ecs-microservice"
  for_each = local.microservices

  service_name    = each.key
  container_port  = each.value.port
  cpu             = each.value.cpu
  memory          = each.value.memory
  target_group_arn = each.value.target_group_arn
  
  cluster_id                     = aws_ecs_cluster.main.id
  vpc_id                         = module.vpc.vpc_id
  private_subnets                = module.vpc.private_subnets
  aws_region                     = var.aws_region
  service_discovery_namespace_id = aws_service_discovery_private_dns_namespace.private.id

  # Connectivities for DB & Redis
  postgres_endpoint  = aws_db_instance.postgres.endpoint
  postgres_db_name   = aws_db_instance.postgres.db_name
  postgres_username  = aws_db_instance.postgres.username
  redis_endpoint     = aws_elasticache_cluster.redis.cache_nodes[0].address

  # Secrets ARNs
  db_password_secret_arn = aws_secretsmanager_secret.db_password.arn
  jwt_secret_arn         = aws_secretsmanager_secret.jwt_keys.arn
}
