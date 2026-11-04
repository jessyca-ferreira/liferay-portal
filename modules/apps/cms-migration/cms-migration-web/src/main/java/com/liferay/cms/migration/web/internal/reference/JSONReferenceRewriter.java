/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.reference;

import com.liferay.cms.migration.web.internal.constants.CMSMigrationConstants;
import com.liferay.cms.migration.web.internal.migration.CMSMigrationContext;
import com.liferay.document.library.kernel.model.DLFileEntry;
import com.liferay.document.library.kernel.service.DLAppLocalService;
import com.liferay.dynamic.data.mapping.model.DDMStructure;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.service.JournalArticleLocalService;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.Validator;

import java.io.Serializable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Walks the JSON that pages store, such as fragment editable values and
 * layout structure item configurations, and repoints every reference to
 * classic content at the migrated object entry.
 *
 * @author Lino Alves
 */
public class JSONReferenceRewriter {

	public JSONReferenceRewriter(
		DLAppLocalService dlAppLocalService,
		HTMLReferenceRewriter htmlReferenceRewriter,
		InfoItemReferenceMapper infoItemReferenceMapper,
		JournalArticleLocalService journalArticleLocalService, Portal portal) {

		_dlAppLocalService = dlAppLocalService;
		_htmlReferenceRewriter = htmlReferenceRewriter;
		_infoItemReferenceMapper = infoItemReferenceMapper;
		_journalArticleLocalService = journalArticleLocalService;
		_portal = portal;
	}

	/**
	 * Rewrites the JSON object in place.
	 *
	 * @param  referenceScope the item type that <code>mappedField</code> and
	 *         <code>collectionFieldId</code> values resolve against, or
	 *         <code>null</code>
	 * @return whether the JSON object changed
	 */
	public boolean rewrite(
		CMSMigrationContext cmsMigrationContext, JSONObject jsonObject,
		ReferenceScope referenceScope) {

		return _rewrite(cmsMigrationContext, jsonObject, false, referenceScope);
	}

	private FileEntry _getAttachmentFileEntry(
		CMSMigrationContext cmsMigrationContext, long fileEntryId) {

		try {
			FileEntry fileEntry = _dlAppLocalService.getFileEntry(fileEntryId);

			if (fileEntry.getGroupId() ==
					cmsMigrationContext.getDepotGroupId()) {

				return null;
			}

			ObjectEntry objectEntry = cmsMigrationContext.getOrMigrateFileEntry(
				fileEntry);

			if (objectEntry == null) {
				return null;
			}

			Map<String, Serializable> values = objectEntry.getValues();

			long attachmentFileEntryId = GetterUtil.getLong(
				values.get(CMSMigrationConstants.OBJECT_FIELD_NAME_FILE));

			if (attachmentFileEntryId == 0) {
				return null;
			}

			return _dlAppLocalService.getFileEntry(attachmentFileEntryId);
		}
		catch (Exception exception) {
			if (_log.isDebugEnabled()) {
				_log.debug(exception);
			}

			return null;
		}
	}

	private void _put(JSONObject jsonObject, String key, Object value) {
		Object currentValue = jsonObject.opt(key);

		if (currentValue instanceof String) {
			jsonObject.put(key, String.valueOf(value));
		}
		else {
			jsonObject.put(key, value);
		}
	}

	private boolean _rewrite(
		CMSMigrationContext cmsMigrationContext, JSONArray jsonArray,
		boolean link, ReferenceScope referenceScope) {

		boolean changed = false;

		for (int i = 0; i < jsonArray.length(); i++) {
			Object value = jsonArray.get(i);

			if (value instanceof JSONArray) {
				changed |= _rewrite(
					cmsMigrationContext, (JSONArray)value, link,
					referenceScope);
			}
			else if (value instanceof JSONObject) {
				changed |= _rewrite(
					cmsMigrationContext, (JSONObject)value, link,
					referenceScope);
			}
		}

		return changed;
	}

