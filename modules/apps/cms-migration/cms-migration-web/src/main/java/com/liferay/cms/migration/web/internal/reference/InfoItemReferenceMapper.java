/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.reference;

import com.liferay.blogs.model.BlogsEntry;
import com.liferay.blogs.service.BlogsEntryLocalService;
import com.liferay.cms.migration.web.internal.constants.CMSMigrationConstants;
import com.liferay.cms.migration.web.internal.info.item.renderer.CMSMigrationInfoItemRenderer;
import com.liferay.cms.migration.web.internal.migration.CMSMigrationContext;
import com.liferay.cms.migration.web.internal.structure.CMSMigrationDDMFormFieldUtil;
import com.liferay.document.library.kernel.model.DLFileEntry;
import com.liferay.document.library.kernel.service.DLFileEntryLocalService;
import com.liferay.dynamic.data.mapping.model.DDMFormField;
import com.liferay.dynamic.data.mapping.model.DDMStructure;
import com.liferay.dynamic.data.mapping.model.DDMTemplate;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.service.JournalArticleLocalService;
import com.liferay.object.constants.ObjectFieldConstants;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.model.ObjectField;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectFieldLocalService;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.module.service.Snapshot;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.Validator;

import java.util.Map;
import java.util.Objects;

/**
 * Translates references to classic content, as pages store them, into
 * references to the migrated object entries: item identity, mapped field IDs,
 * and info item renderer keys.
 *
 * @author Lino Alves
 */
public class InfoItemReferenceMapper {

	public static final String TEMPLATE_INFO_ITEM_RENDERER_KEY =
		"com.liferay.template.internal.info.item.renderer." +
			"TemplateInfoItemTemplatedRenderer";

	public long getClassPK(
		CMSMigrationContext cmsMigrationContext, String sourceClassName,
		String externalReferenceCode, String scopeExternalReferenceCode) {

		if ((sourceClassName == null) ||
			Validator.isNull(externalReferenceCode)) {

			return 0;
		}

		long groupId = cmsMigrationContext.getRewriteGroupId();

		if (Validator.isNotNull(scopeExternalReferenceCode)) {
			Group group = _groupLocalService.fetchGroupByExternalReferenceCode(
				scopeExternalReferenceCode, cmsMigrationContext.getCompanyId());

			if (group != null) {
				groupId = group.getGroupId();
			}
		}

		if (sourceClassName.equals(BlogsEntry.class.getName())) {
			BlogsEntry blogsEntry =
				_blogsEntryLocalService.fetchBlogsEntryByExternalReferenceCode(
					externalReferenceCode, groupId);

			if (blogsEntry != null) {
				return blogsEntry.getEntryId();
			}
		}
		else if (sourceClassName.equals(DLFileEntry.class.getName())) {
			DLFileEntry dlFileEntry =
				_dlFileEntryLocalService.
					fetchDLFileEntryByExternalReferenceCode(
						externalReferenceCode, groupId);

			if (dlFileEntry != null) {
				return dlFileEntry.getFileEntryId();
			}
		}
		else if (sourceClassName.equals(JournalArticle.class.getName())) {
			JournalArticle journalArticle =
				_journalArticleLocalService.
					fetchLatestArticleByExternalReferenceCode(
						groupId, externalReferenceCode);

			if (journalArticle != null) {
				return journalArticle.getResourcePrimKey();
			}
		}

		return 0;
	}

	public DDMStructure getDDMStructure(String sourceClassName, long classPK) {
		if (!Objects.equals(sourceClassName, JournalArticle.class.getName())) {
			return null;
		}

		JournalArticle journalArticle = _fetchJournalArticle(classPK);

		if (journalArticle == null) {
			return null;
		}

		return journalArticle.getDDMStructure();
	}

