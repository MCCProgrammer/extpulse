variable "oci_profile" {
  description = "Profile in ~/.oci/config with the API signing key."
  type        = string
  default     = "DEFAULT"
}

variable "tenancy_ocid" {
  description = "The tenancy (account) OCID. Everything is created in its root compartment."
  type        = string
}

variable "region" {
  description = "The tenancy's home region; Always Free resources exist only there."
  type        = string
  default     = "eu-madrid-1"
}

variable "admin_ssh_public_key" {
  description = "Public key for the ubuntu user (administration)."
  type        = string
}

variable "deploy_ssh_public_key" {
  description = "Public key for the deploy user, which GitHub Actions uses to deploy."
  type        = string
}

variable "budget_alert_email" {
  description = "Where the budget alert goes if the account is ever charged."
  type        = string
}

# Always Free gives up to 4 OCPUs and 24 GB on Ampere A1 in total. This uses part of it:
# enough for the app, PostgreSQL and monitoring, and busy enough (memory above 20%)
# not to count as idle, which Oracle may reclaim on free accounts.
variable "ocpus" {
  description = "OCPUs of the Ampere A1 instance."
  type        = number
  default     = 1
}

variable "memory_gb" {
  description = "Memory of the Ampere A1 instance, in GB."
  type        = number
  default     = 6
}
