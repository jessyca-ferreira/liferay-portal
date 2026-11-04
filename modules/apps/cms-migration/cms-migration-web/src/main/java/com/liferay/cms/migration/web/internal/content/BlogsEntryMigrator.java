/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.content;

import com.liferay.blogs.model.BlogsEntry;
import com.liferay.blogs.service.BlogsEntryLocalService;
import com.liferay.cms.migration.web.internal.constants.CMSMigrationConstants;
import com.liferay.cms.migration.web.internal.migration.CMSMigrationContext;
import com.liferay.cms.migration.web.internal.reference.HTMLReferenceRewriter;
import com.liferay.cms.migration.web.internal.structure.ObjectDefinitionMigrator;
import com.liferay.journal.constants.JournalFolderConstants;
import com.liferay.journal.model.JournalFolder;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.dao.orm.QueryDefinition;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.reading.time.calculator.ReadingTimeCalculator;
import com.liferay.reading.time.model.ReadingTimeEntry;
import com.liferay.reading.time.service.ReadingTimeEntryLocalService;

import java.io.Serializable;

import java.time.Duration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Migrates the blog entries of a site to the CMS migration blog object
 * definition, keeping the cover image, the small image, the friendly URL, and
 * the reading time.
 *
 * @author Lino Alves
 */
public class BlogsEntryMigrator {

	public BlogsEntryMigrator(
		AttachmentCopier attachmentCopier,
		BlogsEntryLocalService blogsEntryLocalService,
		HTMLReferenceRewriter htmlReferenceRewriter,
		ObjectDefinitionMigrator objectDefinitionMigrator,
		ObjectEntryWriter objectEntryWriter, Portal portal,
		ReadingTimeCalculator readingTimeCalculator,
		ReadingTimeEntryLocalService readingTimeEntryLocalService) {

		_attachmentCopier = attachmentCopier;
		_blogsEntryLocalService = blogsEntryLocalService;
		_htmlReferenceRewriter = htmlReferenceRewriter;
		_objectDefinitionMigrator = objectDefinitionMigrator;
		_objectEntryWriter = objectEntryWriter;
		_portal = portal;
		_readingTimeCalculator = readingTimeCalculator;
		_readingTimeEntryLocalService = readingTimeEntryLocalService;
	}

	public List<BlogsEntry> getBlogsEntries(
		CMSMigrationContext cmsMigrationContext) {

		return _blogsEntryLocalService.getGroupEntries(
			cmsMigrationContext.getSourceGroupId(),
			new QueryDefinition<>(
				WorkflowConstants.STATUS_ANY, QueryUtil.ALL_POS,
				QueryUtil.ALL_POS, null));
	}

