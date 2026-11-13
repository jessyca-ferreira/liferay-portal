/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.structure;

import com.liferay.blogs.model.BlogsEntry;
import com.liferay.cms.migration.web.internal.constants.CMSMigrationConstants;
import com.liferay.cms.migration.web.internal.content.LocalizedValuesUtil;
import com.liferay.cms.migration.web.internal.migration.CMSMigrationContext;
import com.liferay.document.library.kernel.model.DLFileEntry;
import com.liferay.document.library.kernel.model.DLFileEntryType;
import com.liferay.dynamic.data.mapping.model.DDMForm;
import com.liferay.dynamic.data.mapping.model.DDMFormField;
import com.liferay.dynamic.data.mapping.model.DDMFormFieldOptions;
import com.liferay.dynamic.data.mapping.model.DDMStructure;
import com.liferay.dynamic.data.mapping.model.LocalizedValue;
import com.liferay.dynamic.data.mapping.service.DDMStructureLocalService;
import com.liferay.journal.model.JournalArticle;
import com.liferay.list.type.entry.util.ListTypeEntryUtil;
import com.liferay.list.type.model.ListTypeDefinition;
import com.liferay.list.type.model.ListTypeEntry;
import com.liferay.list.type.service.ListTypeDefinitionLocalService;
import com.liferay.object.constants.ObjectDefinitionConstants;
import com.liferay.object.constants.ObjectDefinitionSettingConstants;
import com.liferay.object.constants.ObjectFieldConstants;
import com.liferay.object.constants.ObjectFieldSettingConstants;
import com.liferay.object.constants.ObjectFolderConstants;
import com.liferay.object.constants.ObjectRelationshipConstants;
import com.liferay.object.field.builder.ObjectFieldBuilder;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectDefinitionSetting;
import com.liferay.object.model.ObjectField;
import com.liferay.object.model.ObjectFieldSetting;
import com.liferay.object.model.ObjectFolder;
import com.liferay.object.model.ObjectRelationship;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectDefinitionSettingLocalService;
import com.liferay.object.service.ObjectFieldLocalService;
import com.liferay.object.service.ObjectFieldSettingLocalService;
import com.liferay.object.service.ObjectFolderLocalService;
import com.liferay.object.service.ObjectRelationshipLocalService;
import com.liferay.object.tree.constants.TreeConstants;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Creates the CMS object definitions that replace Web Content structures,
 * Blogs, and Documents and Media file entry types. Each definition is keyed by
 * an external reference code derived from its source, so running the
 * migration again reuses the definition and only adds missing fields.
 *
 * @author Lino Alves
 */
public class ObjectDefinitionMigrator {

	public ObjectDefinitionMigrator(
		DDMStructureLocalService ddmStructureLocalService, Language language,
		ListTypeDefinitionLocalService listTypeDefinitionLocalService,
		ObjectDefinitionLocalService objectDefinitionLocalService,
		ObjectDefinitionSettingLocalService objectDefinitionSettingLocalService,
		ObjectFieldLocalService objectFieldLocalService,
		ObjectFieldSettingLocalService objectFieldSettingLocalService,
		ObjectFolderLocalService objectFolderLocalService,
		ObjectRelationshipLocalService objectRelationshipLocalService) {

		_ddmStructureLocalService = ddmStructureLocalService;
		_language = language;
		_listTypeDefinitionLocalService = listTypeDefinitionLocalService;
		_objectDefinitionLocalService = objectDefinitionLocalService;
		_objectDefinitionSettingLocalService =
			objectDefinitionSettingLocalService;
		_objectFieldLocalService = objectFieldLocalService;
		_objectFieldSettingLocalService = objectFieldSettingLocalService;
		_objectFolderLocalService = objectFolderLocalService;
		_objectRelationshipLocalService = objectRelationshipLocalService;
	}

	public ObjectDefinition getExternalVideoObjectDefinition(
			CMSMigrationContext cmsMigrationContext)
		throws PortalException {

		return _objectDefinitionLocalService.
			getObjectDefinitionByExternalReferenceCode(
				"L_CMS_EXTERNAL_VIDEO", cmsMigrationContext.getCompanyId());
	}

