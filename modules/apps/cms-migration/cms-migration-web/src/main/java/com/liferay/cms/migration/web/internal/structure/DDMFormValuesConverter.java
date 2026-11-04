/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.structure;

import com.liferay.cms.migration.web.internal.constants.CMSMigrationConstants;
import com.liferay.cms.migration.web.internal.content.AttachmentAlternativeTextWriter;
import com.liferay.cms.migration.web.internal.migration.CMSMigrationContext;
import com.liferay.cms.migration.web.internal.reference.HTMLReferenceRewriter;
import com.liferay.document.library.kernel.service.DLAppLocalService;
import com.liferay.dynamic.data.mapping.form.field.type.constants.DDMFormFieldTypeConstants;
import com.liferay.dynamic.data.mapping.model.DDMFormField;
import com.liferay.dynamic.data.mapping.model.Value;
import com.liferay.dynamic.data.mapping.storage.DDMFormFieldValue;
import com.liferay.dynamic.data.mapping.storage.DDMFormValues;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.service.JournalArticleLocalService;
import com.liferay.object.constants.ObjectFieldConstants;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.json.JSONUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;

import java.io.Serializable;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Converts Dynamic Data Mapping form values into object entry values. Field
 * references to documents and web content are repointed to the migrated
 * object entries as they are converted.
 *
 * @author Lino Alves
 */
public class DDMFormValuesConverter {

	public DDMFormValuesConverter(
		AttachmentAlternativeTextWriter attachmentAlternativeTextWriter,
		DLAppLocalService dlAppLocalService,
		HTMLReferenceRewriter htmlReferenceRewriter,
		JournalArticleLocalService journalArticleLocalService,
		JSONFactory jsonFactory,
		ObjectDefinitionMigrator objectDefinitionMigrator) {

		_attachmentAlternativeTextWriter = attachmentAlternativeTextWriter;
		_dlAppLocalService = dlAppLocalService;
		_htmlReferenceRewriter = htmlReferenceRewriter;
		_journalArticleLocalService = journalArticleLocalService;
		_jsonFactory = jsonFactory;
		_objectDefinitionMigrator = objectDefinitionMigrator;
	}

	public Map<String, Serializable> getValues(
		CMSMigrationContext cmsMigrationContext, DDMFormValues ddmFormValues) {

		Map<String, Serializable> values = new HashMap<>();

		if (ddmFormValues == null) {
			return values;
		}

		_putValues(
			cmsMigrationContext, ddmFormValues.getDDMFormFieldValues(),
			ddmFormValues.getDefaultLocale(), values);

		return values;
	}

	/**
	 * Returns the data that Web Content templates see for a reference field
	 * after its target was migrated. The format matches the original field
	 * data, so FreeMarker code that parses it keeps working.
	 */
	public String rewriteReferenceData(
		CMSMigrationContext cmsMigrationContext, String data, String type) {

		if (Validator.isNull(data) || !JSONUtil.isJSONObject(data)) {
			return data;
		}

		try {
			JSONObject jsonObject = _jsonFactory.createJSONObject(data);

			if (type.equals(DDMFormFieldTypeConstants.DDM_IMAGE) ||
				type.equals(DDMFormFieldTypeConstants.DOCUMENT_LIBRARY) ||
				type.equals(DDMFormFieldTypeConstants.IMAGE)) {

				FileEntry fileEntry = _getAttachmentFileEntry(
					cmsMigrationContext, jsonObject);

				if (fileEntry == null) {
					return data;
				}

				jsonObject.put(
					"fileEntryId", fileEntry.getFileEntryId()
				).put(
					"groupId", fileEntry.getGroupId()
				).put(
					"uuid", fileEntry.getUuid()
				);

				if (jsonObject.has("classPK")) {
					jsonObject.put("classPK", fileEntry.getFileEntryId());
				}

				return jsonObject.toString();
			}

			if (type.equals(DDMFormFieldTypeConstants.JOURNAL_ARTICLE)) {
				return _rewriteJournalArticleData(
					cmsMigrationContext, jsonObject, data);
			}
		}
		catch (Exception exception) {
			cmsMigrationContext.getCMSMigrationReport(
			).addWarning(
				StringBundler.concat(
					"Unable to rewrite reference ", data, ": ",
					exception.getMessage())
			);
		}

		return data;
	}

