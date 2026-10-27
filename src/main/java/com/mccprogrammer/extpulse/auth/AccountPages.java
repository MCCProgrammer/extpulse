package com.mccprogrammer.extpulse.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Sign-in, sign-up and settings pages. Spring Security handles the sign-in form's POST
 * and the sign-out; these render the pages and save what they ask for.
 */
@Controller
class AccountPages {

	private final AccountService accounts;

	private final PublicProfiles profiles;

	private final AuthProperties properties;

	AccountPages(AccountService accounts, PublicProfiles profiles, AuthProperties properties) {
		this.accounts = accounts;
		this.profiles = profiles;
		this.properties = properties;
	}

	@GetMapping("/login")
	String login(Model model) {
		model.addAttribute("registrationOpen", this.properties.registrationOpen());
		return "auth/login";
	}

	@GetMapping("/register")
	String registerForm(Model model) {
		model.addAttribute("form", new RegisterForm("", ""));
		model.addAttribute("registrationOpen", this.properties.registrationOpen());
		return "auth/register";
	}

	@PostMapping("/register")
	String register(@Valid @ModelAttribute("form") RegisterForm form, BindingResult result, Model model,
			RedirectAttributes redirect) {
		model.addAttribute("registrationOpen", this.properties.registrationOpen());
		if (result.hasErrors()) {
			return "auth/register";
		}
		try {
			this.accounts.register(form.email(), form.password());
		}
		catch (ErrorResponseException ex) {
			model.addAttribute("error", ex.getBody().getDetail());
			return "auth/register";
		}
		redirect.addFlashAttribute("notice", "Account created. Sign in with it.");
		return "redirect:/login";
	}

	@GetMapping("/app/settings")
	String settings(@AuthenticationPrincipal SignedInUser user, Model model) {
		model.addAttribute("slug", this.profiles.slugOf(user.id()).orElse(""));
		return "auth/settings";
	}

	@PostMapping("/app/settings/public-page")
	String choosePublicPage(@AuthenticationPrincipal SignedInUser user, @RequestParam(defaultValue = "") String slug,
			RedirectAttributes redirect) {
		try {
			this.profiles.choose(user.id(), slug);
			redirect.addFlashAttribute("notice",
					slug.isBlank() ? "Your public page is off." : "Your public page is on.");
		}
		catch (ErrorResponseException ex) {
			redirect.addFlashAttribute("error", ex.getBody().getDetail());
		}
		return "redirect:/app/settings";
	}

	record RegisterForm(@NotBlank @Email @Size(max = 254) String email,
			@NotBlank @Size(min = 10, max = 72) String password) {
	}

}
