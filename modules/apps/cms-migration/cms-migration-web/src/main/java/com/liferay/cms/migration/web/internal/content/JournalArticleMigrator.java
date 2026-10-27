/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.content;

import com.liferay.cms.migration.web.internal.constants.CMSMigrationConstants;
import com.liferay.cms.migration.web.internal.migration.CMSMigrationContext;
import com.liferay.cms.migration.web.internal.structure.DDMFormValuesConverter;
import com.liferay.cms.migration.web.internal.structure.ObjectDefinitionMigrator;
import com.liferay.dynamic.data.mapping.model.DDMFieldAttributeTable;
import com.liferay.dynamic.data.mapping.model.DDMFieldTable;
import com.liferay.dynamic.data.mapping.model.Value;
import com.liferay.dynamic.data.mapping.service.DDMFieldLocalService;
import com.liferay.dynamic.data.mapping.storage.DDMFormFieldValue;
import com.liferay.dynamic.data.mapping.storage.DDMFormValues;
import com.liferay.journal.constants.JournalArticleConstants;
import com.liferay.journal.constants.JournalFolderConstants;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.model.JournalArticleResource;
import com.liferay.journal.model.JournalFolder;
import com.liferay.journal.service.JournalArticleLocalService;
import com.liferay.journal.service.JournalArticleResourceLocalService;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.petra.sql.dsl.DSLQueryFactoryUtil;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.module.service.Snapshot;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.workflow.WorkflowConstants;

import java.io.Serializable;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Migrates the latest version of every web content article of a site. An
 * article with an approved version migrates that version; an article that was
 * never approved migrates as a draft.
 *
 * @author Lino Alves
 */
public class JournalArticleMigrator {

	public List<JournalArticleResource> getJournalArticleResources(
		CMSMigrationContext cmsMigrationContext) {

		return _journalArticleResourceLocalService.getArticleResources(
			cmsMigrationContext.getSourceGroupId());
	}

	public ObjectEntry migrate(
			CMSMigrationContext cmsMigrationContext, long resourcePrimKey)
		throws PortalException {

		ObjectEntry objectEntry = cmsMigrationContext.getObjectEntry(
			JournalArticle.class.getName(), resourcePrimKey);

		if (objectEntry != null) {
			return objectEntry;
		}

		JournalArticle journalArticle =
			_journalArticleLocalService.fetchLatestArticle(
				resourcePrimKey, WorkflowConstants.STATUS_APPROVED);

		boolean approved = true;

		if (journalArticle == null) {
			approved = false;

			journalArticle = _journalArticleLocalService.fetchLatestArticle(
				resourcePrimKey, WorkflowConstants.STATUS_ANY);
		}

		if ((journalArticle == null) || journalArticle.isInTrash() ||
			(journalArticle.getClassNameId() !=
				JournalArticleConstants.CLASS_NAME_ID_DEFAULT)) {

			return null;
		}

		ObjectDefinition objectDefinition =
			_objectDefinitionMigrator.getOrAddDDMStructureObjectDefinition(
				cmsMigrationContext, journalArticle.getDDMStructure());

		String externalReferenceCode =
			CMSMigrationConstants.
				EXTERNAL_REFERENCE_CODE_PREFIX_JOURNAL_ARTICLE +
					resourcePrimKey;

		Map<String, Serializable> values = _ddmFormValuesConverter.getValues(
			cmsMigrationContext, _getDDMFormValues(journalArticle));

		Map<Locale, String> descriptionMap = new HashMap<>();
		Map<Locale, String> titleMap = new HashMap<>();

		for (String languageId :
				_journalArticleLocalService.getArticleLocalizationLanguageIds(
					journalArticle.getCompanyId(), journalArticle.getId())) {

			Locale locale = LocaleUtil.fromLanguageId(languageId, false);

			descriptionMap.put(
				locale, journalArticle.getDescription(languageId, false));
			titleMap.put(locale, journalArticle.getTitle(languageId, false));
		}

		values.put(
			CMSMigrationConstants.OBJECT_FIELD_NAME_DESCRIPTION + "_i18n",
			LocalizedValuesUtil.toValues(descriptionMap, value -> value));
		values.put(
			CMSMigrationConstants.OBJECT_FIELD_NAME_TITLE + "_i18n",
			LocalizedValuesUtil.toValues(
				titleMap, value -> _truncateTitle(cmsMigrationContext, value)));

		_putSmallImageValues(
			cmsMigrationContext, externalReferenceCode, journalArticle,
			objectDefinition, values);

		Date expirationDate = journalArticle.getExpirationDate();

		if ((expirationDate != null) && expirationDate.after(new Date())) {
			values.put("expirationDate", expirationDate);
		}

		objectEntry = _objectEntryWriter.write(
			cmsMigrationContext, JournalArticle.class.getName(),
			resourcePrimKey, objectDefinition,
			_getObjectEntryFolderId(cmsMigrationContext, journalArticle),
			externalReferenceCode, journalArticle.getUserId(),
			journalArticle.getDefaultLanguageId(), values,
			LocalizedValuesUtil.toLanguageIdMap(
				journalArticle.getFriendlyURLMap()),
			journalArticle.getCreateDate(), journalArticle.getModifiedDate(),
			approved);

		cmsMigrationContext.getCMSMigrationReport(
		).increment(
			CMSMigrationConstants.PHASE_JOURNAL_ARTICLES
		);

		return objectEntry;
	}

