# The account refuses to create what Always Free doesn't cover, so a mistake (here or
# in the console) fails instead of being charged. Within a quota, later statements
# override earlier ones: each family is zeroed, then the free allowance is given back.
# Quotas cover the services where a charge is likely; the budget alert (budget.tf)
# catches anything else.

locals {
  free_a1_ocpus     = 4
  free_a1_memory_gb = 24
}

resource "oci_limits_quota" "always_free" {
  compartment_id = var.tenancy_ocid
  name           = "extpulse-always-free"
  description    = "Only Always Free compute, storage and no paid databases or load balancers."
  statements = [
    # Compute: Ampere A1 only, up to the free 4 OCPUs and 24 GB. A1 is counted per
    # availability domain and per region, so both get the allowance.
    "zero compute-core quotas in tenancy",
    "set compute-core quota standard-a1-core-count to ${local.free_a1_ocpus} in tenancy",
    "set compute-core quota standard-a1-core-regional-count to ${local.free_a1_ocpus} in tenancy",
    "zero compute-memory quotas in tenancy",
    "set compute-memory quota standard-a1-memory-count to ${local.free_a1_memory_gb} in tenancy",
    "set compute-memory quota standard-a1-memory-regional-count to ${local.free_a1_memory_gb} in tenancy",

    # Block and boot volumes: the free 200 GB and 5 backups.
    "set block-storage quota total-storage-gb to 200 in tenancy",
    "set block-storage quota backup-count to 5 in tenancy",

    # Object Storage: half of the free 20 GB, far more than the backups need.
    "set object-storage quota storage-bytes to 10000000000 in tenancy",

    # Not used by ExtPulse, and charged beyond small free allowances.
    "zero database quotas in tenancy",
    "zero load-balancer quotas in tenancy",
  ]
}
