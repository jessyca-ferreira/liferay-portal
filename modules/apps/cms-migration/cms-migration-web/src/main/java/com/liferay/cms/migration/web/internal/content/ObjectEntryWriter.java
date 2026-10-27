/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.content;

import com.liferay.asset.kernel.model.AssetEntry;
import com.liferay.asset.kernel.service.AssetEntryLocalService;
import com.liferay.cms.migration.web.internal.migration.CMSMigrationContext;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.module.service.Snapshot;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.util.MapUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.workflow.WorkflowConstants;

import java.io.Serializable;

import java.util.Date;
import java.util.HashMap;
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

		for (Map.Entry<String, Serializable> entry : values.entrySet()) {
			String key = entry.getKey();

			if (key.endsWith("_i18n") && (entry.getValue() instanceof Map)) {
				entry.setValue(
					(Serializable)_getAvailableLocalizedValues(
						availableDefaultLanguageId, defaultLanguageId,
						depotGroupId,
						(Map<String, Serializable>)entry.getValue()));
			}
		}

		values.put("externalReferenceCode", externalReferenceCode);

		ServiceContext serviceContext =
			(ServiceContext)cmsMigrationContext.getServiceContext(
			).clone();

		serviceContext.setAddGroupPermissions(true);
		serviceContext.setAddGuestPermissions(true);

		AssetEntry assetEntry = _assetEntryLocalService.fetchEntry(
			className, classPK);

		if (assetEntry != null) {
			serviceContext.setAssetCategoryIds(assetEntry.getCategoryIds());
			serviceContext.setAssetTagNames(assetEntry.getTagNames());
		}

		if (MapUtil.isNotEmpty(friendlyURLMap)) {
			serviceContext.setAttribute(
				"friendlyUrlMap",
				_getAvailableLocalizedValues(
					availableDefaultLanguageId, defaultLanguageId, depotGroupId,
					friendlyURLMap));
		}

		serviceContext.setUserId(cmsMigrationContext.getUserId());

		if (approved) {
			serviceContext.setWorkflowAction(WorkflowConstants.ACTION_PUBLISH);
		}
		else {
			serviceContext.setWorkflowAction(
				WorkflowConstants.ACTION_SAVE_DRAFT);
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

	private static final Snapshot<AssetEntryLocalService>
		_assetEntryLocalServiceSnapshot = new Snapshot<>(
			ObjectEntryWriter.class, AssetEntryLocalService.class);
	private static final Snapshot<Language> _languageSnapshot = new Snapshot<>(
		ObjectEntryWriter.class, Language.class);
	private static final Snapshot<ObjectEntryLocalService>
		_objectEntryLocalServiceSnapshot = new Snapshot<>(
			ObjectEntryWriter.class, ObjectEntryLocalService.class);
	private static final Snapshot<Portal> _portalSnapshot = new Snapshot<>(
		ObjectEntryWriter.class, Portal.class);
	private static final Snapshot<UserLocalService> _userLocalServiceSnapshot =
		new Snapshot<>(ObjectEntryWriter.class, UserLocalService.class);

	private final AssetEntryLocalService _assetEntryLocalService =
		_assetEntryLocalServiceSnapshot.get();
	private final Language _language = _languageSnapshot.get();
	private final ObjectEntryLocalService _objectEntryLocalService =
		_objectEntryLocalServiceSnapshot.get();
	private final Portal _portal = _portalSnapshot.get();
	private final UserLocalService _userLocalService =
		_userLocalServiceSnapshot.get();

}