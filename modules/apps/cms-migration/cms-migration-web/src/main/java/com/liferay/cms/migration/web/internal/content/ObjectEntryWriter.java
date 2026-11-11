/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.content;

import com.liferay.asset.kernel.model.AssetEntry;
import com.liferay.asset.kernel.service.AssetEntryLocalService;
import com.liferay.cms.migration.web.internal.migration.CMSMigrationContext;
import com.liferay.cms.migration.web.internal.structure.CMSMigrationDDMFormFieldUtil;
import com.liferay.cms.migration.web.internal.structure.ObjectEntryValues;
import com.liferay.dynamic.data.mapping.model.DDMFormField;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.model.ObjectField;
import com.liferay.object.model.ObjectRelationship;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.object.service.ObjectFieldLocalService;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.util.MapUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.workflow.WorkflowConstants;

import java.io.Serializable;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Adds or updates the object entry that replaces a classic content item. The
 * entry is keyed by an external reference code derived from its source, so a
 * second run updates the entry instead of duplicating it. The entry is written
 * by the user who runs the migration, because attachments are stored per
 * user, and then attributed to the author of the classic item.
 *
 * @author Lino Alves
 */
public class ObjectEntryWriter {

	public ObjectEntryWriter(
		AssetEntryLocalService assetEntryLocalService, Language language,
		ObjectEntryLocalService objectEntryLocalService,
		ObjectFieldLocalService objectFieldLocalService, Portal portal,
		UserLocalService userLocalService) {

		_assetEntryLocalService = assetEntryLocalService;
		_language = language;
		_objectEntryLocalService = objectEntryLocalService;
		_objectFieldLocalService = objectFieldLocalService;
		_portal = portal;
		_userLocalService = userLocalService;
	}

	public ObjectEntry write(
			CMSMigrationContext cmsMigrationContext, String className,
			long classPK, ObjectDefinition objectDefinition,
			long objectEntryFolderId, String externalReferenceCode, long userId,
			String defaultLanguageId, Map<String, Serializable> values,
			Map<String, String> friendlyURLMap, Date createDate,
			Date modifiedDate, boolean approved)
		throws PortalException {

		long depotGroupId = cmsMigrationContext.getDepotGroupId();

		String availableDefaultLanguageId = _getAvailableDefaultLanguageId(
			defaultLanguageId, depotGroupId, values);

		_putAvailableLocalizedValues(
			availableDefaultLanguageId, defaultLanguageId, depotGroupId, values);

		values.put("externalReferenceCode", externalReferenceCode);

		ServiceContext serviceContext = _getServiceContext(
			approved, cmsMigrationContext);

		AssetEntry assetEntry = _assetEntryLocalService.fetchEntry(
			className, classPK);

		if (assetEntry != null) {
			serviceContext.setAssetCategoryIds(assetEntry.getCategoryIds());
			serviceContext.setAssetPriority(assetEntry.getPriority());
			serviceContext.setAssetTagNames(assetEntry.getTagNames());
		}

		if (MapUtil.isNotEmpty(friendlyURLMap)) {
			serviceContext.setAttribute(
				"friendlyUrlMap",
				_getAvailableLocalizedValues(
					availableDefaultLanguageId, defaultLanguageId, depotGroupId,
					friendlyURLMap));
		}

		ObjectEntry objectEntry = _objectEntryLocalService.fetchObjectEntry(
			externalReferenceCode, depotGroupId,
			objectDefinition.getObjectDefinitionId());

		if (objectEntry == null) {
			objectEntry = _objectEntryLocalService.addObjectEntry(
				depotGroupId, serviceContext.getUserId(),
				objectDefinition.getObjectDefinitionId(), objectEntryFolderId,
				availableDefaultLanguageId, values, serviceContext);

			if (!availableDefaultLanguageId.equals(defaultLanguageId)) {
				cmsMigrationContext.getCMSMigrationReport(
				).addWarning(
					StringBundler.concat(
						"Migrated the entry ", externalReferenceCode,
						" with the default language ",
						availableDefaultLanguageId)
				);
			}
		}
		else {
			objectEntry = _objectEntryLocalService.updateObjectEntry(
				serviceContext.getUserId(), objectEntry.getObjectEntryId(),
				objectEntryFolderId, values, serviceContext);
		}

		User user = _userLocalService.fetchUser(userId);

		if ((user != null) && !user.isGuestUser()) {
			objectEntry.setUserId(user.getUserId());
			objectEntry.setUserName(user.getFullName());
		}

		if (createDate != null) {
			objectEntry.setCreateDate(createDate);
		}

		if (modifiedDate != null) {
			objectEntry.setModifiedDate(modifiedDate);
		}

		objectEntry = _objectEntryLocalService.updateObjectEntry(objectEntry);

		cmsMigrationContext.addObjectEntry(className, classPK, objectEntry);

		return objectEntry;
	}

