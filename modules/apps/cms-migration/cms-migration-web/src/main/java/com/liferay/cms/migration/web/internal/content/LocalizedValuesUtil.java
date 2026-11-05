/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.content;

import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.Validator;

import java.io.Serializable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * @author Lino Alves
 */
public class LocalizedValuesUtil {

	public static void putSiteDefaultValue(
		Map<Locale, String> localizedValues, String defaultValue) {

		Locale locale = LocaleUtil.getSiteDefault();

		if (Validator.isNotNull(localizedValues.get(locale))) {
			return;
		}

		if (Validator.isNull(defaultValue)) {
			defaultValue = localizedValues.get(LocaleUtil.getDefault());
		}

		if (Validator.isNull(defaultValue)) {
			TreeMap<String, String> languageIdMap = new TreeMap<>(
				toLanguageIdMap(localizedValues));

			Map.Entry<String, String> entry = languageIdMap.firstEntry();

			if (entry != null) {
				defaultValue = entry.getValue();
			}
		}

		localizedValues.put(locale, defaultValue);
	}

	public static Map<String, String> toLanguageIdMap(
		Map<Locale, String> localizedValues) {

		Map<String, String> languageIdMap = new HashMap<>();

		if (localizedValues == null) {
			return languageIdMap;
		}

		for (Map.Entry<Locale, String> entry : localizedValues.entrySet()) {
			if (Validator.isNotNull(entry.getValue())) {
				languageIdMap.put(
					LocaleUtil.toLanguageId(entry.getKey()), entry.getValue());
			}
		}

		return languageIdMap;
	}

	public static Serializable toValues(
		Map<Locale, String> localizedValues,
		Function<String, String> function) {

		HashMap<String, String> values = new HashMap<>();

		for (Map.Entry<String, String> entry :
				toLanguageIdMap(
					localizedValues
				).entrySet()) {

			values.put(entry.getKey(), function.apply(entry.getValue()));
		}

		return values;
	}

	public static String truncate(String value, int maxLength) {
		if ((value == null) || (value.length() <= maxLength)) {
			return value;
		}

		return value.substring(0, maxLength);
	}

}