	private FileEntry _getAttachmentFileEntry(
			CMSMigrationContext cmsMigrationContext, JSONObject jsonObject)
		throws Exception {

		FileEntry fileEntry = _getFileEntry(jsonObject);

		if (fileEntry == null) {
			return null;
		}

		ObjectEntry objectEntry = cmsMigrationContext.getOrMigrateFileEntry(
			fileEntry);

		if (objectEntry == null) {
			return null;
		}

		Map<String, Serializable> values = objectEntry.getValues();

		long fileEntryId = GetterUtil.getLong(
			values.get(CMSMigrationConstants.OBJECT_FIELD_NAME_FILE));

		if (fileEntryId == 0) {
			return null;
		}

		return _dlAppLocalService.getFileEntry(fileEntryId);
	}

	private FileEntry _getFileEntry(JSONObject jsonObject) {
		try {
			String uuid = jsonObject.getString("uuid");
			long groupId = jsonObject.getLong("groupId");

			if (Validator.isNotNull(uuid) && (groupId > 0)) {
				return _dlAppLocalService.getFileEntryByUuidAndGroupId(
					uuid, groupId);
			}

			long fileEntryId = jsonObject.getLong("fileEntryId");

			if (fileEntryId == 0) {
				fileEntryId = jsonObject.getLong("classPK");
			}

			if (fileEntryId > 0) {
				return _dlAppLocalService.getFileEntry(fileEntryId);
			}
		}
		catch (Exception exception) {
			if (_log.isDebugEnabled()) {
				_log.debug(exception);
			}
		}

		return null;
	}

	private Serializable _getObjectFieldValue(
		CMSMigrationContext cmsMigrationContext, DDMFormField ddmFormField,
		String data) {

		if (Validator.isNull(data)) {
			return null;
		}

		String businessType = CMSMigrationDDMFormFieldUtil.getBusinessType(
			ddmFormField);

		if (businessType.equals(
				ObjectFieldConstants.BUSINESS_TYPE_ATTACHMENT)) {

			try {
				JSONObject jsonObject = _jsonFactory.createJSONObject(data);

				FileEntry fileEntry = _getAttachmentFileEntry(
					cmsMigrationContext, jsonObject);

				if (fileEntry != null) {
					_attachmentAlternativeTextWriter.write(
						cmsMigrationContext, fileEntry,
						jsonObject.getString("alt"));

					return fileEntry.getFileEntryId();
				}
			}
			catch (Exception exception) {
				cmsMigrationContext.getCMSMigrationReport(
				).addWarning(
					StringBundler.concat(
						"Unable to migrate the file referenced by ",
						ddmFormField.getFieldReference(), ": ",
						exception.getMessage())
				);
			}

			return null;
		}
		else if (businessType.equals(
					ObjectFieldConstants.BUSINESS_TYPE_BOOLEAN)) {

			return GetterUtil.getBoolean(data);
		}
		else if (businessType.equals(ObjectFieldConstants.BUSINESS_TYPE_DATE)) {
			return _parseDate("yyyy-MM-dd", data);
		}
		else if (businessType.equals(
					ObjectFieldConstants.BUSINESS_TYPE_DATE_TIME)) {

			return _parseDate("yyyy-MM-dd HH:mm", data);
		}
		else if (businessType.equals(
					ObjectFieldConstants.BUSINESS_TYPE_DECIMAL)) {

			return GetterUtil.getDouble(data);
		}
		else if (businessType.equals(
					ObjectFieldConstants.BUSINESS_TYPE_LONG_INTEGER)) {

			return GetterUtil.getLong(data);
		}
		else if (businessType.equals(
					ObjectFieldConstants.BUSINESS_TYPE_MULTISELECT_PICKLIST)) {

			List<String> keys = new ArrayList<>();

			for (String optionValue : _getOptionValues(data)) {
				keys.add(
					CMSMigrationDDMFormFieldUtil.getListTypeEntryKey(
						optionValue));
			}

			return StringUtil.merge(keys, StringPool.COMMA_AND_SPACE);
		}
		else if (businessType.equals(
					ObjectFieldConstants.BUSINESS_TYPE_PICKLIST)) {

			List<String> optionValues = _getOptionValues(data);

			if (optionValues.isEmpty()) {
				return null;
			}

			return CMSMigrationDDMFormFieldUtil.getListTypeEntryKey(
				optionValues.get(0));
		}
		else if (businessType.equals(
					ObjectFieldConstants.BUSINESS_TYPE_RICH_TEXT)) {

			return _htmlReferenceRewriter.rewriteFileReferences(
				cmsMigrationContext, data);
		}

		return rewriteReferenceData(
			cmsMigrationContext, data, ddmFormField.getType());
	}