	/**
	 * Returns the info field ID of the object definition that corresponds to
	 * a classic info field ID, or <code>null</code> when the field has no
	 * counterpart.
	 *
	 * @param link whether the field feeds a link, which makes attachments
	 *        resolve to their download URL instead of their file URL
	 */
	public String getFieldId(
		CMSMigrationContext cmsMigrationContext, String sourceClassName,
		DDMStructure ddmStructure, ObjectDefinition objectDefinition,
		String fieldId, boolean link) {

		if (Validator.isNull(fieldId) || (objectDefinition == null)) {
			return null;
		}

		if (fieldId.startsWith("ddmTemplate__ddmTemplate_") ||
			fieldId.startsWith("ObjectEntry_") ||
			fieldId.startsWith("ObjectField_")) {

			return fieldId;
		}

		if (fieldId.startsWith("DDMStructure_")) {
			return _getDDMStructureFieldId(
				ddmStructure, objectDefinition,
				fieldId.substring("DDMStructure_".length()), link);
		}

		String templateKey = _getTemplateKey(fieldId);

		if (templateKey != null) {
			long templateEntryId = _getTemplateEntryId(
				cmsMigrationContext, ddmStructure, templateKey);

			if (templateEntryId == 0) {
				return null;
			}

			return "ddmTemplate__ddmTemplate_" + templateEntryId;
		}

		String name = fieldId;

		int index = name.indexOf(StringPool.UNDERLINE);

		if ((index > 0) && Character.isUpperCase(name.charAt(0))) {
			name = name.substring(index + 1);
		}

		String objectEntryFieldName = _objectEntryFieldNames.get(name);

		if (objectEntryFieldName != null) {
			return "ObjectEntry_" + objectEntryFieldName;
		}

		if (Objects.equals(sourceClassName, DLFileEntry.class.getName())) {
			String fileFieldSuffix = _fileFieldSuffixes.get(name);

			if (fileFieldSuffix != null) {
				return _getAttachmentFieldId(
					objectDefinition,
					CMSMigrationConstants.OBJECT_FIELD_NAME_FILE,
					fileFieldSuffix);
			}
		}

		if (name.equals("coverImage")) {
			return _getAttachmentFieldId(
				objectDefinition,
				CMSMigrationConstants.OBJECT_FIELD_NAME_COVER_IMAGE,
				link ? "#downloadURL" : "#fileURL");
		}

		if (name.equals("previewImage") || name.equals("smallImage")) {
			return _getAttachmentFieldId(
				objectDefinition,
				CMSMigrationConstants.OBJECT_FIELD_NAME_SMALL_IMAGE,
				link ? "#downloadURL" : "#fileURL");
		}

		if (name.equals("content") || name.equals("coverImageCaption") ||
			name.equals("description") || name.equals("subtitle") ||
			name.equals("title")) {

			ObjectField objectField = _objectFieldLocalService.fetchObjectField(
				objectDefinition.getObjectDefinitionId(), name);

			if (objectField != null) {
				return "ObjectField_" + name;
			}
		}

		return null;
	}

	public ObjectDefinition getObjectDefinition(ObjectEntry objectEntry) {
		return _objectDefinitionLocalService.fetchObjectDefinition(
			objectEntry.getObjectDefinitionId());
	}

	public ObjectEntry getObjectEntry(
		CMSMigrationContext cmsMigrationContext, String sourceClassName,
		long classPK) {

		if (sourceClassName == null) {
			return null;
		}

		ObjectEntry objectEntry = cmsMigrationContext.getObjectEntry(
			sourceClassName, classPK);

		if ((objectEntry != null) ||
			!Objects.equals(sourceClassName, JournalArticle.class.getName())) {

			return objectEntry;
		}

		JournalArticle journalArticle = _fetchJournalArticle(classPK);

		if (journalArticle == null) {
			return null;
		}

		return cmsMigrationContext.getObjectEntry(
			sourceClassName, journalArticle.getResourcePrimKey());
	}

	/**
	 * Returns the renderer key and the template key that render a migrated
	 * entry the way the classic renderer rendered the classic item. The
	 * returned array is empty when no counterpart exists.
	 */
	public String[] getRendererKeys(
		CMSMigrationContext cmsMigrationContext, DDMStructure ddmStructure,
		String defaultDDMTemplateKey, String infoItemRendererKey,
		String templateKey) {

		if (Validator.isNull(infoItemRendererKey)) {
			return new String[0];
		}

		String rendererName = infoItemRendererKey.substring(
			infoItemRendererKey.lastIndexOf(StringPool.PERIOD) + 1);

		if (rendererName.equals(
				"JournalArticleDDMTemplateInfoItemTemplatedRenderer") ||
			rendererName.equals("JournalArticleFullContentInfoItemRenderer")) {

			if (Validator.isNull(templateKey)) {
				templateKey = defaultDDMTemplateKey;
			}

			long templateEntryId = _getTemplateEntryId(
				cmsMigrationContext, ddmStructure, templateKey);

			if (templateEntryId == 0) {
				return new String[0];
			}

			return new String[] {
				TEMPLATE_INFO_ITEM_RENDERER_KEY, String.valueOf(templateEntryId)
			};
		}

		String style = _rendererStyles.get(rendererName);

		if (style == null) {
			return new String[0];
		}

		return new String[] {
			CMSMigrationInfoItemRenderer.getKey(style), StringPool.BLANK
		};
	}

