/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.template;

import com.liferay.asset.display.page.portlet.AssetDisplayPageFriendlyURLProvider;
import com.liferay.asset.kernel.model.AssetEntry;
import com.liferay.asset.kernel.service.AssetTagLocalService;
import com.liferay.cms.migration.web.internal.constants.CMSMigrationConstants;
import com.liferay.cms.migration.web.internal.structure.CMSMigrationDDMFormFieldUtil;
import com.liferay.document.library.kernel.service.DLAppLocalService;
import com.liferay.document.library.util.DLURLHelper;
import com.liferay.dynamic.data.mapping.form.field.type.constants.DDMFormFieldTypeConstants;
import com.liferay.dynamic.data.mapping.model.DDMForm;
import com.liferay.dynamic.data.mapping.model.DDMFormField;
import com.liferay.dynamic.data.mapping.model.DDMFormFieldOptions;
import com.liferay.dynamic.data.mapping.model.LocalizedValue;
import com.liferay.friendly.url.model.FriendlyURLEntry;
import com.liferay.friendly.url.service.FriendlyURLEntryLocalService;
import com.liferay.info.item.InfoItemReference;
import com.liferay.journal.util.JournalContent;
import com.liferay.object.constants.ObjectFieldConstants;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.object.service.ObjectEntryService;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.json.JSONUtil;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.service.ClassNameLocalService;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.template.TemplateConstants;
import com.liferay.portal.kernel.templateparser.TemplateNode;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.HtmlUtil;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.PropsUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Time;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.reading.time.message.ReadingTimeMessageProvider;
import com.liferay.reading.time.model.ReadingTimeEntry;
import com.liferay.reading.time.service.ReadingTimeEntryLocalService;

import jakarta.servlet.http.HttpServletRequest;

