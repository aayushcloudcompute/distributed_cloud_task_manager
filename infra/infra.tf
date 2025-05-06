variable "region" {
  description = "AWS region"
  type        = string
  default     = "us-east-1"
}

variable "key_name" {
  description = "EC2 Key Pair name"
  type        = string
  default     = "hello-springboot-key"
}

variable "instance_type" {
  description = "Size of the controller EC2"
  type        = string
  default     = "t3.micro"
}

variable "orchestrator_bucket" {
  description = "Name of the S3 bucket containing orchestrator.jar"
  type        = string
  default     = "p3-testing-bucket"
}