	public ObjectDefinition getOrAddBlogsEntryObjectDefinition(
			CMSMigrationContext cmsMigrationContext)
		throws PortalException {

		List<ObjectField> objectFields = new ArrayList<>(
			_getSystemObjectFields());

		objectFields.add(
			_createObjectField(
				ObjectFieldConstants.BUSINESS_TYPE_TEXT,
				_getLabelMap("subtitle"), true,
				CMSMigrationConstants.OBJECT_FIELD_NAME_SUBTITLE, false));
		objectFields.add(
			_createObjectField(
				ObjectFieldConstants.BUSINESS_TYPE_RICH_TEXT,
				_getLabelMap("content"), true,
				CMSMigrationConstants.OBJECT_FIELD_NAME_CONTENT, false));
		objectFields.add(
			_createAttachmentObjectField(
				ObjectFieldSettingConstants.
					VALUE_USER_COMPUTER_TO_CMS_BASIC_DOCUMENT,
				_getLabelMap("cover-image"),
				CMSMigrationConstants.OBJECT_FIELD_NAME_COVER_IMAGE));
		objectFields.add(
			_createObjectField(
				ObjectFieldConstants.BUSINESS_TYPE_LONG_TEXT,
				_getLabelMap("cover-image-caption"), true,
				CMSMigrationConstants.OBJECT_FIELD_NAME_COVER_IMAGE_CAPTION,
				false));

		ObjectDefinition objectDefinition = _getOrAddObjectDefinition(
			cmsMigrationContext,
			ObjectFolderConstants.EXTERNAL_REFERENCE_CODE_CONTENT_STRUCTURES,
			_getLabelMap("blog"), "CMSMigrationBlogsEntry",
			CMSMigrationConstants.
				OBJECT_DEFINITION_EXTERNAL_REFERENCE_CODE_BLOGS_ENTRY,
			objectFields);

		cmsMigrationContext.addObjectDefinition(
			BlogsEntry.class.getName(), 0, objectDefinition);

		return objectDefinition;
	}

	public ObjectDefinition getOrAddDDMStructureObjectDefinition(
			CMSMigrationContext cmsMigrationContext, DDMStructure ddmStructure)
		throws PortalException {

		ObjectDefinition objectDefinition =
			cmsMigrationContext.getObjectDefinition(
				JournalArticle.class.getName(), ddmStructure.getStructureId());

		if (objectDefinition != null) {
			return objectDefinition;
		}

		List<ObjectField> objectFields = new ArrayList<>(
			_getSystemObjectFields());

		DDMForm ddmForm = ddmStructure.getFullHierarchyDDMForm();

		List<DDMFormField> repeatableGroupDDMFormFields = new ArrayList<>();

		List<DDMFormField> ddmFormFields = _partitionDDMFormFields(
			cmsMigrationContext, ddmStructure, 0,
			CMSMigrationDDMFormFieldUtil.getLeafDDMFormFields(
				ddmForm.getDDMFormFields()),
			repeatableGroupDDMFormFields);

		String listTypeKey = String.valueOf(ddmStructure.getStructureId());

		objectFields.addAll(
			_getDDMFormObjectFields(
				cmsMigrationContext, ddmFormFields, listTypeKey));

		Map<Locale, String> nameMap = ddmStructure.getNameMap();

		LocalizedValuesUtil.putSiteDefaultValue(
			nameMap, ddmStructure.getName(LocaleUtil.getSiteDefault()));

		objectDefinition = _getOrAddObjectDefinition(
			cmsMigrationContext,
			ObjectFolderConstants.EXTERNAL_REFERENCE_CODE_CONTENT_STRUCTURES,
			nameMap, "CMSMigrationStructure" + ddmStructure.getStructureId(),
			CMSMigrationConstants.EXTERNAL_REFERENCE_CODE_PREFIX_DDM_STRUCTURE +
				ddmStructure.getStructureId(),
			objectFields);

		_addRepeatableGroups(
			cmsMigrationContext, ddmStructure, 0, listTypeKey, objectDefinition,
			repeatableGroupDDMFormFields);

		cmsMigrationContext.addDDMStructure(ddmStructure);
		cmsMigrationContext.addObjectDefinition(
			JournalArticle.class.getName(), ddmStructure.getStructureId(),
			objectDefinition);

		return objectDefinition;
	}