	public void migrateAll(CMSMigrationContext cmsMigrationContext) {
		for (JournalArticleResource journalArticleResource :
				getJournalArticleResources(cmsMigrationContext)) {

			cmsMigrationContext.sendStatus(
				journalArticleResource.getArticleId());

			try {
				migrate(
					cmsMigrationContext,
					journalArticleResource.getResourcePrimKey());
			}
			catch (Exception exception) {
				cmsMigrationContext.getCMSMigrationReport(
				).addError(
					StringBundler.concat(
						"Unable to migrate the web content ",
						journalArticleResource.getArticleId(), ": ",
						exception.getMessage())
				);
			}
		}
	}

	private void _addLocalizedValues(
		Map<String, Map<String, String>> attributesMap,
		List<DDMFormFieldValue> ddmFormFieldValues,
		List<String> unavailableLanguageIds) {

		for (DDMFormFieldValue ddmFormFieldValue : ddmFormFieldValues) {
			Value value = ddmFormFieldValue.getValue();

			if ((value != null) && value.isLocalized()) {
				for (String languageId : unavailableLanguageIds) {
					Map<String, String> attributes = attributesMap.get(
						ddmFormFieldValue.getInstanceId() + StringPool.POUND +
							languageId);

					if (attributes != null) {
						value.addString(
							LocaleUtil.fromLanguageId(languageId, false),
							_getValueString(attributes));
					}
				}
			}

			_addLocalizedValues(
				attributesMap, ddmFormFieldValue.getNestedDDMFormFieldValues(),
				unavailableLanguageIds);
		}
	}

	private DDMFormValues _getDDMFormValues(JournalArticle journalArticle) {
		DDMFormValues ddmFormValues = journalArticle.getDDMFormValues();

		if (ddmFormValues == null) {
			return null;
		}

		List<String> unavailableLanguageIds = new ArrayList<>();

		for (String languageId : journalArticle.getAvailableLanguageIds()) {
			if (!_language.isAvailableLocale(languageId)) {
				unavailableLanguageIds.add(languageId);
			}
		}

		if (unavailableLanguageIds.isEmpty()) {
			return ddmFormValues;
		}

		Map<String, Map<String, String>> attributesMap = new HashMap<>();

		for (Object[] columns :
				_ddmFieldLocalService.<List<Object[]>>dslQuery(
					DSLQueryFactoryUtil.select(
						DDMFieldTable.INSTANCE.instanceId,
						DDMFieldAttributeTable.INSTANCE.languageId,
						DDMFieldAttributeTable.INSTANCE.attributeName,
						DDMFieldAttributeTable.INSTANCE.largeAttributeValue,
						DDMFieldAttributeTable.INSTANCE.smallAttributeValue
					).from(
						DDMFieldTable.INSTANCE
					).innerJoinON(
						DDMFieldAttributeTable.INSTANCE,
						DDMFieldAttributeTable.INSTANCE.fieldId.eq(
							DDMFieldTable.INSTANCE.fieldId)
					).where(
						DDMFieldTable.INSTANCE.storageId.eq(
							journalArticle.getId()
						).and(
							DDMFieldAttributeTable.INSTANCE.languageId.in(
								unavailableLanguageIds.toArray(new String[0]))
						)
					))) {

			String attributeValue = GetterUtil.getString(columns[3]);

			if (Validator.isBlank(attributeValue)) {
				attributeValue = GetterUtil.getString(columns[4]);
			}

			Map<String, String> attributes = attributesMap.computeIfAbsent(
				GetterUtil.getString(columns[0]) + StringPool.POUND +
					GetterUtil.getString(columns[1]),
				attributesKey -> new HashMap<>());

			attributes.put(GetterUtil.getString(columns[2]), attributeValue);
		}

		_addLocalizedValues(
			attributesMap, ddmFormValues.getDDMFormFieldValues(),
			unavailableLanguageIds);

		return ddmFormValues;
	}

	private long _getObjectEntryFolderId(
		CMSMigrationContext cmsMigrationContext,
		JournalArticle journalArticle) {

		long objectEntryFolderId = cmsMigrationContext.getObjectEntryFolderId(
			JournalFolder.class.getName(), journalArticle.getFolderId());

		if (objectEntryFolderId == 0) {
			objectEntryFolderId = cmsMigrationContext.getObjectEntryFolderId(
				JournalFolder.class.getName(),
				JournalFolderConstants.DEFAULT_PARENT_FOLDER_ID);
		}

		return objectEntryFolderId;
	}

