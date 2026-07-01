# Amazon RDS PostgreSQL Instance in Private Subnets
resource "aws_db_subnet_group" "db_subnets" {
  name       = "${lower(var.project_name)}-db-subnet-group"
  subnet_ids = module.vpc.private_subnets

  tags = {
    Name = "${var.project_name} DB Subnet Group"
  }
}

resource "aws_security_group" "rds_sg" {
  name        = "${lower(var.project_name)}-rds-sg"
  description = "Security group for PostgreSQL RDS"
  vpc_id      = module.vpc.vpc_id

  # Allow inbound TCP 5432 from within the VPC (e.g. ECS services)
  ingress {
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_db_instance" "postgres" {
  identifier           = "${lower(var.project_name)}-postgres"
  allocated_storage    = 20
  engine               = "postgres"
  engine_version       = "15"
  instance_class       = "db.t4g.micro"
  db_name              = "tradepulse_db"
  username             = "tp_admin"
  password             = aws_secretsmanager_secret_version.db_password_version.secret_string
  db_subnet_group_name = aws_db_subnet_group.db_subnets.name
  vpc_security_group_ids = [aws_security_group.rds_sg.id]
  skip_final_snapshot  = true
}