	public ObjectDefinition getOrAddDLFileEntryTypeObjectDefinition(
			CMSMigrationContext cmsMigrationContext,
			DLFileEntryType dlFileEntryType)
		throws PortalException {

		long fileEntryTypeId = 0;
		Map<Locale, String> nameMap = _getLabelMap("basic-document");

		if (dlFileEntryType != null) {
			fileEntryTypeId = dlFileEntryType.getFileEntryTypeId();

			nameMap = dlFileEntryType.getNameMap();

			LocalizedValuesUtil.putSiteDefaultValue(
				nameMap, dlFileEntryType.getName(LocaleUtil.getSiteDefault()));
		}

		ObjectDefinition objectDefinition =
			cmsMigrationContext.getObjectDefinition(
				DLFileEntry.class.getName(), fileEntryTypeId);

		if (objectDefinition != null) {
			return objectDefinition;
		}

		List<ObjectField> objectFields = new ArrayList<>(
			_getSystemObjectFields());

		objectFields.add(
			_createAttachmentObjectField(
				ObjectFieldSettingConstants.
					VALUE_USER_COMPUTER_TO_CMS_BASIC_DOCUMENT,
				_getLabelMap("file"),
				CMSMigrationConstants.OBJECT_FIELD_NAME_FILE));

		if (dlFileEntryType != null) {
			List<DDMFormField> ddmFormFields = new ArrayList<>();

			for (com.liferay.dynamic.data.mapping.kernel.DDMStructure
					kernelDDMStructure : dlFileEntryType.getDDMStructures()) {

				DDMStructure ddmStructure =
					_ddmStructureLocalService.getDDMStructure(
						kernelDDMStructure.getStructureId());

				DDMForm ddmForm = ddmStructure.getFullHierarchyDDMForm();

				ddmFormFields.addAll(ddmForm.getDDMFormFields());
			}

			objectFields.addAll(
				_getDDMFormObjectFields(
					cmsMigrationContext,
					CMSMigrationDDMFormFieldUtil.getLeafDDMFormFields(
						ddmFormFields),
					"DLFileEntryType" + fileEntryTypeId));
		}

		objectDefinition = _getOrAddObjectDefinition(
			cmsMigrationContext,
			ObjectFolderConstants.EXTERNAL_REFERENCE_CODE_FILE_TYPES, nameMap,
			"CMSMigrationFileEntryType" + fileEntryTypeId,
			CMSMigrationConstants.
				EXTERNAL_REFERENCE_CODE_PREFIX_DL_FILE_ENTRY_TYPE +
					fileEntryTypeId,
			objectFields);

		cmsMigrationContext.addObjectDefinition(
			DLFileEntry.class.getName(), fileEntryTypeId, objectDefinition);

		return objectDefinition;
	}

	private void _addMissingObjectFields(
			CMSMigrationContext cmsMigrationContext,
			ObjectDefinition objectDefinition, List<ObjectField> objectFields)
		throws PortalException {

		for (ObjectField objectField : objectFields) {
			ObjectField existingObjectField =
				_objectFieldLocalService.fetchObjectField(
					objectDefinition.getObjectDefinitionId(),
					objectField.getName());

			if (existingObjectField != null) {
				continue;
			}

			_objectFieldLocalService.addCustomObjectField(
				objectField.getExternalReferenceCode(),
				cmsMigrationContext.getUserId(),
				objectField.getListTypeDefinitionId(),
				objectDefinition.getObjectDefinitionId(),
				objectField.getBusinessType(), objectField.getDBType(),
				Collections.emptyMap(), objectField.isIndexed(),
				objectField.isIndexedAsKeyword(),
				objectField.getIndexedLanguageId(), objectField.getLabelMap(),
				objectField.isLocalized(), objectField.getName(),
				objectField.getReadOnly(),
				objectField.getReadOnlyConditionExpression(),
				objectField.isRequired(), objectField.isState(),
				objectField.getObjectFieldSettings());
		}
	}

	private void _addRepeatableGroups(
			CMSMigrationContext cmsMigrationContext, DDMStructure ddmStructure,
			int depth, String listTypeKey, ObjectDefinition objectDefinition,
			List<DDMFormField> repeatableGroupDDMFormFields)
		throws PortalException {

		for (DDMFormField ddmFormField : repeatableGroupDDMFormFields) {
			ObjectField objectField = _objectFieldLocalService.fetchObjectField(
				objectDefinition.getObjectDefinitionId(),
				CMSMigrationDDMFormFieldUtil.getObjectFieldName(ddmFormField));

			if (objectField != null) {
				cmsMigrationContext.getCMSMigrationReport(
				).addWarning(
					StringBundler.concat(
						"Migrated the repeatable field ",
						ddmFormField.getFieldReference(), " of the structure ",
						ddmStructure.getStructureKey(),
						" as a repeatable group and kept the JSON field ",
						objectField.getName(),
						" that a previous migration added")
				);
			}

			List<DDMFormField> nestedRepeatableGroupDDMFormFields =
				new ArrayList<>();

			List<DDMFormField> itemDDMFormFields = _partitionDDMFormFields(
				cmsMigrationContext, ddmStructure, depth + 1,
				CMSMigrationDDMFormFieldUtil.getLeafDDMFormFields(
					ListUtil.fromArray(
						CMSMigrationDDMFormFieldUtil.getItemDDMFormField(
							ddmFormField))),
				nestedRepeatableGroupDDMFormFields);

			ObjectDefinition repeatableGroupObjectDefinition =
				_getOrAddRepeatableGroupObjectDefinition(
					cmsMigrationContext, ddmFormField, itemDDMFormFields,
					listTypeKey, objectDefinition,
					ddmStructure.getStructureId());

			cmsMigrationContext.addRepeatableGroupObjectRelationship(
				ddmFormField,
				_getOrAddRepeatableGroupObjectRelationship(
					cmsMigrationContext, ddmFormField, objectDefinition,
					repeatableGroupObjectDefinition));

			_addRepeatableGroups(
				cmsMigrationContext, ddmStructure, depth + 1, listTypeKey,
				repeatableGroupObjectDefinition,
				nestedRepeatableGroupDDMFormFields);
		}
	}