	public ObjectEntry migrate(
			CMSMigrationContext cmsMigrationContext, BlogsEntry blogsEntry)
		throws PortalException {

		ObjectEntry objectEntry = cmsMigrationContext.getObjectEntry(
			BlogsEntry.class.getName(), blogsEntry.getEntryId());

		if ((objectEntry != null) || blogsEntry.isInTrash()) {
			return objectEntry;
		}

		ObjectDefinition objectDefinition =
			_objectDefinitionMigrator.getOrAddBlogsEntryObjectDefinition(
				cmsMigrationContext);

		String externalReferenceCode =
			CMSMigrationConstants.EXTERNAL_REFERENCE_CODE_PREFIX_BLOGS_ENTRY +
				blogsEntry.getEntryId();
		String languageId = LocaleUtil.toLanguageId(
			LocaleUtil.getSiteDefault());

		Map<String, Serializable> values = new HashMap<>();

		_putLocalizedValue(
			CMSMigrationConstants.OBJECT_FIELD_NAME_CONTENT, languageId,
			_htmlReferenceRewriter.rewriteFileReferences(
				cmsMigrationContext, blogsEntry.getContent()),
			values);
		_putLocalizedValue(
			CMSMigrationConstants.OBJECT_FIELD_NAME_COVER_IMAGE_CAPTION,
			languageId, blogsEntry.getCoverImageCaption(), values);
		_putLocalizedValue(
			CMSMigrationConstants.OBJECT_FIELD_NAME_DESCRIPTION, languageId,
			blogsEntry.getDescription(), values);
		_putLocalizedValue(
			CMSMigrationConstants.OBJECT_FIELD_NAME_SUBTITLE, languageId,
			blogsEntry.getSubtitle(), values);
		_putLocalizedValue(
			CMSMigrationConstants.OBJECT_FIELD_NAME_TITLE, languageId,
			LocalizedValuesUtil.truncate(
				blogsEntry.getTitle(), _TITLE_MAX_LENGTH),
			values);

		long coverImageFileEntryId = _attachmentCopier.copy(
			cmsMigrationContext, objectDefinition,
			CMSMigrationConstants.OBJECT_FIELD_NAME_COVER_IMAGE,
			externalReferenceCode + "_COVER_IMAGE",
			blogsEntry.getCoverImageFileEntryId());

		if (coverImageFileEntryId > 0) {
			values.put(
				CMSMigrationConstants.OBJECT_FIELD_NAME_COVER_IMAGE,
				coverImageFileEntryId);
		}

		if (blogsEntry.isSmallImage()) {
			long smallImageFileEntryId = _attachmentCopier.copy(
				cmsMigrationContext, objectDefinition,
				CMSMigrationConstants.OBJECT_FIELD_NAME_SMALL_IMAGE,
				externalReferenceCode + "_SMALL_IMAGE",
				blogsEntry.getSmallImageFileEntryId());

			if (smallImageFileEntryId > 0) {
				values.put(
					CMSMigrationConstants.OBJECT_FIELD_NAME_SMALL_IMAGE,
					smallImageFileEntryId);
			}
			else if (Validator.isNotNull(blogsEntry.getSmallImageURL())) {
				values.put(
					CMSMigrationConstants.OBJECT_FIELD_NAME_SMALL_IMAGE_URL,
					blogsEntry.getSmallImageURL());
			}
		}

		if (blogsEntry.getDisplayDate() != null) {
			values.put("displayDate", blogsEntry.getDisplayDate());
		}

		objectEntry = _objectEntryWriter.write(
			cmsMigrationContext, BlogsEntry.class.getName(),
			blogsEntry.getEntryId(), objectDefinition,
			cmsMigrationContext.getObjectEntryFolderId(
				JournalFolder.class.getName(),
				JournalFolderConstants.DEFAULT_PARENT_FOLDER_ID),
			externalReferenceCode, blogsEntry.getUserId(), languageId, values,
			HashMapBuilder.put(
				languageId, blogsEntry.getUrlTitle()
			).build(),
			blogsEntry.getCreateDate(), blogsEntry.getModifiedDate(),
			blogsEntry.isApproved());

		_updateReadingTimeEntry(blogsEntry, objectEntry);

		cmsMigrationContext.getCMSMigrationReport(
		).increment(
			CMSMigrationConstants.PHASE_BLOGS_ENTRIES
		);

		return objectEntry;
	}

	public void migrateAll(CMSMigrationContext cmsMigrationContext) {
		for (BlogsEntry blogsEntry : getBlogsEntries(cmsMigrationContext)) {
			cmsMigrationContext.sendStatus(blogsEntry.getTitle());

			try {
				migrate(cmsMigrationContext, blogsEntry);
			}
			catch (Exception exception) {
				cmsMigrationContext.getCMSMigrationReport(
				).addError(
					StringBundler.concat(
						"Unable to migrate the blog entry ",
						blogsEntry.getTitle(), ": ", exception.getMessage())
				);
			}
		}
	}

	private void _putLocalizedValue(
		String name, String languageId, String value,
		Map<String, Serializable> values) {

		if (Validator.isNull(value)) {
			return;
		}

		values.put(
			name + "_i18n",
			HashMapBuilder.put(
				languageId, value
			).build());
	}

	private void _updateReadingTimeEntry(
		BlogsEntry blogsEntry, ObjectEntry objectEntry) {

		Duration readingTimeDuration;

		ReadingTimeEntry readingTimeEntry =
			_readingTimeEntryLocalService.fetchReadingTimeEntry(blogsEntry);

		if (readingTimeEntry != null) {
			readingTimeDuration = Duration.ofMillis(
				readingTimeEntry.getReadingTime());
		}
		else {
			readingTimeDuration = _readingTimeCalculator.calculate(blogsEntry);
		}

		if (readingTimeDuration == null) {
			return;
		}

		_readingTimeEntryLocalService.updateReadingTimeEntry(
			objectEntry.getGroupId(), _portal.getClassNameId(ObjectEntry.class),
			objectEntry.getObjectEntryId(), readingTimeDuration);
	}

	private static final int _TITLE_MAX_LENGTH = 280;

	private final AttachmentCopier _attachmentCopier;
	private final BlogsEntryLocalService _blogsEntryLocalService;
	private final HTMLReferenceRewriter _htmlReferenceRewriter;
	private final ObjectDefinitionMigrator _objectDefinitionMigrator;
	private final ObjectEntryWriter _objectEntryWriter;
	private final Portal _portal;
	private final ReadingTimeCalculator _readingTimeCalculator;
	private final ReadingTimeEntryLocalService _readingTimeEntryLocalService;

}