	private String _getValueString(Map<String, String> attributes) {
		if ((attributes.size() == 1) &&
			attributes.containsKey(StringPool.BLANK)) {

			return attributes.get(StringPool.BLANK);
		}

		JSONObject jsonObject = _jsonFactory.createJSONObject();

		for (Map.Entry<String, String> entry : attributes.entrySet()) {
			jsonObject.put(
				entry.getKey(),
				_jsonFactory.looseDeserialize(entry.getValue()));
		}

		return jsonObject.toString();
	}

	private void _putSmallImageValues(
		CMSMigrationContext cmsMigrationContext, String externalReferenceCode,
		JournalArticle journalArticle, ObjectDefinition objectDefinition,
		Map<String, Serializable> values) {

		int smallImageSource = journalArticle.getSmallImageSource();

		long fileEntryId = 0;

		if (smallImageSource ==
				JournalArticleConstants.
					SMALL_IMAGE_SOURCE_DOCUMENTS_AND_MEDIA) {

			fileEntryId = _attachmentCopier.copy(
				cmsMigrationContext, objectDefinition,
				CMSMigrationConstants.OBJECT_FIELD_NAME_SMALL_IMAGE,
				externalReferenceCode + "_SMALL_IMAGE",
				journalArticle.getSmallImageId());
		}
		else if (smallImageSource ==
					JournalArticleConstants.SMALL_IMAGE_SOURCE_URL) {

			if (Validator.isNotNull(journalArticle.getSmallImageURL())) {
				values.put(
					CMSMigrationConstants.OBJECT_FIELD_NAME_SMALL_IMAGE_URL,
					journalArticle.getSmallImageURL());
			}
		}
		else if (smallImageSource ==
					JournalArticleConstants.SMALL_IMAGE_SOURCE_USER_COMPUTER) {

			fileEntryId = _attachmentCopier.copyImage(
				cmsMigrationContext, objectDefinition,
				CMSMigrationConstants.OBJECT_FIELD_NAME_SMALL_IMAGE,
				externalReferenceCode + "_SMALL_IMAGE",
				journalArticle.getSmallImageId());
		}

		if (fileEntryId > 0) {
			values.put(
				CMSMigrationConstants.OBJECT_FIELD_NAME_SMALL_IMAGE,
				fileEntryId);
		}
	}

	private String _truncateTitle(
		CMSMigrationContext cmsMigrationContext, String title) {

		if (title.length() > _TITLE_MAX_LENGTH) {
			cmsMigrationContext.getCMSMigrationReport(
			).addWarning(
				"Truncated the title of the web content " + title
			);
		}

		return LocalizedValuesUtil.truncate(title, _TITLE_MAX_LENGTH);
	}

	private static final int _TITLE_MAX_LENGTH = 280;

	private static final Snapshot<DDMFieldLocalService>
		_ddmFieldLocalServiceSnapshot = new Snapshot<>(
			JournalArticleMigrator.class, DDMFieldLocalService.class);
	private static final Snapshot<JournalArticleLocalService>
		_journalArticleLocalServiceSnapshot = new Snapshot<>(
			JournalArticleMigrator.class, JournalArticleLocalService.class);
	private static final Snapshot<JournalArticleResourceLocalService>
		_journalArticleResourceLocalServiceSnapshot = new Snapshot<>(
			JournalArticleMigrator.class,
			JournalArticleResourceLocalService.class);
	private static final Snapshot<JSONFactory> _jsonFactorySnapshot =
		new Snapshot<>(JournalArticleMigrator.class, JSONFactory.class);
	private static final Snapshot<Language> _languageSnapshot = new Snapshot<>(
		JournalArticleMigrator.class, Language.class);

	private final AttachmentCopier _attachmentCopier = new AttachmentCopier();
	private final DDMFieldLocalService _ddmFieldLocalService =
		_ddmFieldLocalServiceSnapshot.get();
	private final DDMFormValuesConverter _ddmFormValuesConverter =
		new DDMFormValuesConverter();
	private final JournalArticleLocalService _journalArticleLocalService =
		_journalArticleLocalServiceSnapshot.get();
	private final JournalArticleResourceLocalService
		_journalArticleResourceLocalService =
			_journalArticleResourceLocalServiceSnapshot.get();
	private final JSONFactory _jsonFactory = _jsonFactorySnapshot.get();
	private final Language _language = _languageSnapshot.get();
	private final ObjectDefinitionMigrator _objectDefinitionMigrator =
		new ObjectDefinitionMigrator();
	private final ObjectEntryWriter _objectEntryWriter =
		new ObjectEntryWriter();

}