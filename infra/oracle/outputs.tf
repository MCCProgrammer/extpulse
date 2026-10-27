output "public_ip" {
  description = "The server's address: the A record of extpulse.mccprogrammer.com points here."
  value       = oci_core_instance.extpulse.public_ip
}

output "ssh" {
  description = "Administration access."
  value       = "ssh -i ~/.ssh/extpulse_admin ubuntu@${oci_core_instance.extpulse.public_ip}"
}

output "image" {
  description = "The Ubuntu build the server was created from."
  value       = data.oci_core_images.ubuntu.images[0].display_name
}
