package com.mccprogrammer.extpulse.publicpage;

import java.util.Locale;

/**
 * A README badge in the usual flat style: a gray label and a colored value, as SVG.
 * Widths come from Verdana's character widths at 11px, the font badges are drawn in.
 */
final class Badge {

	private static final int PADDING = 6;

	private Badge() {
	}

	static String svg(String label, String value) {
		int labelWidth = textWidth(label) + 2 * PADDING;
		int valueWidth = textWidth(value) + 2 * PADDING;
		int width = labelWidth + valueWidth;
		String text = escape(label) + ": " + escape(value);
		// Locale.ROOT: SVG numbers always use a dot, whatever the server's language.
		return String.format(Locale.ROOT, """
				<svg xmlns="http://www.w3.org/2000/svg" width="%1$d" height="20" role="img" aria-label="%2$s">\
				<title>%2$s</title>\
				<clipPath id="r"><rect width="%1$d" height="20" rx="3"/></clipPath>\
				<g clip-path="url(#r)"><rect width="%3$d" height="20" fill="#555"/>\
				<rect x="%3$d" width="%4$d" height="20" fill="#256abf"/></g>\
				<g fill="#fff" text-anchor="middle" font-family="Verdana,Geneva,DejaVu Sans,sans-serif" font-size="11">\
				<text x="%5$.1f" y="14">%6$s</text><text x="%7$.1f" y="14">%8$s</text></g></svg>""", width, text,
				labelWidth, valueWidth, labelWidth / 2.0, escape(label), labelWidth + valueWidth / 2.0, escape(value));
	}

	/** 950, 12.3k, 4.5M: the size of a number at a glance. */
	static String compact(long value) {
		if (value < 1_000) {
			return Long.toString(value);
		}
		if (value < 999_950) {
			return oneDecimal(value / 1_000.0) + "k";
		}
		return oneDecimal(value / 1_000_000.0) + "M";
	}

	private static String oneDecimal(double value) {
		String text = String.format(Locale.ROOT, "%.1f", value);
		return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
	}

	/** Verdana 11px, rounded up; good enough that the text never touches the edges. */
	static int textWidth(String text) {
		double width = 0;
		for (char c : text.toCharArray()) {
			if (c == '.' || c == ',' || c == ' ' || c == 'i' || c == 'l') {
				width += 3.9;
			}
			else if (c == 'm' || c == 'w' || c == 'M' || c == 'W') {
				width += 10.7;
			}
			else if (Character.isUpperCase(c)) {
				width += 7.8;
			}
			else {
				width += 7.0;
			}
		}
		return (int) Math.ceil(width);
	}

	private static String escape(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

}
