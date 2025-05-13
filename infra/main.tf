terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.region
}

# 1) VPC: isolated network
resource "aws_vpc" "p3" {
  cidr_block = "10.0.0.0/16"
  tags = { Name = "p3-vpc" }
}

# 2) Public subnet: inside your VPC
resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.p3.id
  cidr_block              = "10.0.1.0/24"
  map_public_ip_on_launch = true
  tags = { Name = "p3-public-subnet" }
}

# 3) Internet Gateway: lets your subnet talk to the internet
resource "aws_internet_gateway" "igw" {
  vpc_id = aws_vpc.p3.id
  tags = { Name = "p3-igw" }
}

# 4) Route Table: send “0.0.0.0/0” through the IGW
resource "aws_route_table" "rt" {
  vpc_id = aws_vpc.p3.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.igw.id
  }
  tags = { Name = "p3-rt" }
}

resource "aws_route_table_association" "rta" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.rt.id
}

# 5) Security Group: open SSH & port 8080
resource "aws_security_group" "controller_sg" {
  name   = "p3-controller-sg"
  vpc_id = aws_vpc.p3.id

  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  ingress {
    description = "App port"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  tags = { Name = "p3-controller-sg" }
}

# 6) Lookup the latest Ubuntu AMI
data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"]  # Canonical’s account
  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*"]
  }
}

# 7) EC2 Instance: your “controller”
resource "aws_instance" "controller" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = var.instance_type
  key_name               = var.key_name
  subnet_id              = aws_subnet.public.id
  vpc_security_group_ids = [aws_security_group.controller_sg.id]

  # on-boot script:
  user_data = templatefile(
    "${path.module}/userdata/controller.sh.tpl",
    { }
  )

  iam_instance_profile = aws_iam_instance_profile.controller.name

  tags = {
    Name = "p3-controller"
  }
}
