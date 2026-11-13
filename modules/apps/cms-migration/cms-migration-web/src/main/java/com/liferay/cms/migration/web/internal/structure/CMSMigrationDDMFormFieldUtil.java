/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.structure;

import com.liferay.cms.migration.web.internal.constants.CMSMigrationConstants;
import com.liferay.dynamic.data.mapping.form.field.type.constants.DDMFormFieldTypeConstants;
import com.liferay.dynamic.data.mapping.model.DDMFormField;
import com.liferay.object.constants.ObjectFieldConstants;
import com.liferay.object.tree.constants.TreeConstants;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.SetUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Maps Dynamic Data Mapping form fields to object fields. Every name and type
 * decision is deterministic, so the migration, the template context helper,
 * and the reference rewriters always agree without storing a mapping table.
 *
 * @author Lino Alves
 */
public class CMSMigrationDDMFormFieldUtil {

	public static String escapeJSONFieldValue(String json) {
		return StringUtil.replace(
			json, new char[] {'&', '<', '>'},
			new String[] {"&amp;", "&lt;", "&gt;"});
	}

	public static String getAltObjectFieldName(DDMFormField ddmFormField) {
		return getObjectFieldName(ddmFormField) + "Alt";
	}

	public static String getBusinessType(DDMFormField ddmFormField) {
		if (isJSONField(ddmFormField)) {
			return ObjectFieldConstants.BUSINESS_TYPE_RICH_TEXT;
		}

		String type = ddmFormField.getType();

		if (type.equals(DDMFormFieldTypeConstants.CHECKBOX)) {
			return ObjectFieldConstants.BUSINESS_TYPE_BOOLEAN;
		}
		else if (type.equals(DDMFormFieldTypeConstants.CHECKBOX_MULTIPLE) ||
				 (type.equals(DDMFormFieldTypeConstants.SELECT) &&
				  ddmFormField.isMultiple())) {

			return ObjectFieldConstants.BUSINESS_TYPE_MULTISELECT_PICKLIST;
		}
		else if (type.equals(DDMFormFieldTypeConstants.DATE)) {
			return ObjectFieldConstants.BUSINESS_TYPE_DATE;
		}
		else if (type.equals(DDMFormFieldTypeConstants.DATE_TIME)) {
			return ObjectFieldConstants.BUSINESS_TYPE_DATE_TIME;
		}
		else if (type.equals(DDMFormFieldTypeConstants.DDM_IMAGE) ||
				 type.equals(DDMFormFieldTypeConstants.DOCUMENT_LIBRARY) ||
				 type.equals(DDMFormFieldTypeConstants.IMAGE)) {

			return ObjectFieldConstants.BUSINESS_TYPE_ATTACHMENT;
		}
		else if (type.equals(DDMFormFieldTypeConstants.NUMERIC)) {
			if (Objects.equals(ddmFormField.getDataType(), "integer")) {
				return ObjectFieldConstants.BUSINESS_TYPE_LONG_INTEGER;
			}

			return ObjectFieldConstants.BUSINESS_TYPE_DECIMAL;
		}
		else if (type.equals(DDMFormFieldTypeConstants.RADIO) ||
				 type.equals(DDMFormFieldTypeConstants.SELECT)) {

			return ObjectFieldConstants.BUSINESS_TYPE_PICKLIST;
		}
		else if (type.equals(DDMFormFieldTypeConstants.RICH_TEXT)) {
			return ObjectFieldConstants.BUSINESS_TYPE_RICH_TEXT;
		}

		return ObjectFieldConstants.BUSINESS_TYPE_LONG_TEXT;
	}

	public static String getDBType(String businessType) {
		if (businessType.equals(
				ObjectFieldConstants.BUSINESS_TYPE_ATTACHMENT) ||
			businessType.equals(
				ObjectFieldConstants.BUSINESS_TYPE_LONG_INTEGER)) {

			return ObjectFieldConstants.DB_TYPE_LONG;
		}
		else if (businessType.equals(
					ObjectFieldConstants.BUSINESS_TYPE_BOOLEAN)) {

			return ObjectFieldConstants.DB_TYPE_BOOLEAN;
		}
		else if (businessType.equals(ObjectFieldConstants.BUSINESS_TYPE_DATE)) {
			return ObjectFieldConstants.DB_TYPE_DATE;
		}
		else if (businessType.equals(
					ObjectFieldConstants.BUSINESS_TYPE_DATE_TIME)) {

			return ObjectFieldConstants.DB_TYPE_DATE_TIME;
		}
		else if (businessType.equals(
					ObjectFieldConstants.BUSINESS_TYPE_DECIMAL)) {

			return ObjectFieldConstants.DB_TYPE_DOUBLE;
		}
		else if (businessType.equals(
					ObjectFieldConstants.BUSINESS_TYPE_LONG_TEXT) ||
				 businessType.equals(
					 ObjectFieldConstants.BUSINESS_TYPE_RICH_TEXT)) {

			return ObjectFieldConstants.DB_TYPE_CLOB;
		}

		return ObjectFieldConstants.DB_TYPE_STRING;
	}

