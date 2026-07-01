resource "aws_ecr_repository" "repo" {
  name                 = var.service_name
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  # Tags handled by default_tags in provider
}
