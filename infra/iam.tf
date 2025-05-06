# 1) An IAM Role that EC2 can assume
resource "aws_iam_role" "controller" {
  name = "p3-controller-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action = "sts:AssumeRole"
    }]
  })
}

# 2) Attach the AWS-managed Read-only S3 policy
resource "aws_iam_role_policy_attachment" "s3_readonly" {
  role       = aws_iam_role.controller.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess"
}

# 3) Expose it via an instance profile
resource "aws_iam_instance_profile" "controller" {
  name = "p3-controller-profile"
  role = aws_iam_role.controller.name
}