	public List<ObjectEntry> writeRepeatableGroupItems(
			boolean approved, CMSMigrationContext cmsMigrationContext,
			DDMFormField ddmFormField,
			List<ObjectEntryValues> itemObjectEntryValuesList,
			ObjectEntry objectEntry)
		throws PortalException {

		List<ObjectEntry> itemObjectEntries = new ArrayList<>();

		String defaultLanguageId = objectEntry.getDefaultLanguageId();

		long depotGroupId = cmsMigrationContext.getDepotGroupId();

		ObjectRelationship objectRelationship =
			cmsMigrationContext.getRepeatableGroupObjectRelationship(
				ddmFormField, objectEntry.getObjectDefinitionId());

		ObjectField objectField = _objectFieldLocalService.getObjectField(
			objectRelationship.getObjectFieldId2());

		long repeatableGroupObjectDefinitionId =
			objectRelationship.getObjectDefinitionId2();

		ServiceContext serviceContext = _getServiceContext(
			approved, cmsMigrationContext);

		for (int i = 0; i < itemObjectEntryValuesList.size(); i++) {
			ObjectEntryValues itemObjectEntryValues =
				itemObjectEntryValuesList.get(i);

			Map<String, Serializable> values = new HashMap<>(
				itemObjectEntryValues.getValues());

			_putAvailableLocalizedValues(
				defaultLanguageId, defaultLanguageId, depotGroupId, values);

			String externalReferenceCode =
				CMSMigrationDDMFormFieldUtil.
					getRepeatableGroupItemExternalReferenceCode(
						ddmFormField, i,
						objectEntry.getExternalReferenceCode());

			values.put("externalReferenceCode", externalReferenceCode);
			values.put(objectField.getName(), objectEntry.getObjectEntryId());

			ObjectEntry itemObjectEntry =
				_objectEntryLocalService.fetchObjectEntry(
					externalReferenceCode, depotGroupId,
					repeatableGroupObjectDefinitionId);

			if (itemObjectEntry == null) {
				itemObjectEntry = _objectEntryLocalService.addObjectEntry(
					depotGroupId, serviceContext.getUserId(),
					repeatableGroupObjectDefinitionId,
					objectEntry.getObjectEntryFolderId(), defaultLanguageId,
					values, serviceContext);
			}
			else {
				itemObjectEntry = _objectEntryLocalService.updateObjectEntry(
					serviceContext.getUserId(),
					itemObjectEntry.getObjectEntryId(),
					objectEntry.getObjectEntryFolderId(), values,
					serviceContext);
			}

			itemObjectEntry.setUserId(objectEntry.getUserId());
			itemObjectEntry.setUserName(objectEntry.getUserName());
			itemObjectEntry.setCreateDate(objectEntry.getCreateDate());
			itemObjectEntry.setModifiedDate(objectEntry.getModifiedDate());

			itemObjectEntries.add(
				_objectEntryLocalService.updateObjectEntry(itemObjectEntry));
		}

		for (int i = itemObjectEntryValuesList.size();; i++) {
			ObjectEntry itemObjectEntry =
				_objectEntryLocalService.fetchObjectEntry(
					CMSMigrationDDMFormFieldUtil.
						getRepeatableGroupItemExternalReferenceCode(
							ddmFormField, i,
							objectEntry.getExternalReferenceCode()),
					depotGroupId, repeatableGroupObjectDefinitionId);

			if (itemObjectEntry == null) {
				break;
			}

			_objectEntryLocalService.deleteObjectEntry(itemObjectEntry);
		}

		return itemObjectEntries;
	}