	public String getSourceClassName(long classNameId) {
		if (classNameId <= 0) {
			return null;
		}

		return getSourceClassName(_portal.getClassName(classNameId));
	}

	/**
	 * Returns the class name that the migration context uses for a classic
	 * class name, or <code>null</code> when the class name is not migrated.
	 */
	public String getSourceClassName(String className) {
		if (Objects.equals(className, BlogsEntry.class.getName())) {
			return BlogsEntry.class.getName();
		}
		else if (Objects.equals(className, DLFileEntry.class.getName()) ||
				 Objects.equals(className, FileEntry.class.getName())) {

			return DLFileEntry.class.getName();
		}
		else if (Objects.equals(className, JournalArticle.class.getName())) {
			return JournalArticle.class.getName();
		}

		return null;
	}

	public long getTemplateEntryId(
		CMSMigrationContext cmsMigrationContext, DDMStructure ddmStructure,
		String templateKey) {

		return _getTemplateEntryId(
			cmsMigrationContext, ddmStructure, templateKey);
	}

	private JournalArticle _fetchJournalArticle(long classPK) {
		JournalArticle journalArticle =
			_journalArticleLocalService.fetchLatestArticle(classPK);

		if (journalArticle == null) {
			journalArticle = _journalArticleLocalService.fetchArticle(classPK);
		}

		return journalArticle;
	}

	private String _getAttachmentFieldId(
		ObjectDefinition objectDefinition, String objectFieldName,
		String suffix) {

		ObjectField objectField = _objectFieldLocalService.fetchObjectField(
			objectDefinition.getObjectDefinitionId(), objectFieldName);

		if (objectField == null) {
			return null;
		}

		return "ObjectField_" + objectField.getObjectFieldId() + suffix;
	}

	private String _getDDMStructureFieldId(
		DDMStructure ddmStructure, ObjectDefinition objectDefinition,
		String fieldName, boolean link) {

		if (ddmStructure == null) {
			return null;
		}

		DDMFormField ddmFormField = null;

		try {
			ddmFormField = ddmStructure.getDDMFormField(fieldName);
		}
		catch (Exception exception) {
			if (_log.isDebugEnabled()) {
				_log.debug(exception);
			}

			return null;
		}

		if (ddmFormField == null) {
			return null;
		}

		String objectFieldName =
			CMSMigrationDDMFormFieldUtil.getObjectFieldName(ddmFormField);

		ObjectField objectField = _objectFieldLocalService.fetchObjectField(
			objectDefinition.getObjectDefinitionId(), objectFieldName);

		if (objectField == null) {
			return null;
		}

		if (objectField.compareBusinessType(
				ObjectFieldConstants.BUSINESS_TYPE_ATTACHMENT)) {

			return "ObjectField_" + objectField.getObjectFieldId() +
				(link ? "#downloadURL" : "#fileURL");
		}

		return "ObjectField_" + objectFieldName;
	}

	private long _getTemplateEntryId(
		CMSMigrationContext cmsMigrationContext, DDMStructure ddmStructure,
		String templateKey) {

		if ((ddmStructure == null) || Validator.isNull(templateKey)) {
			return 0;
		}

		long templateEntryId = cmsMigrationContext.getTemplateEntryId(
			ddmStructure.getStructureId(), templateKey);

		if (templateEntryId > 0) {
			return templateEntryId;
		}

		for (DDMTemplate ddmTemplate : ddmStructure.getTemplates()) {
			if (Objects.equals(
					_normalizeTemplateKey(ddmTemplate.getTemplateKey()),
					_normalizeTemplateKey(templateKey))) {

				return cmsMigrationContext.getTemplateEntryId(
					ddmStructure.getStructureId(),
					ddmTemplate.getTemplateKey());
			}
		}

		return 0;
	}

	private String _getTemplateKey(String fieldId) {
		String name = fieldId;

		if (name.startsWith(StringPool.UNDERLINE)) {
			name = name.substring(1);
		}

		if (!name.startsWith("ddmTemplate_") ||
			name.startsWith("ddmTemplate__ddmTemplate_")) {

			return null;
		}

		return name.substring("ddmTemplate_".length());
	}

