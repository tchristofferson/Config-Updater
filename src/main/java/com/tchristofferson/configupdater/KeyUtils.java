package com.tchristofferson.configupdater;

public class KeyUtils {

	public static boolean isSubKeyOf(final String parentKey, final String subKey, final char separator) {
		if (parentKey.isEmpty())
			return false;

		return subKey.startsWith(parentKey)
				&& subKey.substring(parentKey.length()).startsWith(String.valueOf(separator));
	}

	public static String getIndents(final String key, final char separator) {
		final String[] splitKey = key.split("[" + separator + "]");
		final StringBuilder builder = new StringBuilder();

		for (int i = 1; i < splitKey.length; i++) {
			builder.append("  ");
		}
		return builder.toString();
	}
}
