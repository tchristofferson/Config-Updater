package com.tchristofferson.configupdater;

public class KeyUtils {

	/**
	 * Checks if the subKey is a sub path of the parentKey.
	 *
	 * @param parentKey the parent key to check against.
	 * @param subKey the part of the key to check if it is a sub path of the parent key.
	 * @param separator the separator between each part of the key. The default is a dot.
	 * @return true if the subKey is a sub path of the parentKey; returns false if the parentKey is empty or does not contain the subKey.
	 */
	public static boolean isSubKeyOf(final String parentKey, final String subKey, final char separator) {
		if (parentKey.isEmpty())
			return false;

		return subKey.startsWith(parentKey)
				&& subKey.substring(parentKey.length()).startsWith(String.valueOf(separator));
	}

	/**
	 * Gets the amount of indentation spaces for the provided key.
	 *
	 * @param key the key to check for the amount of indentation spaces.
	 * @param separator the separator used in the nested path. The default separator is a dot.
	 * @return a string contains only the amount of indentation spaces to add.
	 */
	public static String getIndents(final String key, final char separator) {
		final String[] splitKey = key.split("[" + separator + "]");
		final StringBuilder builder = new StringBuilder();

		for (int i = 1; i < splitKey.length; i++) {
			builder.append("  ");
		}
		return builder.toString();
	}
}