	private List<String> _getOptionValues(String data) {
		List<String> optionValues = new ArrayList<>();

		if (!JSONUtil.isJSONArray(data)) {
			optionValues.add(data);

			return optionValues;
		}

		try {
			JSONArray jsonArray = _jsonFactory.createJSONArray(data);

			for (int i = 0; i < jsonArray.length(); i++) {
				String optionValue = jsonArray.getString(i);

				if (Validator.isNotNull(optionValue)) {
					optionValues.add(optionValue);
				}
			}
		}
		catch (Exception exception) {
			if (_log.isDebugEnabled()) {
				_log.debug(exception);
			}
		}

		return optionValues;
	}

	private JSONObject _getValueJSONObject(
		CMSMigrationContext cmsMigrationContext,
		DDMFormFieldValue ddmFormFieldValue) {

		JSONObject jsonObject = JSONUtil.put(
			"instanceId", ddmFormFieldValue.getInstanceId()
		).put(
			"name", ddmFormFieldValue.getName()
		);

		Value value = ddmFormFieldValue.getValue();

		DDMFormField ddmFormField = ddmFormFieldValue.getDDMFormField();

		String type = StringPool.BLANK;

		if (ddmFormField != null) {
			type = ddmFormField.getType();
		}

		if (value != null) {
			JSONObject valuesJSONObject = _jsonFactory.createJSONObject();

			for (Map.Entry<Locale, String> entry :
					value.getValues(
					).entrySet()) {

				valuesJSONObject.put(
					LocaleUtil.toLanguageId(entry.getKey()),
					rewriteReferenceData(
						cmsMigrationContext, entry.getValue(), type));
			}

			jsonObject.put(
				"localized", value.isLocalized()
			).put(
				"values", valuesJSONObject
			);
		}

		JSONArray nestedJSONArray = _jsonFactory.createJSONArray();

		for (DDMFormFieldValue nestedDDMFormFieldValue :
				ddmFormFieldValue.getNestedDDMFormFieldValues()) {

			nestedJSONArray.put(
				_getValueJSONObject(
					cmsMigrationContext, nestedDDMFormFieldValue));
		}

		return jsonObject.put("nestedFieldValues", nestedJSONArray);
	}

	private Serializable _parseDate(String pattern, String data) {
		DateFormat dateFormat = new SimpleDateFormat(pattern);

		try {
			return dateFormat.parse(data);
		}
		catch (Exception exception) {
			if (_log.isDebugEnabled()) {
				_log.debug(exception);
			}

			return null;
		}
	}

	private void _putAltValues(
		DDMFormField ddmFormField, Value value,
		Map<String, Serializable> values) {

		Map<String, Serializable> altValues = new HashMap<>();

		for (Map.Entry<Locale, String> entry :
				value.getValues(
				).entrySet()) {

			if (!JSONUtil.isJSONObject(entry.getValue())) {
				continue;
			}

			try {
				JSONObject jsonObject = _jsonFactory.createJSONObject(
					entry.getValue());

				altValues.put(
					LocaleUtil.toLanguageId(entry.getKey()),
					jsonObject.getString("alt"));
			}
			catch (Exception exception) {
				if (_log.isDebugEnabled()) {
					_log.debug(exception);
				}
			}
		}

		values.put(
			CMSMigrationDDMFormFieldUtil.getAltObjectFieldName(ddmFormField) +
				"_i18n",
			(Serializable)altValues);
	}

	private void _putLeafValue(
		CMSMigrationContext cmsMigrationContext, DDMFormField ddmFormField,
		DDMFormFieldValue ddmFormFieldValue, Locale defaultLocale,
		Map<String, Serializable> values) {

		Value value = ddmFormFieldValue.getValue();

		if (value == null) {
			return;
		}

		String name = CMSMigrationDDMFormFieldUtil.getObjectFieldName(
			ddmFormField);

		if (CMSMigrationDDMFormFieldUtil.isLocalized(ddmFormField)) {
			Map<String, Serializable> localizedValues = new HashMap<>();

			for (Map.Entry<Locale, String> entry :
					value.getValues(
					).entrySet()) {

				Serializable objectFieldValue = _getObjectFieldValue(
					cmsMigrationContext, ddmFormField, entry.getValue());

				if (objectFieldValue != null) {
					localizedValues.put(
						LocaleUtil.toLanguageId(entry.getKey()),
						objectFieldValue);
				}
			}

			values.put(name + "_i18n", (Serializable)localizedValues);

			return;
		}

		Locale locale = defaultLocale;

		if (value.isLocalized() &&
			!value.getAvailableLocales(
			).contains(
				locale
			)) {

			locale = value.getDefaultLocale();
		}

		String data = value.getString(locale);

		Serializable objectFieldValue = _getObjectFieldValue(
			cmsMigrationContext, ddmFormField, data);

		if (objectFieldValue != null) {
			values.put(name, objectFieldValue);
		}

		if (CMSMigrationDDMFormFieldUtil.isTypeImage(ddmFormField)) {
			_putAltValues(ddmFormField, value, values);
		}
	}