	private String _normalizeTemplateKey(String templateKey) {
		return templateKey.replaceAll("\\W", StringPool.UNDERLINE);
	}

	private static final Log _log = LogFactoryUtil.getLog(
		InfoItemReferenceMapper.class);

	private static final Snapshot<BlogsEntryLocalService>
		_blogsEntryLocalServiceSnapshot = new Snapshot<>(
			InfoItemReferenceMapper.class, BlogsEntryLocalService.class);
	private static final Snapshot<DLFileEntryLocalService>
		_dlFileEntryLocalServiceSnapshot = new Snapshot<>(
			InfoItemReferenceMapper.class, DLFileEntryLocalService.class);
	private static final Map<String, String> _fileFieldSuffixes =
		HashMapBuilder.put(
			"downloadURL", "#downloadURL"
		).put(
			"fileName", "#fileName"
		).put(
			"fileURL", "#fileURL"
		).put(
			"mimeType", "#mimeType"
		).put(
			"previewImage", "#previewURL"
		).put(
			"previewURL", "#previewURL"
		).put(
			"size", "#size"
		).build();
	private static final Snapshot<GroupLocalService>
		_groupLocalServiceSnapshot = new Snapshot<>(
			InfoItemReferenceMapper.class, GroupLocalService.class);
	private static final Snapshot<JournalArticleLocalService>
		_journalArticleLocalServiceSnapshot = new Snapshot<>(
			InfoItemReferenceMapper.class, JournalArticleLocalService.class);
	private static final Snapshot<ObjectDefinitionLocalService>
		_objectDefinitionLocalServiceSnapshot = new Snapshot<>(
			InfoItemReferenceMapper.class, ObjectDefinitionLocalService.class);
	private static final Map<String, String> _objectEntryFieldNames =
		HashMapBuilder.put(
			"authorName", "author"
		).put(
			"authorProfileImage", "userProfileImage"
		).put(
			"createDate", "createDate"
		).put(
			"displayDate", "displayDate"
		).put(
			"displayPageURL", "displayPageURL"
		).put(
			"expirationDate", "expirationDate"
		).put(
			"lastEditorName", "author"
		).put(
			"lastEditorProfileImage", "userProfileImage"
		).put(
			"modifiedDate", "modifiedDate"
		).put(
			"publishDate", "publishDate"
		).build();
	private static final Snapshot<ObjectFieldLocalService>
		_objectFieldLocalServiceSnapshot = new Snapshot<>(
			InfoItemReferenceMapper.class, ObjectFieldLocalService.class);
	private static final Snapshot<Portal> _portalSnapshot = new Snapshot<>(
		InfoItemReferenceMapper.class, Portal.class);
	private static final Map<String, String> _rendererStyles =
		HashMapBuilder.put(
			"BlogsEntryAbstractInfoItemRenderer",
			CMSMigrationInfoItemRenderer.STYLE_ABSTRACT
		).put(
			"BlogsEntryFullContentInfoItemRenderer",
			CMSMigrationInfoItemRenderer.STYLE_BLOGS_FULL_CONTENT
		).put(
			"FileEntryAbstractInfoItemRenderer",
			CMSMigrationInfoItemRenderer.STYLE_FILE_ABSTRACT
		).put(
			"FileEntryTitleInfoItemRenderer",
			CMSMigrationInfoItemRenderer.STYLE_FILE_TITLE
		).put(
			"JournalArticleAbstractInfoItemRenderer",
			CMSMigrationInfoItemRenderer.STYLE_ABSTRACT
		).put(
			"JournalArticleTitleInfoItemRenderer",
			CMSMigrationInfoItemRenderer.STYLE_TITLE
		).build();

	private final BlogsEntryLocalService _blogsEntryLocalService =
		_blogsEntryLocalServiceSnapshot.get();
	private final DLFileEntryLocalService _dlFileEntryLocalService =
		_dlFileEntryLocalServiceSnapshot.get();
	private final GroupLocalService _groupLocalService =
		_groupLocalServiceSnapshot.get();
	private final JournalArticleLocalService _journalArticleLocalService =
		_journalArticleLocalServiceSnapshot.get();
	private final ObjectDefinitionLocalService _objectDefinitionLocalService =
		_objectDefinitionLocalServiceSnapshot.get();
	private final ObjectFieldLocalService _objectFieldLocalService =
		_objectFieldLocalServiceSnapshot.get();
	private final Portal _portal = _portalSnapshot.get();

}