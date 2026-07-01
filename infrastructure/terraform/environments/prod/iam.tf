# Setup Trust Relationship (OIDC) to allow GitHub Actions deployment without hardcoded Access Keys.
module "iam_github_oidc_provider" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-github-oidc-provider"
  version = "~> 5.0"
}

module "iam_github_oidc_role" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-github-oidc-role"
  version = "~> 5.0"

  name = "GitHubActionsOIDCRole"

  # Restrict role assumption to this project repository only
  subjects = [var.github_repo]

  policies = {
    ECRPushAndPull = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryPowerUser"
    ECSDeploy      = "arn:aws:iam::aws:policy/AmazonECS_FullAccess"
  }
}
