/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.content;

import com.liferay.cms.migration.web.internal.migration.CMSMigrationContext;
import com.liferay.document.library.kernel.model.DLVersionNumberIncrease;
import com.liferay.document.library.kernel.service.DLAppLocalService;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.util.Validator;

/**
 * @author Albert Gomes Cabral
 */
public class AttachmentAlternativeTextWriter {

	public AttachmentAlternativeTextWriter(
		DLAppLocalService dlAppLocalService) {

		_dlAppLocalService = dlAppLocalService;
	}

	public void write(
		CMSMigrationContext cmsMigrationContext, FileEntry fileEntry,
		String alternativeText) {

		if (Validator.isNull(alternativeText) ||
			Validator.isNotNull(fileEntry.getDescription())) {

			return;
		}

		try {
			_dlAppLocalService.updateFileEntry(
				cmsMigrationContext.getUserId(), fileEntry.getFileEntryId(),
				null, null, fileEntry.getTitle(), null, alternativeText, null,
				DLVersionNumberIncrease.NONE, (byte[])null, null, null, null,
				cmsMigrationContext.getServiceContext());
		}
		catch (PortalException portalException) {
			cmsMigrationContext.getCMSMigrationReport(
			).addWarning(
				StringBundler.concat(
					"Unable to set the alternative text of the attachment ",
					fileEntry.getTitle(), ": ", portalException.getMessage())
			);
		}
	}

	private final DLAppLocalService _dlAppLocalService;

}