	private void _putValues(
		CMSMigrationContext cmsMigrationContext,
		List<DDMFormFieldValue> ddmFormFieldValues, Locale defaultLocale,
		Map<String, Serializable> values) {

		Map<String, List<DDMFormFieldValue>> repeatableDDMFormFieldValuesMap =
			new LinkedHashMap<>();

		for (DDMFormFieldValue ddmFormFieldValue : ddmFormFieldValues) {
			DDMFormField ddmFormField = ddmFormFieldValue.getDDMFormField();

			if ((ddmFormField == null) ||
				CMSMigrationDDMFormFieldUtil.isIgnoredField(ddmFormField)) {

				continue;
			}

			if (CMSMigrationDDMFormFieldUtil.isJSONField(ddmFormField)) {
				List<DDMFormFieldValue> repeatableDDMFormFieldValues =
					repeatableDDMFormFieldValuesMap.computeIfAbsent(
						CMSMigrationDDMFormFieldUtil.getObjectFieldName(
							ddmFormField),
						key -> new ArrayList<>());

				repeatableDDMFormFieldValues.add(ddmFormFieldValue);

				continue;
			}

			if (!Objects.equals(
					ddmFormField.getType(),
					DDMFormFieldTypeConstants.FIELDSET)) {

				_putLeafValue(
					cmsMigrationContext, ddmFormField, ddmFormFieldValue,
					defaultLocale, values);
			}

			_putValues(
				cmsMigrationContext,
				ddmFormFieldValue.getNestedDDMFormFieldValues(), defaultLocale,
				values);
		}

		for (Map.Entry<String, List<DDMFormFieldValue>> entry :
				repeatableDDMFormFieldValuesMap.entrySet()) {

			JSONArray jsonArray = _jsonFactory.createJSONArray();

			for (DDMFormFieldValue ddmFormFieldValue : entry.getValue()) {
				jsonArray.put(
					_getValueJSONObject(
						cmsMigrationContext, ddmFormFieldValue));
			}

			values.put(entry.getKey(), jsonArray.toString());
		}
	}

	private String _rewriteJournalArticleData(
			CMSMigrationContext cmsMigrationContext, JSONObject jsonObject,
			String data)
		throws Exception {

		long classPK = jsonObject.getLong("classPK");

		JournalArticle journalArticle =
			_journalArticleLocalService.fetchLatestArticle(classPK);

		if (journalArticle == null) {
			return data;
		}

		ObjectDefinition objectDefinition =
			_objectDefinitionMigrator.getOrAddDDMStructureObjectDefinition(
				cmsMigrationContext, journalArticle.getDDMStructure());

		return JSONUtil.put(
			"className", objectDefinition.getClassName()
		).put(
			"externalReferenceCode",
			CMSMigrationConstants.
				EXTERNAL_REFERENCE_CODE_PREFIX_JOURNAL_ARTICLE +
					journalArticle.getResourcePrimKey()
		).put(
			"groupId", cmsMigrationContext.getDepotGroupId()
		).put(
			"objectDefinitionExternalReferenceCode",
			objectDefinition.getExternalReferenceCode()
		).put(
			"title", jsonObject.getString("title")
		).toString();
	}

	private static final Log _log = LogFactoryUtil.getLog(
		DDMFormValuesConverter.class);

	private final AttachmentAlternativeTextWriter
		_attachmentAlternativeTextWriter;
	private final DLAppLocalService _dlAppLocalService;
	private final HTMLReferenceRewriter _htmlReferenceRewriter;
	private final JournalArticleLocalService _journalArticleLocalService;
	private final JSONFactory _jsonFactory;
	private final ObjectDefinitionMigrator _objectDefinitionMigrator;

}