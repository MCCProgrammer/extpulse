package com.mccprogrammer.extpulse;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Shortcut for an error the API answers with: Spring MVC turns it into a Problem
 * Details response with this status and detail.
 */
public final class ApiProblem {

	private ApiProblem() {
	}

	public static ErrorResponseException of(HttpStatus status, String detail) {
		return of(status, detail, null);
	}

	public static ErrorResponseException of(HttpStatus status, String detail, Throwable cause) {
		return new ErrorResponseException(status, ProblemDetail.forStatusAndDetail(status, detail), cause);
	}

}