	private String _getAvailableDefaultLanguageId(
			String defaultLanguageId, long groupId,
			Map<String, Serializable> values)
		throws PortalException {

		if (_language.isAvailableLocale(groupId, defaultLanguageId)) {
			return defaultLanguageId;
		}

		Set<String> languageIds = new TreeSet<>();

		for (Map.Entry<String, Serializable> entry : values.entrySet()) {
			String key = entry.getKey();

			if (key.endsWith("_i18n") && (entry.getValue() instanceof Map)) {
				Map<String, Serializable> localizedValues =
					(Map<String, Serializable>)entry.getValue();

				languageIds.addAll(localizedValues.keySet());
			}
		}

		String siteDefaultLanguageId = _language.getLanguageId(
			_portal.getSiteDefaultLocale(groupId));

		if (languageIds.contains(siteDefaultLanguageId)) {
			return siteDefaultLanguageId;
		}

		for (String languageId : languageIds) {
			if (_language.isAvailableLocale(groupId, languageId)) {
				return languageId;
			}
		}

		return siteDefaultLanguageId;
	}

	private <T> HashMap<String, T> _getAvailableLocalizedValues(
		String availableDefaultLanguageId, String defaultLanguageId,
		long groupId, Map<String, T> localizedValues) {

		HashMap<String, T> availableLocalizedValues = new HashMap<>();

		for (Map.Entry<String, T> entry : localizedValues.entrySet()) {
			if (_language.isAvailableLocale(groupId, entry.getKey())) {
				availableLocalizedValues.put(entry.getKey(), entry.getValue());
			}
		}

		if (!availableLocalizedValues.containsKey(availableDefaultLanguageId) &&
			localizedValues.containsKey(defaultLanguageId)) {

			availableLocalizedValues.put(
				availableDefaultLanguageId,
				localizedValues.get(defaultLanguageId));
		}

		return availableLocalizedValues;
	}

	private ServiceContext _getServiceContext(
		boolean approved, CMSMigrationContext cmsMigrationContext) {

		ServiceContext serviceContext = cmsMigrationContext.getServiceContext();

		serviceContext = (ServiceContext)serviceContext.clone();

		serviceContext.setAddGroupPermissions(true);
		serviceContext.setAddGuestPermissions(true);
		serviceContext.setUserId(cmsMigrationContext.getUserId());

		if (approved) {
			serviceContext.setWorkflowAction(WorkflowConstants.ACTION_PUBLISH);
		}
		else {
			serviceContext.setWorkflowAction(
				WorkflowConstants.ACTION_SAVE_DRAFT);
		}

		return serviceContext;
	}

	private void _putAvailableLocalizedValues(
		String availableDefaultLanguageId, String defaultLanguageId,
		long groupId, Map<String, Serializable> values) {

		for (Map.Entry<String, Serializable> entry : values.entrySet()) {
			String key = entry.getKey();

			if (key.endsWith("_i18n") && (entry.getValue() instanceof Map)) {
				entry.setValue(
					(Serializable)_getAvailableLocalizedValues(
						availableDefaultLanguageId, defaultLanguageId, groupId,
						(Map<String, Serializable>)entry.getValue()));
			}
		}
	}

	private final AssetEntryLocalService _assetEntryLocalService;
	private final Language _language;
	private final ObjectEntryLocalService _objectEntryLocalService;
	private final ObjectFieldLocalService _objectFieldLocalService;
	private final Portal _portal;
	private final UserLocalService _userLocalService;

}