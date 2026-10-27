# One public subnet in its own network. The firewall (security list) lets in only SSH
# and the web: 22 for administration and deploys (keys only, no passwords), 80 for
# Let's Encrypt and the redirect to HTTPS, and 443 for the app.

resource "oci_core_vcn" "extpulse" {
  compartment_id = var.tenancy_ocid
  display_name   = "extpulse"
  cidr_blocks    = ["10.0.0.0/16"]
  dns_label      = "extpulse"
}

resource "oci_core_internet_gateway" "extpulse" {
  compartment_id = var.tenancy_ocid
  vcn_id         = oci_core_vcn.extpulse.id
  display_name   = "extpulse"
  enabled        = true
}

resource "oci_core_route_table" "public" {
  compartment_id = var.tenancy_ocid
  vcn_id         = oci_core_vcn.extpulse.id
  display_name   = "extpulse-public"

  route_rules {
    destination       = "0.0.0.0/0"
    destination_type  = "CIDR_BLOCK"
    network_entity_id = oci_core_internet_gateway.extpulse.id
  }
}

resource "oci_core_security_list" "public" {
  compartment_id = var.tenancy_ocid
  vcn_id         = oci_core_vcn.extpulse.id
  display_name   = "extpulse-public"

  egress_security_rules {
    destination = "0.0.0.0/0"
    protocol    = "all"
  }

  dynamic "ingress_security_rules" {
    for_each = { ssh = 22, http = 80, https = 443 }
    content {
      description = ingress_security_rules.key
      source      = "0.0.0.0/0"
      protocol    = "6" # TCP
      tcp_options {
        min = ingress_security_rules.value
        max = ingress_security_rules.value
      }
    }
  }

  # "Fragmentation needed" messages, so path MTU discovery works.
  ingress_security_rules {
    source   = "0.0.0.0/0"
    protocol = "1" # ICMP
    icmp_options {
      type = 3
      code = 4
    }
  }
}

resource "oci_core_subnet" "public" {
  compartment_id    = var.tenancy_ocid
  vcn_id            = oci_core_vcn.extpulse.id
  display_name      = "extpulse-public"
  cidr_block        = "10.0.1.0/24"
  dns_label         = "public"
  route_table_id    = oci_core_route_table.public.id
  security_list_ids = [oci_core_security_list.public.id]
}
