package com.mccprogrammer.extpulse.alerts;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
class EmailAlertSender implements AlertSender {

	private final JavaMailSender mail;

	private final AlertProperties properties;

	EmailAlertSender(JavaMailSender mail, AlertProperties properties) {
		this.mail = mail;
		this.properties = properties;
	}

	@Override
	public AlertChannel channel() {
		return AlertChannel.EMAIL;
	}

	@Override
	public void send(String target, AlertMessage message) {
		SimpleMailMessage email = new SimpleMailMessage();
		email.setFrom(this.properties.mailFrom());
		email.setTo(target);
		email.setSubject(message.title());
		email.setText(message.body() + "\n\n-- \nExtPulse: daily stats and reviews of your extensions.");
		this.mail.send(email);
	}

}