	public static DDMFormField getItemDDMFormField(DDMFormField ddmFormField) {
		DDMFormField itemDDMFormField = new DDMFormField(ddmFormField);

		itemDDMFormField.setRepeatable(false);

		return itemDDMFormField;
	}

	/**
	 * Returns the fields that become object fields. A repeatable field is
	 * stored whole, nested fields included, as JSON in one object field. A
	 * nonrepeatable field set contributes its nested fields instead of itself.
	 */
	public static List<DDMFormField> getLeafDDMFormFields(
		List<DDMFormField> ddmFormFields) {

		List<DDMFormField> leafDDMFormFields = new ArrayList<>();

		for (DDMFormField ddmFormField : ddmFormFields) {
			if (isIgnoredField(ddmFormField)) {
				continue;
			}

			if (isJSONField(ddmFormField)) {
				leafDDMFormFields.add(ddmFormField);

				continue;
			}

			if (!Objects.equals(
					ddmFormField.getType(),
					DDMFormFieldTypeConstants.FIELDSET)) {

				leafDDMFormFields.add(ddmFormField);
			}

			leafDDMFormFields.addAll(
				getLeafDDMFormFields(ddmFormField.getNestedDDMFormFields()));
		}

		return leafDDMFormFields;
	}

	public static String getListTypeEntryKey(String optionValue) {
		String key = _toAlphanumeric(optionValue);

		if (Validator.isNull(key)) {
			return "option";
		}

		return key;
	}

	public static String getObjectFieldName(DDMFormField ddmFormField) {
		String fieldReference = ddmFormField.getFieldReference();

		if (Validator.isNull(fieldReference)) {
			fieldReference = ddmFormField.getName();
		}

		return getObjectFieldName(fieldReference);
	}

	public static String getObjectFieldName(String fieldReference) {
		String name = _toAlphanumeric(fieldReference);

		if (Validator.isNull(name) || Character.isDigit(name.charAt(0))) {
			name = "field" + name;
		}

		name = StringUtil.lowerCaseFirstLetter(name);

		if (_reservedNames.contains(StringUtil.toLowerCase(name))) {
			name = "field" + StringUtil.upperCaseFirstLetter(name);
		}

		if (name.length() > 41) {
			name = name.substring(0, 41);
		}

		return name;
	}

	public static String getRepeatableGroupExternalReferenceCode(
		DDMFormField ddmFormField,
		String objectDefinitionExternalReferenceCode) {

		return StringBundler.concat(
			objectDefinitionExternalReferenceCode, StringPool.UNDERLINE,
			getObjectFieldName(ddmFormField));
	}

	public static String getRepeatableGroupItemExternalReferenceCode(
		DDMFormField ddmFormField, int index,
		String objectEntryExternalReferenceCode) {

		return StringBundler.concat(
			objectEntryExternalReferenceCode, StringPool.UNDERLINE,
			getObjectFieldName(ddmFormField), StringPool.UNDERLINE, index);
	}

	public static boolean isAttachmentField(DDMFormField ddmFormField) {
		return Objects.equals(
			getBusinessType(ddmFormField),
			ObjectFieldConstants.BUSINESS_TYPE_ATTACHMENT);
	}

	public static boolean isIgnoredField(DDMFormField ddmFormField) {
		if (ddmFormField.isTransient() &&
			!Objects.equals(
				ddmFormField.getType(), DDMFormFieldTypeConstants.FIELDSET)) {

			return true;
		}

		return ArrayUtil.contains(_IGNORED_TYPES, ddmFormField.getType());
	}

	public static boolean isJSONField(DDMFormField ddmFormField) {
		return ddmFormField.isRepeatable();
	}

	public static boolean isLocalized(DDMFormField ddmFormField) {
		if (!ddmFormField.isLocalizable() || isJSONField(ddmFormField)) {
			return false;
		}

		String businessType = getBusinessType(ddmFormField);

		if (businessType.equals(ObjectFieldConstants.BUSINESS_TYPE_LONG_TEXT) ||
			businessType.equals(ObjectFieldConstants.BUSINESS_TYPE_RICH_TEXT)) {

			return true;
		}

		return false;
	}

	public static boolean isNested(DDMFormField ddmFormField) {
		return ListUtil.isNotEmpty(ddmFormField.getNestedDDMFormFields());
	}

	public static boolean isOptionsField(DDMFormField ddmFormField) {
		String businessType = getBusinessType(ddmFormField);

		if (businessType.equals(
				ObjectFieldConstants.BUSINESS_TYPE_MULTISELECT_PICKLIST) ||
			businessType.equals(ObjectFieldConstants.BUSINESS_TYPE_PICKLIST)) {

			return true;
		}

		return false;
	}

