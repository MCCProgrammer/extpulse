terraform {
  required_version = ">= 1.16"

  required_providers {
    oci = {
      source  = "oracle/oci"
      version = "~> 9.8.0"
    }
  }
}

# Credentials come from ~/.oci/config (an API signing key of the Oracle Cloud user);
# nothing secret is in this directory.
provider "oci" {
  config_file_profile = var.oci_profile
  region              = var.region
}
