package com.mccprogrammer.extpulse.alerts;

/**
 * An alert as it is sent: a one-line title (the email subject) and a short body.
 */
record AlertMessage(String title, String body) {
}
