/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.content;

import com.liferay.cms.migration.web.internal.constants.CMSMigrationConstants;
import com.liferay.cms.migration.web.internal.migration.CMSMigrationContext;
import com.liferay.document.library.kernel.model.DLFolder;
import com.liferay.document.library.kernel.model.DLFolderConstants;
import com.liferay.document.library.kernel.service.DLFolderLocalService;
import com.liferay.journal.constants.JournalFolderConstants;
import com.liferay.journal.model.JournalFolder;
import com.liferay.journal.service.JournalFolderLocalService;
import com.liferay.object.constants.ObjectEntryFolderConstants;
import com.liferay.object.model.ObjectEntryFolder;
import com.liferay.object.service.ObjectEntryFolderLocalService;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.model.ModelHintsUtil;
import com.liferay.portal.kernel.module.service.Snapshot;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.LocaleUtil;

/**
 * Recreates the Web Content and Documents and Media folder trees as object
 * entry folders under the space's Contents and Files root folders.
 *
 * @author Lino Alves
 */
public class FolderMigrator {

	public void migrateDLFolders(CMSMigrationContext cmsMigrationContext)
		throws PortalException {

		ObjectEntryFolder objectEntryFolder = _getRootObjectEntryFolder(
			cmsMigrationContext,
			ObjectEntryFolderConstants.EXTERNAL_REFERENCE_CODE_FILES);

		cmsMigrationContext.addFolderId(
			DLFolder.class.getName(),
			DLFolderConstants.DEFAULT_PARENT_FOLDER_ID,
			objectEntryFolder.getObjectEntryFolderId());

		_migrateDLFolders(
			cmsMigrationContext, DLFolderConstants.DEFAULT_PARENT_FOLDER_ID,
			objectEntryFolder.getObjectEntryFolderId());
	}

	public void migrateJournalFolders(CMSMigrationContext cmsMigrationContext)
		throws PortalException {

		ObjectEntryFolder objectEntryFolder = _getRootObjectEntryFolder(
			cmsMigrationContext,
			ObjectEntryFolderConstants.EXTERNAL_REFERENCE_CODE_CONTENTS);

		cmsMigrationContext.addFolderId(
			JournalFolder.class.getName(),
			JournalFolderConstants.DEFAULT_PARENT_FOLDER_ID,
			objectEntryFolder.getObjectEntryFolderId());

		_migrateJournalFolders(
			cmsMigrationContext,
			JournalFolderConstants.DEFAULT_PARENT_FOLDER_ID,
			objectEntryFolder.getObjectEntryFolderId());
	}

	public void registerDLRootFolder(CMSMigrationContext cmsMigrationContext)
		throws PortalException {

		ObjectEntryFolder objectEntryFolder = _getRootObjectEntryFolder(
			cmsMigrationContext,
			ObjectEntryFolderConstants.EXTERNAL_REFERENCE_CODE_FILES);

		cmsMigrationContext.addFolderId(
			DLFolder.class.getName(),
			DLFolderConstants.DEFAULT_PARENT_FOLDER_ID,
			objectEntryFolder.getObjectEntryFolderId());
	}

	private ObjectEntryFolder _getOrAddObjectEntryFolder(
			CMSMigrationContext cmsMigrationContext, String description,
			String externalReferenceCode, long folderId, String name,
			long parentObjectEntryFolderId)
		throws PortalException {

		ObjectEntryFolder objectEntryFolder =
			_objectEntryFolderLocalService.
				fetchObjectEntryFolderByExternalReferenceCode(
					externalReferenceCode,
					cmsMigrationContext.getDepotGroupId(),
					cmsMigrationContext.getCompanyId());

		if (objectEntryFolder != null) {
			return objectEntryFolder;
		}

		cmsMigrationContext.getCMSMigrationReport(
		).increment(
			CMSMigrationConstants.PHASE_FOLDERS
		);

		return _objectEntryFolderLocalService.addObjectEntryFolder(
			externalReferenceCode, cmsMigrationContext.getDepotGroupId(),
			cmsMigrationContext.getUserId(), parentObjectEntryFolderId,
			description,
			HashMapBuilder.put(
				LocaleUtil.getSiteDefault(), name
			).build(),
			_truncateName(cmsMigrationContext, folderId, name),
			cmsMigrationContext.getServiceContext());
	}