import java.io.Serializable;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Builds the variables that a Web Content template expects, from a migrated
 * object entry. Migrated information templates call {@link
 * #getContext(String, String)} before running the original template code, so
 * the same FreeMarker code renders the same HTML for the object entry as it
 * did for the web content article.
 *
 * @author Lino Alves
 */
public class CMSMigrationTemplateContextHelper {

	public CMSMigrationTemplateContextHelper(
		AssetDisplayPageFriendlyURLProvider assetDisplayPageFriendlyURLProvider,
		AssetTagLocalService assetTagLocalService,
		ClassNameLocalService classNameLocalService,
		DLAppLocalService dlAppLocalService, DLURLHelper dlURLHelper,
		Function<String, DDMForm> ddmFormFunction,
		FriendlyURLEntryLocalService friendlyURLEntryLocalService,
		GroupLocalService groupLocalService,
		HttpServletRequest httpServletRequest, JournalContent journalContent,
		Language language,
		ObjectDefinitionLocalService objectDefinitionLocalService,
		ObjectEntryLocalService objectEntryLocalService,
		ObjectEntryService objectEntryService,
		ReadingTimeEntryLocalService readingTimeEntryLocalService,
		ReadingTimeMessageProvider readingTimeMessageProvider,
		UserLocalService userLocalService) {

		_assetDisplayPageFriendlyURLProvider =
			assetDisplayPageFriendlyURLProvider;
		_assetTagLocalService = assetTagLocalService;
		_classNameLocalService = classNameLocalService;
		_dlAppLocalService = dlAppLocalService;
		_dlURLHelper = dlURLHelper;
		_ddmFormFunction = ddmFormFunction;
		_friendlyURLEntryLocalService = friendlyURLEntryLocalService;
		_groupLocalService = groupLocalService;
		_httpServletRequest = httpServletRequest;
		_journalContent = journalContent;
		_language = language;
		_objectDefinitionLocalService = objectDefinitionLocalService;
		_objectEntryLocalService = objectEntryLocalService;
		_objectEntryService = objectEntryService;
		_readingTimeEntryLocalService = readingTimeEntryLocalService;
		_readingTimeMessageProvider = readingTimeMessageProvider;
		_userLocalService = userLocalService;
	}

	/**
	 * Returns the values that the generated Blogs widget template displays
	 * for a migrated blog entry.
	 */
	public Map<String, String> getBlogsEntryData(AssetEntry assetEntry)
		throws Exception {

		ObjectEntry objectEntry = _objectEntryService.getObjectEntry(
			assetEntry.getClassPK());

		ThemeDisplay themeDisplay =
			(ThemeDisplay)_httpServletRequest.getAttribute(
				WebKeys.THEME_DISPLAY);

		String languageId = themeDisplay.getLanguageId();

		Map<String, Serializable> values = objectEntry.getValues();

		String summary = GetterUtil.getString(
			_getLocalizedValue(
				languageId, CMSMigrationConstants.OBJECT_FIELD_NAME_DESCRIPTION,
				values));

		if (Validator.isNull(summary)) {
			summary = GetterUtil.getString(
				_getLocalizedValue(
					languageId, CMSMigrationConstants.OBJECT_FIELD_NAME_CONTENT,
					values));
		}

		summary = StringUtil.shorten(
			StringUtil.removeSubstring(
				HtmlUtil.stripHtml(summary),
				StringPool.NEW_LINE + StringPool.SPACE),
			GetterUtil.getInteger(
				PropsUtil.get(PropsKeys.BLOGS_PAGE_ABSTRACT_LENGTH), 400));

		Date date = objectEntry.getDisplayDate();

		if (date == null) {
			date = objectEntry.getStatusDate();
		}

		String publishedAgo = StringPool.BLANK;

		if (date != null) {
			publishedAgo = _language.format(
				themeDisplay.getLocale(), "x-ago",
				_language.getTimeDescription(
					themeDisplay.getLocale(),
					System.currentTimeMillis() - date.getTime(), true),
				false);
		}

		User user = _userLocalService.fetchUser(objectEntry.getUserId());

		String userPortraitURL = StringPool.BLANK;

		if (user != null) {
			userPortraitURL = user.getPortraitURL(themeDisplay);
		}

		return HashMapBuilder.put(
			"coverImageCaption",
			GetterUtil.getString(
				_getLocalizedValue(
					languageId,
					CMSMigrationConstants.OBJECT_FIELD_NAME_COVER_IMAGE_CAPTION,
					values))
		).put(
			"coverImageURL",
			_getFileEntryURL(
				GetterUtil.getLong(
					values.get(
						CMSMigrationConstants.OBJECT_FIELD_NAME_COVER_IMAGE)))
		).put(
			"publishedAgo", publishedAgo
		).put(
			"smallImageURL", _getSmallImageURL(values)
		).put(
			"summary", summary
		).put(
			"title", objectEntry.getTitleValue(languageId)
		).put(
			"userName", objectEntry.getUserName()
		).put(
			"userPortraitURL", userPortraitURL
		).put(
			"viewURL",
			GetterUtil.getString(
				_assetDisplayPageFriendlyURLProvider.getFriendlyURL(
					new InfoItemReference(
						assetEntry.getClassName(), assetEntry.getClassPK()),
					themeDisplay))
		).build();
	}

	/**
	 * Returns the Web Content template variables for an object entry.
	 *
	 * @param objectEntryId the primary key of the migrated object entry
	 * @param settings the Base64 encoded JSON that the migration embedded in
	 *        the template, with the structure definition and the identity of
	 *        the original template
	 */
	public Map<String, Object> getContext(String objectEntryId, String settings)
		throws Exception {

		Map<String, Object> context = new HashMap<>();

		ObjectEntry objectEntry = _objectEntryService.getObjectEntry(
			GetterUtil.getLong(objectEntryId));

		JSONObject settingsJSONObject = JSONFactoryUtil.createJSONObject(
			new String(_decoder.decode(settings), StringPool.UTF8));

		DDMForm ddmForm = _ddmFormFunction.apply(
			settingsJSONObject.getString("ddmForm"));

		ThemeDisplay themeDisplay =
			(ThemeDisplay)_httpServletRequest.getAttribute(
				WebKeys.THEME_DISPLAY);

		Locale locale = themeDisplay.getLocale();

		Map<String, Serializable> values = objectEntry.getValues();

		List<TemplateNode> templateNodes = new ArrayList<>();

		for (DDMFormField ddmFormField : ddmForm.getDDMFormFields()) {
			if (CMSMigrationDDMFormFieldUtil.isIgnoredField(ddmFormField)) {
				continue;
			}

			templateNodes.add(
				_getTemplateNode(ddmFormField, locale, themeDisplay, values));
		}

		templateNodes.addAll(
			_includeBackwardsCompatibilityTemplateNodes(templateNodes, -1));

		for (TemplateNode templateNode : templateNodes) {
			context.put(templateNode.getName(), templateNode);
		}

		for (String name : _getFieldReferences(ddmForm.getDDMFormFields())) {
			context.computeIfAbsent(
				name,
				key -> new TemplateNode(
					locale, themeDisplay, key, StringPool.BLANK,
					StringPool.BLANK, new HashMap<>()));
		}

		_putReservedTemplateNodes(
			context, locale, objectEntry, themeDisplay, values);

		Group group = _groupLocalService.getGroup(objectEntry.getGroupId());

		context.put("articleGroupId", objectEntry.getGroupId());
		context.put("articleLocale", locale);
		context.put(
			"cmsMigrationTokens",
			_getTokens(objectEntry, settingsJSONObject, themeDisplay));
		context.put("groupId", objectEntry.getGroupId());
		context.put("journalContent", _journalContent);
		context.put("journalContentUtil", _journalContent);

		String templatesPath = StringBundler.concat(
			TemplateConstants.TEMPLATE_SEPARATOR, StringPool.SLASH,
			group.getCompanyId(), StringPool.SLASH,
			settingsJSONObject.getLong("templateGroupId"), StringPool.SLASH,
			settingsJSONObject.getLong("ddmStructureClassNameId"));

		context.put("journalTemplatesPath", templatesPath);
		context.put("templatesPath", templatesPath);

		context.put(
			"randomNamespace", StringUtil.randomId() + StringPool.UNDERLINE);
		context.put("viewMode", "view");

		return context;
	}

	public String getReadingTime(AssetEntry assetEntry) throws Exception {
		ReadingTimeEntry readingTimeEntry =
			_readingTimeEntryLocalService.fetchReadingTimeEntry(
				_objectEntryService.getObjectEntry(assetEntry.getClassPK()));

		if (readingTimeEntry == null) {
			return StringPool.BLANK;
		}

		ThemeDisplay themeDisplay =
			(ThemeDisplay)_httpServletRequest.getAttribute(
				WebKeys.THEME_DISPLAY);

		return _readingTimeMessageProvider.provide(
			readingTimeEntry, themeDisplay.getLocale());
	}

	private void _addOptions(
		DDMFormField ddmFormField, Locale locale, TemplateNode templateNode,
		List<String> optionReferences) {

		DDMFormFieldOptions ddmFormFieldOptions =
			ddmFormField.getDDMFormFieldOptions();

		Map<String, String> optionsReferences =
			ddmFormFieldOptions.getOptionsReferences();

		for (Map.Entry<String, LocalizedValue> entry :
				ddmFormFieldOptions.getOptions(
				).entrySet()) {

			LocalizedValue localizedValue = entry.getValue();

			templateNode.appendOptionMap(
				optionsReferences.getOrDefault(entry.getKey(), entry.getKey()),
				localizedValue.getString(locale));
		}

		if (Objects.equals(
				ddmFormField.getType(), DDMFormFieldTypeConstants.SELECT)) {

			templateNode.appendOptions(optionReferences);
		}
	}

	private String _formatDate(String pattern, Object value) {
		if (!(value instanceof Date)) {
			return GetterUtil.getString(value);
		}

		DateFormat dateFormat = new SimpleDateFormat(pattern);

		return dateFormat.format((Date)value);
	}

	private List<String> _getFieldReferences(List<DDMFormField> ddmFormFields) {
		List<String> fieldReferences = new ArrayList<>();

		for (DDMFormField ddmFormField : ddmFormFields) {
			fieldReferences.add(ddmFormField.getFieldReference());
			fieldReferences.addAll(
				_getFieldReferences(ddmFormField.getNestedDDMFormFields()));
		}

		return fieldReferences;
	}

	private String _getFileEntryData(
		Map<String, String> attributes, long fileEntryId, String alt) {

		if (fileEntryId == 0) {
			return StringPool.BLANK;
		}

		try {
			FileEntry fileEntry = _dlAppLocalService.getFileEntry(fileEntryId);

			JSONObject jsonObject = JSONUtil.put(
				"alt", GetterUtil.getString(alt)
			).put(
				"classPK", fileEntry.getFileEntryId()
			).put(
				"fileEntryId", fileEntry.getFileEntryId()
			).put(
				"groupId", fileEntry.getGroupId()
			).put(
				"name", fileEntry.getFileName()
			).put(
				"resourcePrimKey", fileEntry.getPrimaryKey()
			).put(
				"title", fileEntry.getTitle()
			).put(
				"type", "document"
			).put(
				"uuid", fileEntry.getUuid()
			);

			for (String key : jsonObject.keySet()) {
				attributes.put(key, jsonObject.getString(key));
			}

			return jsonObject.toString();
		}
		catch (Exception exception) {
			if (_log.isDebugEnabled()) {
				_log.debug(exception);
			}

			return StringPool.BLANK;
		}
	}

	private String _getFileEntryURL(long fileEntryId) {
		if (fileEntryId <= 0) {
			return StringPool.BLANK;
		}

		try {
			FileEntry fileEntry = _dlAppLocalService.getFileEntry(fileEntryId);

			return _dlURLHelper.getPreviewURL(
				fileEntry, fileEntry.getFileVersion(), null, StringPool.BLANK,
				false, false);
		}
		catch (Exception exception) {
			if (_log.isDebugEnabled()) {
				_log.debug(exception);
			}

			return StringPool.BLANK;
		}
	}

	private Object _getLocalizedValue(
		String languageId, String name, Map<String, Serializable> values) {

		Object value = values.get(name + "_i18n");

		if (value instanceof Map) {
			Map<String, Object> localizedValues = (Map<String, Object>)value;

			Object localizedValue = localizedValues.get(languageId);

			if (Validator.isNotNull(localizedValue)) {
				return localizedValue;
			}
		}

		return values.get(name);
	}

	private String _getOptionValue(DDMFormField ddmFormField, String key) {
		DDMFormFieldOptions ddmFormFieldOptions =
			ddmFormField.getDDMFormFieldOptions();

		for (String optionValue : ddmFormFieldOptions.getOptionsValues()) {
			if (Objects.equals(
					CMSMigrationDDMFormFieldUtil.getListTypeEntryKey(
						optionValue),
					key)) {

				Map<String, String> optionsReferences =
					ddmFormFieldOptions.getOptionsReferences();

				return optionsReferences.getOrDefault(optionValue, optionValue);
			}
		}

		return key;
	}

	private String _getSmallImageURL(Map<String, Serializable> values) {
		String smallImageURL = _getFileEntryURL(
			GetterUtil.getLong(
				values.get(
					CMSMigrationConstants.OBJECT_FIELD_NAME_SMALL_IMAGE)));

		if (Validator.isNotNull(smallImageURL)) {
			return smallImageURL;
		}

		return GetterUtil.getString(
			values.get(
				CMSMigrationConstants.OBJECT_FIELD_NAME_SMALL_IMAGE_URL));
	}

	private TemplateNode _getTemplateNode(
		DDMFormField ddmFormField, Locale locale, ThemeDisplay themeDisplay,
		Map<String, Serializable> values) {

		if (CMSMigrationDDMFormFieldUtil.isJSONField(ddmFormField)) {
			return _getTemplateNodeFromJSON(
				ddmFormField, locale, themeDisplay,
				GetterUtil.getString(
					values.get(
						CMSMigrationDDMFormFieldUtil.getObjectFieldName(
							ddmFormField))));
		}

		String languageId = LocaleUtil.toLanguageId(locale);
		Map<String, String> attributes = new HashMap<>();
		List<String> optionReferences = new ArrayList<>();

		String data = StringPool.BLANK;

		if (!Objects.equals(
				ddmFormField.getType(), DDMFormFieldTypeConstants.FIELDSET)) {

			data = _getTemplateNodeData(
				attributes, ddmFormField, languageId, optionReferences, values);
		}

		TemplateNode templateNode = new TemplateNode(
			locale, themeDisplay, ddmFormField.getFieldReference(), data,
			ddmFormField.getType(), attributes);

		_addOptions(ddmFormField, locale, templateNode, optionReferences);

		for (DDMFormField nestedDDMFormField :
				ddmFormField.getNestedDDMFormFields()) {

			if (CMSMigrationDDMFormFieldUtil.isIgnoredField(
					nestedDDMFormField)) {

				continue;
			}

			templateNode.appendChild(
				_getTemplateNode(
					nestedDDMFormField, locale, themeDisplay, values));
		}

		templateNode.appendSibling(templateNode);

		return templateNode;
	}

	private String _getTemplateNodeData(
		Map<String, String> attributes, DDMFormField ddmFormField,
		String languageId, List<String> optionReferences,
		Map<String, Serializable> values) {

		String name = CMSMigrationDDMFormFieldUtil.getObjectFieldName(
			ddmFormField);

		Object value = _getLocalizedValue(languageId, name, values);

		String businessType = CMSMigrationDDMFormFieldUtil.getBusinessType(
			ddmFormField);

		if (businessType.equals(
				ObjectFieldConstants.BUSINESS_TYPE_ATTACHMENT)) {

			return _getFileEntryData(
				attributes, GetterUtil.getLong(value),
				GetterUtil.getString(
					_getLocalizedValue(
						languageId,
						CMSMigrationDDMFormFieldUtil.getAltObjectFieldName(
							ddmFormField),
						values)));
		}

		if (value == null) {
			return StringPool.BLANK;
		}

		if (businessType.equals(ObjectFieldConstants.BUSINESS_TYPE_DATE)) {
			return _formatDate("yyyy-MM-dd", value);
		}
		else if (businessType.equals(
					ObjectFieldConstants.BUSINESS_TYPE_DATE_TIME)) {

			return _formatDate("yyyy-MM-dd HH:mm", value);
		}
		else if (businessType.equals(
					ObjectFieldConstants.BUSINESS_TYPE_DECIMAL) ||
				 businessType.equals(
					 ObjectFieldConstants.BUSINESS_TYPE_LONG_INTEGER)) {

			return _toNumberString(value);
		}
		else if (businessType.equals(
					ObjectFieldConstants.BUSINESS_TYPE_MULTISELECT_PICKLIST)) {

			JSONArray jsonArray = JSONFactoryUtil.createJSONArray();

			for (String key :
					StringUtil.split(
						GetterUtil.getString(value),
						StringPool.COMMA_AND_SPACE)) {

				String optionReference = _getOptionValue(ddmFormField, key);

				jsonArray.put(optionReference);
				optionReferences.add(optionReference);
			}

			return jsonArray.toString();
		}
		else if (businessType.equals(
					ObjectFieldConstants.BUSINESS_TYPE_PICKLIST)) {

			String optionReference = _getOptionValue(
				ddmFormField, GetterUtil.getString(value));

			optionReferences.add(optionReference);

			return optionReference;
		}

		String data = GetterUtil.getString(value);

		if (Objects.equals(
				ddmFormField.getType(), DDMFormFieldTypeConstants.TEXT)) {

			return HtmlUtil.escape(data);
		}

		if (Objects.equals(
				ddmFormField.getType(),
				DDMFormFieldTypeConstants.JOURNAL_ARTICLE)) {

			return _resolveJournalArticleData(data);
		}

		return data;
	}

	private TemplateNode _getTemplateNodeFromJSON(
		DDMFormField ddmFormField, Locale locale, ThemeDisplay themeDisplay,
		String json) {

		List<JSONObject> jsonObjects = new ArrayList<>();

		if (JSONUtil.isJSONArray(json)) {
			try {
				JSONArray jsonArray = JSONFactoryUtil.createJSONArray(json);

				for (int i = 0; i < jsonArray.length(); i++) {
					jsonObjects.add(jsonArray.getJSONObject(i));
				}
			}
			catch (Exception exception) {
				if (_log.isDebugEnabled()) {
					_log.debug(exception);
				}
			}
		}

		if (jsonObjects.isEmpty()) {
			jsonObjects.add(JSONFactoryUtil.createJSONObject());
		}

		TemplateNode prototypeTemplateNode = null;

		for (JSONObject jsonObject : jsonObjects) {
			TemplateNode templateNode = _getTemplateNodeFromJSONObject(
				ddmFormField, locale, themeDisplay, jsonObject);

			if (prototypeTemplateNode == null) {
				prototypeTemplateNode = templateNode;
			}

			prototypeTemplateNode.appendSibling(templateNode);
		}

		return prototypeTemplateNode;
	}

	private TemplateNode _getTemplateNodeFromJSONObject(
		DDMFormField ddmFormField, Locale locale, ThemeDisplay themeDisplay,
		JSONObject jsonObject) {

		String data = StringPool.BLANK;

		JSONObject valuesJSONObject = jsonObject.getJSONObject("values");

		if (valuesJSONObject != null) {
			data = valuesJSONObject.getString(LocaleUtil.toLanguageId(locale));

			if (Validator.isNull(data)) {
				data = valuesJSONObject.getString(
					LocaleUtil.toLanguageId(LocaleUtil.getSiteDefault()));
			}

			if (Validator.isNull(data) && (valuesJSONObject.length() > 0)) {
				data = valuesJSONObject.getString(
					valuesJSONObject.keys(
					).next());
			}
		}

		String type = ddmFormField.getType();

		Map<String, String> attributes = new HashMap<>();
		List<String> optionReferences = new ArrayList<>();

		if (type.equals(DDMFormFieldTypeConstants.DOCUMENT_LIBRARY) ||
			type.equals(DDMFormFieldTypeConstants.IMAGE)) {

			if (JSONUtil.isJSONObject(data)) {
				try {
					JSONObject dataJSONObject =
						JSONFactoryUtil.createJSONObject(data);

					for (String key : dataJSONObject.keySet()) {
						attributes.put(key, dataJSONObject.getString(key));
					}
				}
				catch (Exception exception) {
					if (_log.isDebugEnabled()) {
						_log.debug(exception);
					}
				}
			}
		}
		else if (type.equals(DDMFormFieldTypeConstants.JOURNAL_ARTICLE)) {
			data = _resolveJournalArticleData(data);
		}
		else if (type.equals(DDMFormFieldTypeConstants.RADIO) ||
				 type.equals(DDMFormFieldTypeConstants.SELECT) ||
				 type.equals(DDMFormFieldTypeConstants.CHECKBOX_MULTIPLE)) {

			data = _toOptionReferences(ddmFormField, data, optionReferences);
		}
		else if (type.equals(DDMFormFieldTypeConstants.TEXT)) {
			data = HtmlUtil.escape(data);
		}

		TemplateNode templateNode = new TemplateNode(
			locale, themeDisplay, ddmFormField.getFieldReference(), data, type,
			attributes);

		_addOptions(ddmFormField, locale, templateNode, optionReferences);

		Map<String, List<JSONObject>> nestedJSONObjectsMap =
			new LinkedHashMap<>();

		JSONArray nestedJSONArray = jsonObject.getJSONArray(
			"nestedFieldValues");

		if (nestedJSONArray != null) {
			for (int i = 0; i < nestedJSONArray.length(); i++) {
				JSONObject nestedJSONObject = nestedJSONArray.getJSONObject(i);

				List<JSONObject> nestedJSONObjects =
					nestedJSONObjectsMap.computeIfAbsent(
						nestedJSONObject.getString("name"),
						key -> new ArrayList<>());

				nestedJSONObjects.add(nestedJSONObject);
			}
		}

		for (DDMFormField nestedDDMFormField :
				ddmFormField.getNestedDDMFormFields()) {

			if (CMSMigrationDDMFormFieldUtil.isIgnoredField(
					nestedDDMFormField)) {

				continue;
			}

			List<JSONObject> nestedJSONObjects = nestedJSONObjectsMap.get(
				nestedDDMFormField.getName());

			if (ListUtil.isEmpty(nestedJSONObjects)) {
				nestedJSONObjects = Arrays.asList(
					JSONFactoryUtil.createJSONObject());
			}

			TemplateNode prototypeTemplateNode = null;

			for (JSONObject nestedJSONObject : nestedJSONObjects) {
				TemplateNode nestedTemplateNode =
					_getTemplateNodeFromJSONObject(
						nestedDDMFormField, locale, themeDisplay,
						nestedJSONObject);

				if (prototypeTemplateNode == null) {
					prototypeTemplateNode = nestedTemplateNode;

					templateNode.appendChild(nestedTemplateNode);
				}

				prototypeTemplateNode.appendSibling(nestedTemplateNode);
			}
		}

		return templateNode;
	}

	private Map<String, String> _getTokens(
			ObjectEntry objectEntry, JSONObject settingsJSONObject,
			ThemeDisplay themeDisplay)
		throws Exception {

		Layout layout = themeDisplay.getLayout();

		String friendlyURLCurrent = themeDisplay.getPathFriendlyURLPublic();

		if ((layout != null) && layout.isPrivateLayout()) {
			friendlyURLCurrent = themeDisplay.getPathFriendlyURLPrivateGroup();
		}

		Group siteGroup = themeDisplay.getSiteGroup();

		return HashMapBuilder.put(
			"article_group_id", String.valueOf(objectEntry.getGroupId())
		).put(
			"article_resource_pk",
			String.valueOf(objectEntry.getObjectEntryId())
		).put(
			"cdn_host", themeDisplay.getCDNHost()
		).put(
			"class_name_id",
			settingsJSONObject.getString("ddmStructureClassNameId")
		).put(
			"company_group_id", String.valueOf(themeDisplay.getCompanyGroupId())
		).put(
			"company_id", String.valueOf(themeDisplay.getCompanyId())
		).put(
			"ddm_structure_id", settingsJSONObject.getString("ddmStructureId")
		).put(
			"ddm_structure_key", settingsJSONObject.getString("ddmStructureKey")
		).put(
			"ddm_template_id", settingsJSONObject.getString("ddmTemplateId")
		).put(
			"ddm_template_key", settingsJSONObject.getString("ddmTemplateKey")
		).put(
			"friendly_url_current", friendlyURLCurrent
		).put(
			"friendly_url_private_group",
			themeDisplay.getPathFriendlyURLPrivateGroup()
		).put(
			"friendly_url_private_user",
			themeDisplay.getPathFriendlyURLPrivateUser()
		).put(
			"friendly_url_public", themeDisplay.getPathFriendlyURLPublic()
		).put(
			"group_friendly_url", siteGroup.getFriendlyURL()
		).put(
			"image_path", themeDisplay.getPathImage()
		).put(
			"layout_set_friendly_url",
			friendlyURLCurrent + siteGroup.getFriendlyURL()
		).put(
			"main_path", themeDisplay.getPathMain()
		).put(
			"portal_ctx", themeDisplay.getPathContext()
		).put(
			"portal_url", themeDisplay.getURLPortal()
		).put(
			"protocol", themeDisplay.isSecure() ? "https" : "http"
		).put(
			"root_path", themeDisplay.getPathContext()
		).put(
			"scope_group_id", String.valueOf(themeDisplay.getScopeGroupId())
		).put(
			"site_group_id", String.valueOf(themeDisplay.getSiteGroupId())
		).put(
			"theme_image_path", themeDisplay.getPathThemeImages()
		).build();
	}

	private List<TemplateNode> _includeBackwardsCompatibilityTemplateNodes(
		List<TemplateNode> templateNodes, int parentOffset) {

		List<TemplateNode> backwardsCompatibilityTemplateNodes =
			new ArrayList<>();

		parentOffset++;

		for (TemplateNode templateNode : templateNodes) {
			if (!Objects.equals(
					templateNode.getType(),
					DDMFormFieldTypeConstants.FIELDSET)) {

				if (parentOffset > 0) {
					backwardsCompatibilityTemplateNodes.add(
						(TemplateNode)templateNode.clone());
				}

				continue;
			}

			List<TemplateNode> childTemplateNodes = templateNode.getChildren();

			if (ListUtil.isEmpty(childTemplateNodes)) {
				continue;
			}

			String fieldSetName = templateNode.getName();

			if (!fieldSetName.endsWith("FieldSet")) {
				continue;
			}

			String name = fieldSetName.substring(
				0, fieldSetName.indexOf("FieldSet"));

			TemplateNode mainChildTemplateNode = templateNode.getChild(name);

			if (mainChildTemplateNode == null) {
				backwardsCompatibilityTemplateNodes.addAll(
					_includeBackwardsCompatibilityTemplateNodes(
						childTemplateNodes, parentOffset));

				continue;
			}

			if (Objects.equals(
					mainChildTemplateNode.getType(),
					DDMFormFieldTypeConstants.FIELDSET)) {

				backwardsCompatibilityTemplateNodes.addAll(
					_includeBackwardsCompatibilityTemplateNodes(
						Arrays.asList(mainChildTemplateNode), parentOffset));

				continue;
			}

			List<TemplateNode> newChildTemplateNodes = new ArrayList<>(
				childTemplateNodes);

			newChildTemplateNodes.remove(mainChildTemplateNode);

			List<TemplateNode> newSiblingsTemplateNodes = new ArrayList<>(
				mainChildTemplateNode.getSiblings());

			if (!newSiblingsTemplateNodes.isEmpty()) {
				newSiblingsTemplateNodes.remove(mainChildTemplateNode);
			}

			mainChildTemplateNode = (TemplateNode)mainChildTemplateNode.clone();

			mainChildTemplateNode.appendChildren(
				_includeBackwardsCompatibilityTemplateNodes(
					newChildTemplateNodes, parentOffset));

			List<TemplateNode> siblingsTemplateNodes =
				templateNode.getSiblings();

			if (!siblingsTemplateNodes.isEmpty()) {
				newSiblingsTemplateNodes.addAll(
					ListUtil.subList(
						siblingsTemplateNodes, 1,
						siblingsTemplateNodes.size()));
			}

			List<TemplateNode> mainChildSiblingsTemplateNodes =
				mainChildTemplateNode.getSiblings();

			mainChildSiblingsTemplateNodes.clear();

			mainChildSiblingsTemplateNodes.add(mainChildTemplateNode);

			mainChildSiblingsTemplateNodes.addAll(
				_includeBackwardsCompatibilityTemplateNodes(
					newSiblingsTemplateNodes, parentOffset));

			backwardsCompatibilityTemplateNodes.add(mainChildTemplateNode);
		}

		return backwardsCompatibilityTemplateNodes;
	}

	private void _putReservedTemplateNode(
		Map<String, Object> context, Locale locale, String name,
		ThemeDisplay themeDisplay, String value) {

		context.put(
			name,
			new TemplateNode(
				locale, themeDisplay, name, GetterUtil.getString(value),
				StringPool.BLANK, new HashMap<>()));
	}

	private void _putReservedTemplateNodes(
			Map<String, Object> context, Locale locale, ObjectEntry objectEntry,
			ThemeDisplay themeDisplay, Map<String, Serializable> values)
		throws Exception {

		String languageId = LocaleUtil.toLanguageId(locale);

		ObjectDefinition objectDefinition =
			_objectDefinitionLocalService.getObjectDefinition(
				objectEntry.getObjectDefinitionId());

		_putReservedTemplateNode(
			context, locale, "reserved-article-asset-tag-names", themeDisplay,
			StringUtil.merge(
				_assetTagLocalService.getTagNames(
					objectDefinition.getClassName(),
					objectEntry.getObjectEntryId())));

		User user = _userLocalService.fetchUser(objectEntry.getUserId());

		if (user != null) {
			_putReservedTemplateNode(
				context, locale, "reserved-article-author-comments",
				themeDisplay, user.getComments());
			_putReservedTemplateNode(
				context, locale, "reserved-article-author-email-address",
				themeDisplay, user.getEmailAddress());
			_putReservedTemplateNode(
				context, locale, "reserved-article-author-job-title",
				themeDisplay, user.getJobTitle());
			_putReservedTemplateNode(
				context, locale, "reserved-article-author-name", themeDisplay,
				user.getFullName());
		}
		else {
			_putReservedTemplateNode(
				context, locale, "reserved-article-author-comments",
				themeDisplay, StringPool.BLANK);
			_putReservedTemplateNode(
				context, locale, "reserved-article-author-email-address",
				themeDisplay, StringPool.BLANK);
			_putReservedTemplateNode(
				context, locale, "reserved-article-author-job-title",
				themeDisplay, StringPool.BLANK);
			_putReservedTemplateNode(
				context, locale, "reserved-article-author-name", themeDisplay,
				objectEntry.getUserName());
		}

		_putReservedTemplateNode(
			context, locale, "reserved-article-author-id", themeDisplay,
			String.valueOf(objectEntry.getUserId()));
		_putReservedTemplateNode(
			context, locale, "reserved-article-create-date", themeDisplay,
			Time.getRFC822(objectEntry.getCreateDate()));
		_putReservedTemplateNode(
			context, locale, "reserved-article-description", themeDisplay,
			GetterUtil.getString(
				_getLocalizedValue(
					languageId,
					CMSMigrationConstants.OBJECT_FIELD_NAME_DESCRIPTION,
					values)));

		if (objectEntry.getDisplayDate() != null) {
			_putReservedTemplateNode(
				context, locale, "reserved-article-display-date", themeDisplay,
				Time.getRFC822(objectEntry.getDisplayDate()));
		}

		_putReservedTemplateNode(
			context, locale, "reserved-article-external-reference-code",
			themeDisplay, objectEntry.getExternalReferenceCode());
		_putReservedTemplateNode(
			context, locale, "reserved-article-id", themeDisplay,
			String.valueOf(objectEntry.getObjectEntryId()));
		_putReservedTemplateNode(
			context, locale, "reserved-article-id_", themeDisplay,
			String.valueOf(objectEntry.getObjectEntryId()));
		_putReservedTemplateNode(
			context, locale, "reserved-article-modified-date", themeDisplay,
			Time.getRFC822(objectEntry.getModifiedDate()));
		_putReservedTemplateNode(
			context, locale, "reserved-article-resource-prim-key", themeDisplay,
			String.valueOf(objectEntry.getObjectEntryId()));
		_putReservedTemplateNode(
			context, locale, "reserved-article-small-image-url", themeDisplay,
			_getSmallImageURL(values));
		_putReservedTemplateNode(
			context, locale, "reserved-article-title", themeDisplay,
			objectEntry.getTitleValue(languageId));

		FriendlyURLEntry friendlyURLEntry =
			_friendlyURLEntryLocalService.fetchMainFriendlyURLEntry(
				_classNameLocalService.getClassNameId(
					objectDefinition.getClassName()),
				objectEntry.getObjectEntryId());

		String urlTitle = StringPool.BLANK;

		if (friendlyURLEntry != null) {
			urlTitle = friendlyURLEntry.getUrlTitle();
		}

		_putReservedTemplateNode(
			context, locale, "reserved-article-url-title", themeDisplay,
			urlTitle);
		_putReservedTemplateNode(
			context, locale, "reserved-article-version", themeDisplay,
			String.valueOf(objectEntry.getVersion()));
	}

	private String _resolveJournalArticleData(String data) {
		if (!JSONUtil.isJSONObject(data)) {
			return data;
		}

		try {
			JSONObject jsonObject = JSONFactoryUtil.createJSONObject(data);

			String objectDefinitionExternalReferenceCode = jsonObject.getString(
				"objectDefinitionExternalReferenceCode");

			if (Validator.isNull(objectDefinitionExternalReferenceCode)) {
				return data;
			}

			ThemeDisplay themeDisplay =
				(ThemeDisplay)_httpServletRequest.getAttribute(
					WebKeys.THEME_DISPLAY);

			ObjectDefinition objectDefinition =
				_objectDefinitionLocalService.
					fetchObjectDefinitionByExternalReferenceCode(
						objectDefinitionExternalReferenceCode,
						themeDisplay.getCompanyId());

			if (objectDefinition == null) {
				return data;
			}

			ObjectEntry objectEntry = _objectEntryLocalService.fetchObjectEntry(
				jsonObject.getString("externalReferenceCode"),
				jsonObject.getLong("groupId"),
				objectDefinition.getObjectDefinitionId());

			if (objectEntry == null) {
				return data;
			}

			return jsonObject.put(
				"classPK", objectEntry.getObjectEntryId()
			).put(
				"uuid", objectEntry.getUuid()
			).toString();
		}
		catch (Exception exception) {
			if (_log.isDebugEnabled()) {
				_log.debug(exception);
			}

			return data;
		}
	}

	private String _toNumberString(Object value) {
		if (value instanceof Double) {
			double doubleValue = (Double)value;

			if (doubleValue == Math.rint(doubleValue)) {
				return String.valueOf((long)doubleValue);
			}
		}

		return String.valueOf(value);
	}

	private String _toOptionReferences(
		DDMFormField ddmFormField, String data, List<String> optionReferences) {

		DDMFormFieldOptions ddmFormFieldOptions =
			ddmFormField.getDDMFormFieldOptions();

		Map<String, String> optionsReferences =
			ddmFormFieldOptions.getOptionsReferences();

		List<String> optionValues = new ArrayList<>();

		if (JSONUtil.isJSONArray(data)) {
			try {
				JSONArray jsonArray = JSONFactoryUtil.createJSONArray(data);

				for (int i = 0; i < jsonArray.length(); i++) {
					optionValues.add(jsonArray.getString(i));
				}
			}
			catch (Exception exception) {
				if (_log.isDebugEnabled()) {
					_log.debug(exception);
				}
			}
		}
		else if (Validator.isNotNull(data)) {
			optionValues.add(data);
		}

		for (String optionValue : optionValues) {
			optionReferences.add(
				optionsReferences.getOrDefault(optionValue, optionValue));
		}

		if (Objects.equals(
				ddmFormField.getType(),
				DDMFormFieldTypeConstants.CHECKBOX_MULTIPLE) ||
			(Objects.equals(
				ddmFormField.getType(), DDMFormFieldTypeConstants.SELECT) &&
			 ddmFormField.isMultiple())) {

			return JSONUtil.putAll(
				optionReferences.toArray()
			).toString();
		}

		if (optionReferences.isEmpty()) {
			return StringPool.BLANK;
		}

		return optionReferences.get(0);
	}

	private static final Log _log = LogFactoryUtil.getLog(
		CMSMigrationTemplateContextHelper.class);

	private static final Base64.Decoder _decoder = Base64.getDecoder();

	private final AssetDisplayPageFriendlyURLProvider
		_assetDisplayPageFriendlyURLProvider;
	private final AssetTagLocalService _assetTagLocalService;
	private final ClassNameLocalService _classNameLocalService;
	private final Function<String, DDMForm> _ddmFormFunction;
	private final DLAppLocalService _dlAppLocalService;
	private final DLURLHelper _dlURLHelper;
	private final FriendlyURLEntryLocalService _friendlyURLEntryLocalService;
	private final GroupLocalService _groupLocalService;
	private final HttpServletRequest _httpServletRequest;
	private final JournalContent _journalContent;
	private final Language _language;
	private final ObjectDefinitionLocalService _objectDefinitionLocalService;
	private final ObjectEntryLocalService _objectEntryLocalService;
	private final ObjectEntryService _objectEntryService;
	private final ReadingTimeEntryLocalService _readingTimeEntryLocalService;
	private final ReadingTimeMessageProvider _readingTimeMessageProvider;
	private final UserLocalService _userLocalService;

}