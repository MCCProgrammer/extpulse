# A copy of the daily backups off the server, in Object Storage (Always Free: 20 GB).
# The server signs in as itself (instance principal), so no key is stored on it, and may
# only add objects and read them: whoever takes over the server can't delete or replace
# the copies. The bucket deletes them after 30 days.

locals {
  backup_bucket         = "extpulse-backups"
  backup_retention_days = 30
}

data "oci_objectstorage_namespace" "this" {
  compartment_id = var.tenancy_ocid
}

resource "oci_objectstorage_bucket" "backups" {
  compartment_id = var.tenancy_ocid
  namespace      = data.oci_objectstorage_namespace.this.namespace
  name           = local.backup_bucket
  access_type    = "NoPublicAccess"
  storage_tier   = "Standard" # Infrequent Access charges for each retrieval
  versioning     = "Disabled"
}

# The bucket's lifecycle rules run as the Object Storage service, which needs its own
# permission to delete objects.
resource "oci_identity_policy" "backup_lifecycle" {
  compartment_id = var.tenancy_ocid
  name           = "extpulse-backup-lifecycle"
  description    = "Lets Object Storage delete ExtPulse backups that have expired."
  statements = [
    "Allow service objectstorage-${var.region} to manage object-family in tenancy where target.bucket.name = '${local.backup_bucket}'",
  ]
}

resource "oci_objectstorage_object_lifecycle_policy" "backups" {
  namespace = data.oci_objectstorage_namespace.this.namespace
  bucket    = oci_objectstorage_bucket.backups.name

  rules {
    name        = "delete-old-backups"
    action      = "DELETE"
    target      = "objects"
    is_enabled  = true
    time_amount = local.backup_retention_days
    time_unit   = "DAYS"
  }

  depends_on = [oci_identity_policy.backup_lifecycle]
}

# The server, and only it: a rebuilt server gets a new OCID, and this rule follows it.
resource "oci_identity_dynamic_group" "server" {
  compartment_id = var.tenancy_ocid
  name           = "extpulse-server"
  description    = "The ExtPulse server, for its backups."
  matching_rule  = "instance.id = '${oci_core_instance.extpulse.id}'"
}

resource "oci_identity_policy" "server_backups" {
  compartment_id = var.tenancy_ocid
  name           = "extpulse-server-backups"
  description    = "Lets the ExtPulse server add and read its backups, but not replace or delete them."
  statements = [
    "Allow dynamic-group ${oci_identity_dynamic_group.server.name} to read buckets in tenancy where target.bucket.name = '${local.backup_bucket}'",
    "Allow dynamic-group ${oci_identity_dynamic_group.server.name} to manage objects in tenancy where all {target.bucket.name = '${local.backup_bucket}', any {request.permission = 'OBJECT_CREATE', request.permission = 'OBJECT_INSPECT', request.permission = 'OBJECT_READ'}}",
  ]
}