	private ObjectEntryFolder _getRootObjectEntryFolder(
			CMSMigrationContext cmsMigrationContext,
			String externalReferenceCode)
		throws PortalException {

		return _objectEntryFolderLocalService.
			getObjectEntryFolderByExternalReferenceCode(
				externalReferenceCode, cmsMigrationContext.getDepotGroupId(),
				cmsMigrationContext.getCompanyId());
	}

	private void _migrateDLFolders(
			CMSMigrationContext cmsMigrationContext, long parentFolderId,
			long parentObjectEntryFolderId)
		throws PortalException {

		for (DLFolder dlFolder :
				_dlFolderLocalService.getFolders(
					cmsMigrationContext.getSourceGroupId(), parentFolderId,
					false)) {

			if (dlFolder.isHidden()) {
				continue;
			}

			ObjectEntryFolder objectEntryFolder = _getOrAddObjectEntryFolder(
				cmsMigrationContext, dlFolder.getDescription(),
				CMSMigrationConstants.EXTERNAL_REFERENCE_CODE_PREFIX_DL_FOLDER +
					dlFolder.getFolderId(),
				dlFolder.getFolderId(), dlFolder.getName(),
				parentObjectEntryFolderId);

			cmsMigrationContext.addFolderId(
				DLFolder.class.getName(), dlFolder.getFolderId(),
				objectEntryFolder.getObjectEntryFolderId());

			_migrateDLFolders(
				cmsMigrationContext, dlFolder.getFolderId(),
				objectEntryFolder.getObjectEntryFolderId());
		}
	}

	private void _migrateJournalFolders(
			CMSMigrationContext cmsMigrationContext, long parentFolderId,
			long parentObjectEntryFolderId)
		throws PortalException {

		for (JournalFolder journalFolder :
				_journalFolderLocalService.getFolders(
					cmsMigrationContext.getSourceGroupId(), parentFolderId)) {

			ObjectEntryFolder objectEntryFolder = _getOrAddObjectEntryFolder(
				cmsMigrationContext, journalFolder.getDescription(),
				CMSMigrationConstants.
					EXTERNAL_REFERENCE_CODE_PREFIX_JOURNAL_FOLDER +
						journalFolder.getFolderId(),
				journalFolder.getFolderId(), journalFolder.getName(),
				parentObjectEntryFolderId);

			cmsMigrationContext.addFolderId(
				JournalFolder.class.getName(), journalFolder.getFolderId(),
				objectEntryFolder.getObjectEntryFolderId());

			_migrateJournalFolders(
				cmsMigrationContext, journalFolder.getFolderId(),
				objectEntryFolder.getObjectEntryFolderId());
		}
	}

	private String _truncateName(
		CMSMigrationContext cmsMigrationContext, long folderId, String name) {

		int maxLength = ModelHintsUtil.getMaxLength(
			ObjectEntryFolder.class.getName(), "name");

		if (name.length() <= maxLength) {
			return name;
		}

		cmsMigrationContext.getCMSMigrationReport(
		).addWarning(
			"Truncated the name of the folder " + name
		);

		String suffix = StringPool.DASH + folderId;

		return name.substring(0, maxLength - suffix.length()) + suffix;
	}

	private static final Snapshot<DLFolderLocalService>
		_dlFolderLocalServiceSnapshot = new Snapshot<>(
			FolderMigrator.class, DLFolderLocalService.class);
	private static final Snapshot<JournalFolderLocalService>
		_journalFolderLocalServiceSnapshot = new Snapshot<>(
			FolderMigrator.class, JournalFolderLocalService.class);
	private static final Snapshot<ObjectEntryFolderLocalService>
		_objectEntryFolderLocalServiceSnapshot = new Snapshot<>(
			FolderMigrator.class, ObjectEntryFolderLocalService.class);

	private final DLFolderLocalService _dlFolderLocalService =
		_dlFolderLocalServiceSnapshot.get();
	private final JournalFolderLocalService _journalFolderLocalService =
		_journalFolderLocalServiceSnapshot.get();
	private final ObjectEntryFolderLocalService _objectEntryFolderLocalService =
		_objectEntryFolderLocalServiceSnapshot.get();

}