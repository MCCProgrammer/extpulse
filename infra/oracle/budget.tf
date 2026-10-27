# Everything here is Always Free. This budget is the safety net: an email as soon as
# the account is charged anything at all.

resource "oci_budget_budget" "safety_net" {
  compartment_id = var.tenancy_ocid
  display_name   = "extpulse-safety-net"
  description    = "Alerts on any charge: ExtPulse should cost nothing."
  amount         = 1
  reset_period   = "MONTHLY"
  target_type    = "COMPARTMENT"
  targets        = [var.tenancy_ocid]
}

resource "oci_budget_alert_rule" "any_charge" {
  budget_id      = oci_budget_budget.safety_net.id
  display_name   = "any-charge"
  type           = "ACTUAL"
  threshold      = 1 # percent of the 1-unit budget: the first cent
  threshold_type = "PERCENTAGE"
  recipients     = var.budget_alert_email
  message        = "Oracle Cloud charged something: ExtPulse should be free. Check the cost analysis."
}
