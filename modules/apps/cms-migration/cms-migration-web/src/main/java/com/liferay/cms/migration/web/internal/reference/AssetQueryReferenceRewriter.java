/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.reference;

import com.liferay.blogs.model.BlogsEntry;
import com.liferay.cms.migration.web.internal.migration.CMSMigrationContext;
import com.liferay.document.library.kernel.model.DLFileEntry;
import com.liferay.dynamic.data.mapping.model.DDMStructure;
import com.liferay.dynamic.data.mapping.service.DDMStructureLocalService;
import com.liferay.journal.model.JournalArticle;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rewrites the asset type filters of dynamic asset queries, as Asset
 * Publisher preferences and dynamic collections store them, so the queries
 * select the migrated object entries instead of the classic content.
 *
 * @author Lino Alves
 */
public class AssetQueryReferenceRewriter {

	public AssetQueryReferenceRewriter(
		DDMStructureLocalService ddmStructureLocalService,
		InfoItemReferenceMapper infoItemReferenceMapper, Portal portal) {

		_ddmStructureLocalService = ddmStructureLocalService;
		_infoItemReferenceMapper = infoItemReferenceMapper;
		_portal = portal;
	}

	/**
	 * Rewrites the settings in place.
	 *
	 * @return the item type that the rewritten query selects when it selects
	 *         one migrated type, or <code>null</code>
	 */
	public ReferenceScope rewrite(
		CMSMigrationContext cmsMigrationContext, Map<String, String[]> settings,
		String label) {

		String anyAssetType = _getValue(settings, "anyAssetType", "true");

		List<Long> classNameIds = new ArrayList<>();

		if (anyAssetType.equals("true")) {
			if (!cmsMigrationContext.getObjectDefinitions(
				).isEmpty()) {

				cmsMigrationContext.getCMSMigrationReport(
				).addWarning(
					label +
						" selects every asset type, so it lists the classic " +
							"content and the migrated content side by side"
				);
			}

			return null;
		}
		else if (anyAssetType.equals("false")) {
			for (String classNameId : _getValues(settings, "classNameIds")) {
				classNameIds.add(GetterUtil.getLong(classNameId));
			}
		}
		else {
			classNameIds.add(GetterUtil.getLong(anyAssetType));
		}

		Set<Long> newClassNameIds = new LinkedHashSet<>();
		List<ObjectDefinition> objectDefinitions = new ArrayList<>();
		DDMStructure ddmStructure = null;
		String sourceClassName = null;

		for (long classNameId : classNameIds) {
			String className = _infoItemReferenceMapper.getSourceClassName(
				classNameId);

			if (className == null) {
				newClassNameIds.add(classNameId);

				continue;
			}

			String factoryName = _factoryNames.get(className);

			List<Long> classTypeIds = _getClassTypeIds(settings, factoryName);

			settings.put("anyClassType" + factoryName, new String[] {"true"});
			settings.put("classTypeIds" + factoryName, new String[0]);

			for (Map.Entry<String, ObjectDefinition> entry :
					cmsMigrationContext.getObjectDefinitions(
					).entrySet()) {

				String[] keyParts = StringUtil.split(
					entry.getKey(), StringPool.POUND);

				if (!keyParts[0].equals(className)) {
					continue;
				}

				long classTypeId = GetterUtil.getLong(keyParts[1]);

				if ((classTypeIds != null) &&
					!classTypeIds.contains(classTypeId)) {

					continue;
				}

				ObjectDefinition objectDefinition = entry.getValue();

				newClassNameIds.add(
					_portal.getClassNameId(objectDefinition.getClassName()));
				objectDefinitions.add(objectDefinition);

				if (className.equals(JournalArticle.class.getName())) {
					ddmStructure = _ddmStructureLocalService.fetchDDMStructure(
						classTypeId);
				}

				sourceClassName = className;
			}

			if (Validator.isNotNull(
					_getValue(settings, "ddmStructureFieldName", null))) {

				cmsMigrationContext.getCMSMigrationReport(
				).addWarning(
					label +
						" filters by a structure field, which object entries " +
							"do not support, so the filter was removed"
				);

				settings.put("ddmStructureFieldName", new String[0]);
				settings.put("ddmStructureFieldValue", new String[0]);
			}
		}

		if (newClassNameIds.size() == 1) {
			settings.put(
				"anyAssetType",
				new String[] {
					String.valueOf(
						newClassNameIds.iterator(
						).next())
				});
		}
		else {
			settings.put("anyAssetType", new String[] {"false"});
		}

		settings.put(
			"classNameIds",
			ArrayUtil.toStringArray(newClassNameIds.toArray(new Long[0])));

		if (objectDefinitions.size() != 1) {
			return null;
		}

		return new ReferenceScope(
			ddmStructure, objectDefinitions.get(0), sourceClassName);
	}

	private List<Long> _getClassTypeIds(
		Map<String, String[]> settings, String factoryName) {

		String anyClassType = _getValue(
			settings, "anyClassType" + factoryName, "true");

		if (anyClassType.equals("true")) {
			return null;
		}

		List<Long> classTypeIds = new ArrayList<>();

		if (anyClassType.equals("false")) {
			for (String classTypeId :
					_getValues(settings, "classTypeIds" + factoryName)) {

				classTypeIds.add(GetterUtil.getLong(classTypeId));
			}
		}
		else {
			classTypeIds.add(GetterUtil.getLong(anyClassType));
		}

		return classTypeIds;
	}

	private String _getValue(
		Map<String, String[]> settings, String key, String defaultValue) {

		String[] values = _getValues(settings, key);

		if (values.length == 0) {
			return defaultValue;
		}

		return values[0];
	}

	private String[] _getValues(Map<String, String[]> settings, String key) {
		String[] values = settings.get(key);

		if (values == null) {
			return new String[0];
		}

		List<String> splitValues = new ArrayList<>();

		for (String value : values) {
			for (String splitValue : StringUtil.split(value)) {
				if (Validator.isNotNull(splitValue)) {
					splitValues.add(splitValue);
				}
			}
		}

		return splitValues.toArray(new String[0]);
	}

	private static final Map<String, String> _factoryNames = HashMapBuilder.put(
		BlogsEntry.class.getName(), "BlogsEntryAssetRendererFactory"
	).put(
		DLFileEntry.class.getName(), "DLFileEntryAssetRendererFactory"
	).put(
		JournalArticle.class.getName(), "JournalArticleAssetRendererFactory"
	).build();

	private final DDMStructureLocalService _ddmStructureLocalService;
	private final InfoItemReferenceMapper _infoItemReferenceMapper;
	private final Portal _portal;

}