	private boolean _rewrite(
		CMSMigrationContext cmsMigrationContext, JSONObject jsonObject,
		boolean link, ReferenceScope referenceScope) {

		boolean changed = false;

		List<String> keys = new ArrayList<>(jsonObject.keySet());

		for (String key : keys) {
			Object value = jsonObject.opt(key);

			if (value instanceof JSONArray) {
				changed |= _rewrite(
					cmsMigrationContext, (JSONArray)value, link,
					referenceScope);
			}
			else if (value instanceof JSONObject) {
				changed |= _rewrite(
					cmsMigrationContext, (JSONObject)value,
					link || key.equals("config") || key.equals("link"),
					referenceScope);
			}
			else if (value instanceof String) {
				String newValue = _rewriteHTML(
					cmsMigrationContext, (String)value);

				if (!Objects.equals(value, newValue)) {
					jsonObject.put(key, newValue);

					changed = true;
				}
			}
		}

		if (jsonObject.has("classPK") &&
			(jsonObject.has("classNameId") || jsonObject.has("className"))) {

			changed |= _rewriteItemReference(
				cmsMigrationContext, jsonObject, link);
		}
		else if (jsonObject.has("externalReferenceCode") &&
				 jsonObject.has("className")) {

			changed |= _rewriteItemReference(
				cmsMigrationContext, jsonObject, link);
		}
		else if (jsonObject.has("fileEntryId")) {
			changed |= _rewriteFileEntryReference(
				cmsMigrationContext, jsonObject);
		}

		if (referenceScope != null) {
			changed |= _rewriteImplicitFieldReference(
				cmsMigrationContext, jsonObject, "collectionFieldId", link,
				referenceScope);
			changed |= _rewriteImplicitFieldReference(
				cmsMigrationContext, jsonObject, "mappedField", link,
				referenceScope);
		}

		return changed;
	}

	private boolean _rewriteFileEntryReference(
		CMSMigrationContext cmsMigrationContext, JSONObject jsonObject) {

		long fileEntryId = jsonObject.getLong("fileEntryId");

		if (fileEntryId <= 0) {
			return false;
		}

		FileEntry attachmentFileEntry = _getAttachmentFileEntry(
			cmsMigrationContext, fileEntryId);

		if (attachmentFileEntry == null) {
			return false;
		}

		_put(jsonObject, "fileEntryId", attachmentFileEntry.getFileEntryId());

		if (jsonObject.has("url")) {
			jsonObject.put(
				"url",
				_htmlReferenceRewriter.getFileEntryURL(attachmentFileEntry));
		}

		return true;
	}

	private String _rewriteHTML(
		CMSMigrationContext cmsMigrationContext, String value) {

		if (Validator.isNull(value) ||
			(!value.contains("/documents/") && !value.contains("/w/") &&
			 !value.contains("/b/") && !value.contains("adaptive-media") &&
			 !value.contains("data-fileentryid"))) {

			return value;
		}

		value = _htmlReferenceRewriter.rewriteFileReferences(
			cmsMigrationContext, value);

		return _htmlReferenceRewriter.rewriteContentLinks(
			cmsMigrationContext, value);
	}

	private boolean _rewriteImplicitFieldReference(
		CMSMigrationContext cmsMigrationContext, JSONObject jsonObject,
		String key, boolean link, ReferenceScope referenceScope) {

		String fieldId = jsonObject.getString(key);

		if (Validator.isNull(fieldId)) {
			return false;
		}

		ObjectDefinition objectDefinition =
			referenceScope.getObjectDefinition();

		String newFieldId = _infoItemReferenceMapper.getFieldId(
			cmsMigrationContext, referenceScope.getSourceClassName(),
			referenceScope.getDDMStructure(), objectDefinition, fieldId, link);

		if (newFieldId == null) {
			cmsMigrationContext.getCMSMigrationReport(
			).addWarning(
				StringBundler.concat(
					"The field ", fieldId, " has no counterpart in ",
					objectDefinition.getName())
			);

			return false;
		}

		jsonObject.put(key, newFieldId);

		return true;
	}