	private ObjectField _createAttachmentObjectField(
		String fileSource, Map<Locale, String> labelMap, String name) {

		ObjectField objectField = _createObjectField(
			ObjectFieldConstants.BUSINESS_TYPE_ATTACHMENT, labelMap, false,
			name, false);

		List<ObjectFieldSetting> objectFieldSettings = new ArrayList<>();

		objectFieldSettings.add(
			_createObjectFieldSetting(
				ObjectFieldSettingConstants.NAME_ACCEPTED_FILE_EXTENSIONS,
				StringPool.STAR));
		objectFieldSettings.add(
			_createObjectFieldSetting(
				ObjectFieldSettingConstants.NAME_FILE_SOURCE, fileSource));
		objectFieldSettings.add(
			_createObjectFieldSetting(
				ObjectFieldSettingConstants.NAME_MAX_FILE_SIZE, "0"));

		if (fileSource.equals(
				ObjectFieldSettingConstants.
					VALUE_USER_COMPUTER_TO_CMS_BASIC_DOCUMENT)) {

			objectFieldSettings.add(
				_createObjectFieldSetting(
					ObjectFieldSettingConstants.NAME_SHOW_FILES_IN_LIBRARY,
					Boolean.FALSE.toString()));
		}

		objectField.setObjectFieldSettings(objectFieldSettings);

		return objectField;
	}

	private ObjectDefinitionSetting _createObjectDefinitionSetting(
		String name, String value) {

		ObjectDefinitionSetting objectDefinitionSetting =
			_objectDefinitionSettingLocalService.createObjectDefinitionSetting(
				0L);

		objectDefinitionSetting.setName(name);
		objectDefinitionSetting.setValue(value);

		return objectDefinitionSetting;
	}

	private ObjectField _createObjectField(
		String businessType, Map<Locale, String> labelMap, boolean localized,
		String name, boolean required) {

		String dbType = CMSMigrationDDMFormFieldUtil.getDBType(businessType);

		String indexedLanguageId = null;

		if (dbType.equals(ObjectFieldConstants.DB_TYPE_CLOB) ||
			dbType.equals(ObjectFieldConstants.DB_TYPE_STRING)) {

			indexedLanguageId = LocaleUtil.toLanguageId(
				LocaleUtil.getSiteDefault());
		}

		ObjectFieldBuilder objectFieldBuilder = new ObjectFieldBuilder();

		return objectFieldBuilder.businessType(
			businessType
		).dbType(
			dbType
		).externalReferenceCode(
			name
		).indexed(
			true
		).indexedAsKeyword(
			false
		).indexedLanguageId(
			indexedLanguageId
		).labelMap(
			labelMap
		).localized(
			localized
		).name(
			name
		).required(
			required
		).build();
	}

	private ObjectFieldSetting _createObjectFieldSetting(
		String name, String value) {

		ObjectFieldSetting objectFieldSetting =
			_objectFieldSettingLocalService.createObjectFieldSetting(0L);

		objectFieldSetting.setName(name);
		objectFieldSetting.setValue(value);

		return objectFieldSetting;
	}

	private Map<Locale, String> _getAltLabelMap(Map<Locale, String> labelMap) {
		Map<Locale, String> altLabelMap = new HashMap<>();

		for (Map.Entry<Locale, String> entry : labelMap.entrySet()) {
			altLabelMap.put(entry.getKey(), entry.getValue() + " (Alt)");
		}

		return altLabelMap;
	}

