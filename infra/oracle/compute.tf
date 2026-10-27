data "oci_identity_availability_domains" "all" {
  compartment_id = var.tenancy_ocid
}

# The newest Ubuntu 24.04 build for Arm (Ampere A1).
data "oci_core_images" "ubuntu" {
  compartment_id           = var.tenancy_ocid
  operating_system         = "Canonical Ubuntu"
  operating_system_version = "24.04"
  shape                    = "VM.Standard.A1.Flex"
  sort_by                  = "TIMECREATED"
  sort_order               = "DESC"
}

resource "oci_core_instance" "extpulse" {
  compartment_id      = var.tenancy_ocid
  availability_domain = data.oci_identity_availability_domains.all.availability_domains[0].name
  display_name        = "extpulse"
  shape               = "VM.Standard.A1.Flex" # Always Free eligible

  shape_config {
    ocpus         = var.ocpus
    memory_in_gbs = var.memory_gb
  }

  source_details {
    source_type             = "image"
    source_id               = data.oci_core_images.ubuntu.images[0].id
    boot_volume_size_in_gbs = 50 # of the 200 GB of Always Free block storage
  }

  create_vnic_details {
    subnet_id        = oci_core_subnet.public.id
    assign_public_ip = true
    hostname_label   = "extpulse"
  }

  metadata = {
    ssh_authorized_keys = var.admin_ssh_public_key
    user_data = base64encode(templatefile("${path.module}/cloud-init.yaml", {
      deploy_ssh_public_key = var.deploy_ssh_public_key
    }))
  }

  lifecycle {
    # A newer Ubuntu image, or a change to the first-boot script, must not replace a
    # running server (and its data). Rebuilding is a deliberate `terraform apply -replace`.
    ignore_changes = [source_details[0].source_id, metadata["user_data"]]
  }
}
