/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.migration.entry;

import com.liferay.dynamic.data.mapping.model.DDMFormField;

import java.io.Serializable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Jessyca Ferreira
 */
public class ObjectEntryValues {

	public Map<DDMFormField, List<ObjectEntryValues>>
		getRepeatableGroupItemsMap() {

		return _repeatableGroupItemsMap;
	}

	public Map<String, Serializable> getValues() {
		return _values;
	}

	private final Map<DDMFormField, List<ObjectEntryValues>>
		_repeatableGroupItemsMap = new LinkedHashMap<>();
	private final Map<String, Serializable> _values = new HashMap<>();

}