	private List<ObjectField> _getDDMFormObjectFields(
			CMSMigrationContext cmsMigrationContext,
			List<DDMFormField> ddmFormFields, String listTypeKey)
		throws PortalException {

		List<ObjectField> objectFields = new ArrayList<>();

		Set<String> names = new HashSet<>();

		for (DDMFormField ddmFormField : ddmFormFields) {
			String name = CMSMigrationDDMFormFieldUtil.getObjectFieldName(
				ddmFormField);

			if (!names.add(name)) {
				cmsMigrationContext.getCMSMigrationReport(
				).addWarning(
					StringBundler.concat(
						"Skipped duplicate field ", name, " in ", listTypeKey)
				);

				continue;
			}

			Map<Locale, String> labelMap = _getLabelMap(
				ddmFormField.getLabel(), name);

			String businessType = CMSMigrationDDMFormFieldUtil.getBusinessType(
				ddmFormField);

			if (businessType.equals(
					ObjectFieldConstants.BUSINESS_TYPE_ATTACHMENT)) {

				objectFields.add(
					_createAttachmentObjectField(
						ObjectFieldSettingConstants.VALUE_CMS_BASIC_DOCUMENT,
						labelMap, name));

				if (CMSMigrationDDMFormFieldUtil.isTypeImage(ddmFormField)) {
					objectFields.add(
						_createObjectField(
							ObjectFieldConstants.BUSINESS_TYPE_LONG_TEXT,
							_getAltLabelMap(labelMap), true,
							CMSMigrationDDMFormFieldUtil.getAltObjectFieldName(
								ddmFormField),
							false));
				}

				continue;
			}

			ObjectField objectField = _createObjectField(
				businessType, labelMap,
				CMSMigrationDDMFormFieldUtil.isLocalized(ddmFormField), name,
				false);

			if (CMSMigrationDDMFormFieldUtil.isOptionsField(ddmFormField)) {
				ListTypeDefinition listTypeDefinition =
					_getOrAddListTypeDefinition(
						cmsMigrationContext, ddmFormField, labelMap,
						listTypeKey);

				objectField.setListTypeDefinitionId(
					listTypeDefinition.getListTypeDefinitionId());
			}

			objectFields.add(objectField);
		}

		return objectFields;
	}

	private Map<Locale, String> _getLabelMap(
		LocalizedValue localizedValue, String defaultLabel) {

		Map<Locale, String> labelMap = new HashMap<>();

		if (localizedValue != null) {
			for (Map.Entry<Locale, String> entry :
					localizedValue.getValues(
					).entrySet()) {

				if (Validator.isNotNull(entry.getValue())) {
					labelMap.put(entry.getKey(), entry.getValue());
				}
			}
		}

		Locale locale = LocaleUtil.getSiteDefault();

		if (labelMap.containsKey(locale)) {
			return labelMap;
		}

		String label = null;

		if (localizedValue != null) {
			label = localizedValue.getString(locale);
		}

		if (Validator.isNull(label)) {
			label = defaultLabel;
		}

		labelMap.put(locale, label);

		return labelMap;
	}

	private Map<Locale, String> _getLabelMap(String key) {
		return HashMapBuilder.put(
			LocaleUtil.getSiteDefault(),
			_language.get(LocaleUtil.getSiteDefault(), key)
		).build();
	}

	private List<ObjectDefinitionSetting> _getObjectDefinitionSettings() {
		return ListUtil.fromArray(
			_createObjectDefinitionSetting(
				ObjectDefinitionSettingConstants.NAME_ACCEPT_ALL_GROUPS,
				Boolean.TRUE.toString()),
			_createObjectDefinitionSetting(
				ObjectDefinitionSettingConstants.NAME_DOMAIN, "space"));
	}

	private ListTypeDefinition _getOrAddListTypeDefinition(
			CMSMigrationContext cmsMigrationContext, DDMFormField ddmFormField,
			Map<Locale, String> labelMap, String listTypeKey)
		throws PortalException {

		String externalReferenceCode = StringBundler.concat(
			CMSMigrationConstants.EXTERNAL_REFERENCE_CODE_PREFIX_LIST_TYPE,
			listTypeKey, StringPool.UNDERLINE,
			CMSMigrationDDMFormFieldUtil.getObjectFieldName(ddmFormField));

		ListTypeDefinition listTypeDefinition =
			_listTypeDefinitionLocalService.
				fetchListTypeDefinitionByExternalReferenceCode(
					externalReferenceCode, cmsMigrationContext.getCompanyId());

		if (listTypeDefinition != null) {
			return listTypeDefinition;
		}

		List<ListTypeEntry> listTypeEntries = new ArrayList<>();

		DDMFormFieldOptions ddmFormFieldOptions =
			ddmFormField.getDDMFormFieldOptions();

		Set<String> keys = new HashSet<>();

		for (String optionValue : ddmFormFieldOptions.getOptionsValues()) {
			String key = CMSMigrationDDMFormFieldUtil.getListTypeEntryKey(
				optionValue);

			if (!keys.add(key)) {
				cmsMigrationContext.getCMSMigrationReport(
				).addWarning(
					StringBundler.concat(
						"Skipped duplicate option ", optionValue, " in ",
						externalReferenceCode)
				);

				continue;
			}

			listTypeEntries.add(
				ListTypeEntryUtil.createListTypeEntry(
					key,
					_getLabelMap(
						ddmFormFieldOptions.getOptionLabels(optionValue),
						optionValue)));
		}

		return _listTypeDefinitionLocalService.addListTypeDefinition(
			externalReferenceCode, cmsMigrationContext.getUserId(), labelMap,
			false, listTypeEntries, cmsMigrationContext.getServiceContext());
	}

