# AWS KMS Key for Encryption at Rest (e.g. TOTP secrets, DB passwords)
resource "aws_kms_key" "kms" {
  description             = "KMS key for TradePulse secrets encryption"
  deletion_window_in_days = 7
  enable_key_rotation     = true
}

# AWS Secrets Manager for PostgreSQL DB Password
resource "aws_secretsmanager_secret" "db_password" {
  name       = "${lower(var.project_name)}-db-password-secret"
  kms_key_id = aws_kms_key.kms.key_id
}

resource "random_password" "db_password" {
  length           = 16
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

resource "aws_secretsmanager_secret_version" "db_password_version" {
  secret_id     = aws_secretsmanager_secret.db_password.id
  secret_string = random_password.db_password.result
}

# AWS Secrets Manager for JWT Secret Keys (RS256 Private and Public Keys)
resource "aws_secretsmanager_secret" "jwt_keys" {
  name       = "${lower(var.project_name)}-jwt-keys"
  kms_key_id = aws_kms_key.kms.key_id
}

# In a real environment, we'd upload the PEM format keys. For demonstration, we scaffold it.
resource "aws_secretsmanager_secret_version" "jwt_keys_version" {
  secret_id     = aws_secretsmanager_secret.jwt_keys.id
  secret_string = jsonencode({
    private_key = "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQ..."
    public_key  = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA..."
  })
}