	private boolean _rewriteItemReference(
		CMSMigrationContext cmsMigrationContext, JSONObject jsonObject,
		boolean link) {

		String sourceClassName = null;

		if (jsonObject.has("className")) {
			sourceClassName = _infoItemReferenceMapper.getSourceClassName(
				jsonObject.getString("className"));
		}

		if ((sourceClassName == null) && jsonObject.has("classNameId")) {
			sourceClassName = _infoItemReferenceMapper.getSourceClassName(
				jsonObject.getLong("classNameId"));
		}

		if (sourceClassName == null) {
			return false;
		}

		long classPK = jsonObject.getLong("classPK");

		if (classPK <= 0) {
			classPK = _infoItemReferenceMapper.getClassPK(
				cmsMigrationContext, sourceClassName,
				jsonObject.getString("externalReferenceCode"),
				jsonObject.getString("scopeExternalReferenceCode"));
		}

		ObjectEntry objectEntry = _infoItemReferenceMapper.getObjectEntry(
			cmsMigrationContext, sourceClassName, classPK);

		if (objectEntry == null) {
			return false;
		}

		ObjectDefinition objectDefinition =
			_infoItemReferenceMapper.getObjectDefinition(objectEntry);
		DDMStructure ddmStructure = _infoItemReferenceMapper.getDDMStructure(
			sourceClassName, classPK);

		String fieldId = jsonObject.getString("fieldId");

		if (Validator.isNotNull(fieldId)) {
			String newFieldId = _infoItemReferenceMapper.getFieldId(
				cmsMigrationContext, sourceClassName, ddmStructure,
				objectDefinition, fieldId, link);

			if (newFieldId == null) {
				cmsMigrationContext.getCMSMigrationReport(
				).addWarning(
					StringBundler.concat(
						"The entry ", objectEntry.getExternalReferenceCode(),
						" has no counterpart for the field ", fieldId)
				);

				return false;
			}

			jsonObject.put("fieldId", newFieldId);
		}

		if (jsonObject.has("className")) {
			jsonObject.put("className", objectDefinition.getClassName());
		}

		if (jsonObject.has("classNameId")) {
			_put(
				jsonObject, "classNameId",
				_portal.getClassNameId(objectDefinition.getClassName()));
		}

		if (jsonObject.has("classPK")) {
			_put(jsonObject, "classPK", objectEntry.getObjectEntryId());
		}

		if (jsonObject.has("externalReferenceCode")) {
			jsonObject.put(
				"externalReferenceCode",
				objectEntry.getExternalReferenceCode());
		}

		if (jsonObject.has("itemSubtype")) {
			jsonObject.put("itemSubtype", StringPool.BLANK);
		}

		if (jsonObject.has("itemType")) {
			jsonObject.put("itemType", objectDefinition.getClassName());
		}

		if (jsonObject.has("scopeExternalReferenceCode")) {
			jsonObject.put(
				"scopeExternalReferenceCode",
				cmsMigrationContext.getDepotGroup(
				).getExternalReferenceCode());
		}

		if (jsonObject.has("fileEntryId") &&
			Objects.equals(sourceClassName, DLFileEntry.class.getName())) {

			_rewriteFileEntryReference(cmsMigrationContext, jsonObject);
		}

		JSONObject templateJSONObject = jsonObject.getJSONObject("template");

		if (templateJSONObject != null) {
			_rewriteTemplate(
				cmsMigrationContext, classPK, ddmStructure, sourceClassName,
				templateJSONObject);
		}

		return true;
	}

	private void _rewriteTemplate(
		CMSMigrationContext cmsMigrationContext, long classPK,
		DDMStructure ddmStructure, String sourceClassName,
		JSONObject templateJSONObject) {

		String defaultDDMTemplateKey = null;

		if (Objects.equals(sourceClassName, JournalArticle.class.getName())) {
			JournalArticle journalArticle =
				_journalArticleLocalService.fetchLatestArticle(classPK);

			if (journalArticle != null) {
				defaultDDMTemplateKey = journalArticle.getDDMTemplateKey();
			}
		}

		String[] rendererKeys = _infoItemReferenceMapper.getRendererKeys(
			cmsMigrationContext, ddmStructure, defaultDDMTemplateKey,
			templateJSONObject.getString("infoItemRendererKey"),
			templateJSONObject.getString("templateKey"));

		if (rendererKeys.length == 0) {
			if (Validator.isNotNull(
					templateJSONObject.getString("infoItemRendererKey"))) {

				cmsMigrationContext.getCMSMigrationReport(
				).addWarning(
					"The renderer " +
						templateJSONObject.getString("infoItemRendererKey") +
							" has no counterpart for migrated content"
				);
			}

			return;
		}

		templateJSONObject.put(
			"infoItemRendererKey", rendererKeys[0]
		).put(
			"templateKey", rendererKeys[1]
		);
	}

	private static final Log _log = LogFactoryUtil.getLog(
		JSONReferenceRewriter.class);

	private final DLAppLocalService _dlAppLocalService;
	private final HTMLReferenceRewriter _htmlReferenceRewriter;
	private final InfoItemReferenceMapper _infoItemReferenceMapper;
	private final JournalArticleLocalService _journalArticleLocalService;
	private final Portal _portal;

}