	private ObjectDefinition _getOrAddObjectDefinition(
			CMSMigrationContext cmsMigrationContext,
			String objectFolderExternalReferenceCode,
			Map<Locale, String> labelMap, String name,
			String externalReferenceCode, List<ObjectField> objectFields)
		throws PortalException {

		ObjectDefinition objectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					externalReferenceCode, cmsMigrationContext.getCompanyId());

		if (objectDefinition != null) {
			_addMissingObjectFields(
				cmsMigrationContext, objectDefinition, objectFields);

			return objectDefinition;
		}

		ObjectFolder objectFolder =
			_objectFolderLocalService.getObjectFolderByExternalReferenceCode(
				objectFolderExternalReferenceCode,
				cmsMigrationContext.getCompanyId());

		objectDefinition =
			_objectDefinitionLocalService.addCustomObjectDefinition(
				externalReferenceCode, cmsMigrationContext.getUserId(),
				objectFolder.getObjectFolderId(), null, Collections.emptyMap(),
				true, true, false, true, true, true, true, false, true, null,
				labelMap, name, null, null, labelMap, false,
				ObjectDefinitionConstants.SCOPE_DEPOT,
				ObjectDefinitionConstants.STORAGE_TYPE_DEFAULT,
				_getObjectDefinitionSettings(), objectFields,
				Collections.emptyList(),
				cmsMigrationContext.getServiceContext());

		ObjectField titleObjectField =
			_objectFieldLocalService.fetchObjectField(
				objectDefinition.getObjectDefinitionId(),
				CMSMigrationConstants.OBJECT_FIELD_NAME_TITLE);

		_objectDefinitionLocalService.updateTitleObjectFieldId(
			objectDefinition.getObjectDefinitionId(),
			titleObjectField.getObjectFieldId());

		objectDefinition =
			_objectDefinitionLocalService.publishCustomObjectDefinition(
				cmsMigrationContext.getUserId(),
				objectDefinition.getObjectDefinitionId());

		cmsMigrationContext.getCMSMigrationReport(
		).increment(
			"object-definitions"
		);