	public static boolean isRepeatableGroupField(
		DDMFormField ddmFormField, int depth, boolean migrateRepeatableFields) {

		if (!migrateRepeatableFields || !ddmFormField.isRepeatable() ||
			(depth >= TreeConstants.MAX_HEIGHT)) {

			return false;
		}

		return _isRepeatableGroupFieldSupported(ddmFormField);
	}

	public static boolean isTypeImage(DDMFormField ddmFormField) {
		String type = ddmFormField.getType();

		if (type.equals(DDMFormFieldTypeConstants.DDM_IMAGE) ||
			type.equals(DDMFormFieldTypeConstants.IMAGE)) {

			return true;
		}

		return false;
	}

	public static String unescapeJSONFieldValue(String value) {
		return StringUtil.replace(
			value, StringPool.AMPERSAND, StringPool.SEMICOLON, _unescapeMap);
	}

	private static boolean _isRepeatableGroupFieldSupported(
		DDMFormField ddmFormField) {

		if (!Objects.equals(
				ddmFormField.getType(), DDMFormFieldTypeConstants.FIELDSET)) {

			return _isRepeatableGroupItemFieldSupported(ddmFormField);
		}

		boolean leafFieldFound = false;

		for (DDMFormField nestedDDMFormField :
				ddmFormField.getNestedDDMFormFields()) {

			if (isIgnoredField(nestedDDMFormField)) {
				continue;
			}

			if (nestedDDMFormField.isRepeatable()) {
				leafFieldFound = true;

				continue;
			}

			if (Objects.equals(
					nestedDDMFormField.getType(),
					DDMFormFieldTypeConstants.FIELDSET)) {

				if (!_isRepeatableGroupFieldSupported(nestedDDMFormField)) {
					return false;
				}
			}
			else if (!_isRepeatableGroupItemFieldSupported(
						nestedDDMFormField)) {

				return false;
			}

			leafFieldFound = true;
		}

		return leafFieldFound;
	}

	private static boolean _isRepeatableGroupItemFieldSupported(
		DDMFormField ddmFormField) {

		String type = ddmFormField.getType();

		if ((type.equals(DDMFormFieldTypeConstants.RADIO) ||
			 type.equals(DDMFormFieldTypeConstants.SELECT)) &&
			!ddmFormField.isMultiple()) {

			return true;
		}

		return ArrayUtil.contains(
			new String[] {
				DDMFormFieldTypeConstants.CHECKBOX,
				DDMFormFieldTypeConstants.DATE,
				DDMFormFieldTypeConstants.DATE_TIME,
				DDMFormFieldTypeConstants.DDM_IMAGE,
				DDMFormFieldTypeConstants.DOCUMENT_LIBRARY,
				DDMFormFieldTypeConstants.IMAGE,
				DDMFormFieldTypeConstants.NUMERIC,
				DDMFormFieldTypeConstants.RICH_TEXT,
				DDMFormFieldTypeConstants.TEXT
			},
			type);
	}

	private static String _toAlphanumeric(String s) {
		if (s == null) {
			return StringPool.BLANK;
		}

		StringBuilder sb = new StringBuilder(s.length());

		for (char c : s.toCharArray()) {
			if (Validator.isChar(c) || Validator.isDigit(c)) {
				sb.append(c);
			}
		}

		return sb.toString();
	}

	private static final String[] _IGNORED_TYPES = {
		DDMFormFieldTypeConstants.PARAGRAPH, DDMFormFieldTypeConstants.SEPARATOR
	};

	private static final Set<String> _reservedNames = SetUtil.fromArray(
		"actions", "companyid", "createdate", "creator", "currentdate",
		"datecreated", "datemodified", "displaydate", "expirationdate",
		"externalreferencecode", "groupid", "id", "keywords", "lastpublishdate",
		"modifieddate", "reviewdate", "status", "statusbyuserid",
		"statusbyusername", "statusdate", "taxonomycategoryids", "userid",
		"username", CMSMigrationConstants.OBJECT_FIELD_NAME_DESCRIPTION,
		StringUtil.toLowerCase(
			CMSMigrationConstants.OBJECT_FIELD_NAME_SMALL_IMAGE),
		StringUtil.toLowerCase(
			CMSMigrationConstants.OBJECT_FIELD_NAME_SMALL_IMAGE_URL),
		CMSMigrationConstants.OBJECT_FIELD_NAME_TITLE);
	private static final Map<String, String> _unescapeMap = HashMapBuilder.put(
		"#39", StringPool.APOSTROPHE
	).put(
		"amp", StringPool.AMPERSAND
	).put(
		"apos", StringPool.APOSTROPHE
	).put(
		"gt", StringPool.GREATER_THAN
	).put(
		"lt", StringPool.LESS_THAN
	).put(
		"quot", StringPool.QUOTE
	).build();

}