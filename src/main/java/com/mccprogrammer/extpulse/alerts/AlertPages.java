package com.mccprogrammer.extpulse.alerts;

import com.mccprogrammer.extpulse.alerts.AlertController.RuleResponse;
import com.mccprogrammer.extpulse.auth.SignedInUser;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The alert rules as a page: the list, a form for a new one, and buttons to test or
 * delete each.
 */
@Controller
class AlertPages {

	private final AlertService alerts;

	AlertPages(AlertService alerts) {
		this.alerts = alerts;
	}

	@GetMapping("/app/alerts")
	String rules(@AuthenticationPrincipal SignedInUser user, Model model) {
		model.addAttribute("rules", this.alerts.rulesOf(user.id()).stream().map(RuleResponse::of).toList());
		model.addAttribute("types", AlertType.values());
		model.addAttribute("channels", AlertChannel.values());
		return "alerts/rules";
	}

	@PostMapping("/app/alerts")
	String add(@AuthenticationPrincipal SignedInUser user, @Validated @ModelAttribute RuleForm form,
			BindingResult result, RedirectAttributes redirect) {
		if (result.hasErrors()) {
			redirect.addFlashAttribute("error", "Choose what to watch and where to send it.");
			return "redirect:/app/alerts";
		}
		return done(redirect, () -> {
			this.alerts.addRule(user.id(), form.type(), form.channel(), blankToNull(form.target()), form.threshold());
			return "Alert added.";
		});
	}

	@PostMapping("/app/alerts/{id}/test")
	String test(@AuthenticationPrincipal SignedInUser user, @PathVariable long id, RedirectAttributes redirect) {
		return done(redirect, () -> {
			this.alerts.sendTest(user.id(), id);
			return "Test alert sent.";
		});
	}

	@PostMapping("/app/alerts/{id}/delete")
	String delete(@AuthenticationPrincipal SignedInUser user, @PathVariable long id, RedirectAttributes redirect) {
		return done(redirect, () -> {
			this.alerts.deleteRule(user.id(), id);
			return "Alert deleted.";
		});
	}

	private static String done(RedirectAttributes redirect, Action action) {
		try {
			redirect.addFlashAttribute("notice", action.run());
		}
		catch (ErrorResponseException ex) {
			redirect.addFlashAttribute("error", ex.getBody().getDetail());
		}
		return "redirect:/app/alerts";
	}

	private static String blankToNull(String value) {
		return (value != null && !value.isBlank()) ? value.strip() : null;
	}

	@FunctionalInterface
	private interface Action {

		String run();

	}

	record RuleForm(@NotNull AlertType type, @NotNull AlertChannel channel, @Size(max = 500) String target,
			Integer threshold) {
	}

}