		return objectDefinition;
	}

	private ObjectDefinition _getOrAddRepeatableGroupObjectDefinition(
			CMSMigrationContext cmsMigrationContext, DDMFormField ddmFormField,
			List<DDMFormField> itemDDMFormFields, String listTypeKey,
			ObjectDefinition objectDefinition, long structureId)
		throws PortalException {

		String externalReferenceCode =
			CMSMigrationDDMFormFieldUtil.
				getRepeatableGroupExternalReferenceCode(
					ddmFormField, objectDefinition.getExternalReferenceCode());

		ObjectDefinition repeatableGroupObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					externalReferenceCode, cmsMigrationContext.getCompanyId());

		List<ObjectField> objectFields = _getDDMFormObjectFields(
			cmsMigrationContext, itemDDMFormFields, listTypeKey);

		if (repeatableGroupObjectDefinition == null) {
			ObjectFolder objectFolder =
				_objectFolderLocalService.
					getObjectFolderByExternalReferenceCode(
						ObjectFolderConstants.
							EXTERNAL_REFERENCE_CODE_STRUCTURE_REPEATABLE_GROUPS,
						cmsMigrationContext.getCompanyId());

			Map<Locale, String> labelMap = _getLabelMap(
				ddmFormField.getLabel(),
				CMSMigrationDDMFormFieldUtil.getObjectFieldName(ddmFormField));

			repeatableGroupObjectDefinition =
				_objectDefinitionLocalService.addCustomObjectDefinition(
					externalReferenceCode, cmsMigrationContext.getUserId(),
					objectFolder.getObjectFolderId(), null,
					Collections.emptyMap(), true, true, false, true, false,
					true, true, false, true, null, labelMap,
					_getRepeatableGroupObjectDefinitionName(
						cmsMigrationContext, ddmFormField, structureId),
					null, null, labelMap, false,
					ObjectDefinitionConstants.SCOPE_DEPOT,
					ObjectDefinitionConstants.STORAGE_TYPE_DEFAULT,
					ListUtil.fromArray(
						_createObjectDefinitionSetting(
							ObjectDefinitionSettingConstants.
								NAME_ACCEPT_ALL_GROUPS,
							Boolean.TRUE.toString())),
					objectFields, Collections.emptyList(),
					cmsMigrationContext.getServiceContext());

			repeatableGroupObjectDefinition =
				_objectDefinitionLocalService.publishCustomObjectDefinition(
					cmsMigrationContext.getUserId(),
					repeatableGroupObjectDefinition.getObjectDefinitionId());

			cmsMigrationContext.getCMSMigrationReport(
			).increment(
				"object-definitions"
			);
		}
		else {
			_addMissingObjectFields(
				cmsMigrationContext, repeatableGroupObjectDefinition,
				objectFields);
		}

		return repeatableGroupObjectDefinition;
	}

	private ObjectRelationship _getOrAddRepeatableGroupObjectRelationship(
			CMSMigrationContext cmsMigrationContext, DDMFormField ddmFormField,
			ObjectDefinition objectDefinition,
			ObjectDefinition repeatableGroupObjectDefinition)
		throws PortalException {

		String externalReferenceCode =
			CMSMigrationDDMFormFieldUtil.
				getRepeatableGroupExternalReferenceCode(
					ddmFormField, objectDefinition.getExternalReferenceCode());

		ObjectRelationship objectRelationship =
			_objectRelationshipLocalService.
				fetchObjectRelationshipByExternalReferenceCode(
					externalReferenceCode, cmsMigrationContext.getCompanyId(),
					objectDefinition.getObjectDefinitionId());

		if (objectRelationship != null) {
			return objectRelationship;
		}

		objectRelationship =
			_objectRelationshipLocalService.addObjectRelationship(
				externalReferenceCode, cmsMigrationContext.getUserId(),
				objectDefinition.getObjectDefinitionId(),
				repeatableGroupObjectDefinition.getObjectDefinitionId(), 0,
				ObjectRelationshipConstants.DELETION_TYPE_CASCADE,
				Collections.emptyMap(), true,
				repeatableGroupObjectDefinition.getLabelMap(),
				_getRepeatableGroupObjectRelationshipName(
					ddmFormField, objectDefinition,
					repeatableGroupObjectDefinition),
				false, ObjectRelationshipConstants.TYPE_ONE_TO_MANY, null);

		ObjectDefinitionSetting objectDefinitionSetting =
			_objectDefinitionSettingLocalService.fetchObjectDefinitionSetting(
				repeatableGroupObjectDefinition.getObjectDefinitionId(),
				ObjectDefinitionSettingConstants.
					NAME_ALLOW_STANDALONE_OBJECT_ENTRY);

		if (objectDefinitionSetting == null) {
			_objectDefinitionSettingLocalService.addObjectDefinitionSetting(
				cmsMigrationContext.getUserId(),
				repeatableGroupObjectDefinition.getObjectDefinitionId(),
				ObjectDefinitionSettingConstants.
					NAME_ALLOW_STANDALONE_OBJECT_ENTRY,
				Boolean.FALSE.toString());
		}

		return objectRelationship;
	}

	private String _getRepeatableGroupObjectDefinitionName(
		CMSMigrationContext cmsMigrationContext, DDMFormField ddmFormField,
		long structureId) {

		String objectFieldName = StringUtil.upperCaseFirstLetter(
			CMSMigrationDDMFormFieldUtil.getObjectFieldName(ddmFormField));

		return _getUniqueName(
			name -> {
				ObjectDefinition objectDefinition =
					_objectDefinitionLocalService.fetchObjectDefinition(
						cmsMigrationContext.getCompanyId(), "C_" + name);

				return objectDefinition != null;
			},
			_NAME_MAX_LENGTH,
			"CMSMigrationGroup" + structureId + objectFieldName);
	}

	private String _getRepeatableGroupObjectRelationshipName(
		DDMFormField ddmFormField, ObjectDefinition objectDefinition,
		ObjectDefinition repeatableGroupObjectDefinition) {

		String pkObjectFieldName = objectDefinition.getPKObjectFieldName();

		String objectFieldName = StringUtil.upperCaseFirstLetter(
			CMSMigrationDDMFormFieldUtil.getObjectFieldName(ddmFormField));

		return _getUniqueName(
			name -> {
				for (ObjectDefinition curObjectDefinition :
						ListUtil.fromArray(
							objectDefinition,
							repeatableGroupObjectDefinition)) {

					long objectDefinitionId =
						curObjectDefinition.getObjectDefinitionId();

					ObjectField objectField =
						_objectFieldLocalService.fetchObjectField(
							objectDefinitionId, name);
					ObjectRelationship objectRelationship =
						_objectRelationshipLocalService.
							fetchObjectRelationshipByObjectDefinitionId(
								objectDefinitionId, name);

					if ((objectField != null) || (objectRelationship != null)) {
						return true;
					}
				}

				return false;
			},
			Math.min(
				_NAME_MAX_LENGTH,
				ObjectFieldConstants.DB_COLUMN_NAME_MAX_LENGTH -
					pkObjectFieldName.length() - 3),
			"group" + objectFieldName);
	}

	private List<ObjectField> _getSystemObjectFields() {
		return ListUtil.fromArray(
			_createObjectField(
				ObjectFieldConstants.BUSINESS_TYPE_TEXT, _getLabelMap("title"),
				true, CMSMigrationConstants.OBJECT_FIELD_NAME_TITLE, true),
			_createObjectField(
				ObjectFieldConstants.BUSINESS_TYPE_LONG_TEXT,
				_getLabelMap("description"), true,
				CMSMigrationConstants.OBJECT_FIELD_NAME_DESCRIPTION, false),
			_createAttachmentObjectField(
				ObjectFieldSettingConstants.
					VALUE_USER_COMPUTER_TO_CMS_BASIC_DOCUMENT,
				_getLabelMap("small-image"),
				CMSMigrationConstants.OBJECT_FIELD_NAME_SMALL_IMAGE),
			_createObjectField(
				ObjectFieldConstants.BUSINESS_TYPE_TEXT,
				_getLabelMap("small-image-url"), false,
				CMSMigrationConstants.OBJECT_FIELD_NAME_SMALL_IMAGE_URL,
				false));
	}

	private String _getUniqueName(
		Predicate<String> existsPredicate, int maxLength, String name) {

		if (name.length() > maxLength) {
			name = name.substring(0, maxLength);
		}

		String uniqueName = name;

		for (int i = 1; existsPredicate.test(uniqueName); i++) {
			String suffix = String.valueOf(i);

			String prefix = name.substring(
				0, Math.min(name.length(), maxLength - suffix.length()));

			uniqueName = prefix + suffix;
		}

		return uniqueName;
	}

	private List<DDMFormField> _partitionDDMFormFields(
		CMSMigrationContext cmsMigrationContext, DDMStructure ddmStructure,
		int depth, List<DDMFormField> leafDDMFormFields,
		List<DDMFormField> repeatableGroupDDMFormFields) {

		List<DDMFormField> ddmFormFields = new ArrayList<>();

		for (DDMFormField ddmFormField : leafDDMFormFields) {
			if (CMSMigrationDDMFormFieldUtil.isRepeatableGroupField(
					ddmFormField, depth,
					cmsMigrationContext.isMigrateRepeatableFields())) {

				repeatableGroupDDMFormFields.add(ddmFormField);

				continue;
			}

			if (cmsMigrationContext.isMigrateRepeatableFields() &&
				ddmFormField.isRepeatable()) {

				String reason = " because it contains unsupported field types";

				if (depth >= TreeConstants.MAX_HEIGHT) {
					reason = StringBundler.concat(
						" because repeatable groups cannot be nested ",
						"more than ", TreeConstants.MAX_HEIGHT, " levels deep");
				}

				cmsMigrationContext.getCMSMigrationReport(
				).addWarning(
					StringBundler.concat(
						"Migrated the repeatable field ",
						ddmFormField.getFieldReference(), " of the structure ",
						ddmStructure.getStructureKey(), " as JSON", reason)
				);
			}

			ddmFormFields.add(ddmFormField);
		}

		return ddmFormFields;
	}

	private static final int _NAME_MAX_LENGTH = 41;

	private final DDMStructureLocalService _ddmStructureLocalService;
	private final Language _language;
	private final ListTypeDefinitionLocalService
		_listTypeDefinitionLocalService;
	private final ObjectDefinitionLocalService _objectDefinitionLocalService;
	private final ObjectDefinitionSettingLocalService
		_objectDefinitionSettingLocalService;
	private final ObjectFieldLocalService _objectFieldLocalService;
	private final ObjectFieldSettingLocalService
		_objectFieldSettingLocalService;
	private final ObjectFolderLocalService _objectFolderLocalService;
	private final ObjectRelationshipLocalService
		_objectRelationshipLocalService;

}