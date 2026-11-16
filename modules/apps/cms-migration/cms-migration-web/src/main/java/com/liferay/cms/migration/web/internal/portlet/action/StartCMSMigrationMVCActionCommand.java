/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.portlet.action;

import com.liferay.cms.migration.web.internal.background.task.CMSMigrationBackgroundTaskExecutor;
import com.liferay.cms.migration.web.internal.constants.CMSMigrationPortletKeys;
import com.liferay.depot.constants.DepotConstants;
import com.liferay.depot.exception.DepotEntryNameException;
import com.liferay.depot.model.DepotEntry;
import com.liferay.depot.service.DepotEntryLocalService;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskManager;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.portlet.bridges.mvc.BaseMVCActionCommand;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCActionCommand;
import com.liferay.portal.kernel.security.auth.PrincipalException;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.ServiceContextFactory;
import com.liferay.portal.kernel.servlet.SessionErrors;
import com.liferay.portal.kernel.servlet.SessionMessages;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.util.WebKeys;

import jakarta.portlet.ActionRequest;
import jakarta.portlet.ActionResponse;

import java.io.Serializable;

import java.util.Collections;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Lino Alves
 */
@Component(
	property = {
		"jakarta.portlet.name=" + CMSMigrationPortletKeys.CMS_MIGRATION,
		"mvc.command.name=/cms_migration/start_cms_migration"
	},
	service = MVCActionCommand.class
)
public class StartCMSMigrationMVCActionCommand extends BaseMVCActionCommand {

	@Override
	protected void doProcessAction(
			ActionRequest actionRequest, ActionResponse actionResponse)
		throws Exception {

		ThemeDisplay themeDisplay = (ThemeDisplay)actionRequest.getAttribute(
			WebKeys.THEME_DISPLAY);

		PermissionChecker permissionChecker =
			themeDisplay.getPermissionChecker();

		if (!permissionChecker.isCompanyAdmin()) {
			throw new PrincipalException.MustBeCompanyAdmin(
				permissionChecker.getUserId());
		}

		try {
			Group sourceGroup = _groupLocalService.getGroup(
				ParamUtil.getLong(actionRequest, "sourceGroupId"));

			DepotEntry depotEntry = _getDepotEntry(actionRequest);

			_backgroundTaskManager.addBackgroundTask(
				themeDisplay.getUserId(), themeDisplay.getCompanyGroupId(),
				sourceGroup.getDescriptiveName(themeDisplay.getLocale()),
				CMSMigrationBackgroundTaskExecutor.class.getName(),
				HashMapBuilder.<String, Serializable>put(
					"depotEntryId", depotEntry.getDepotEntryId()
				).put(
					"migrateBlogsEntries",
					ParamUtil.getBoolean(actionRequest, "migrateBlogsEntries")
				).put(
					"migrateFileEntries",
					ParamUtil.getBoolean(actionRequest, "migrateFileEntries")
				).put(
					"migrateJournalArticles",
					ParamUtil.getBoolean(
						actionRequest, "migrateJournalArticles")
				).put(
					"migrateRepeatableFields",
					ParamUtil.getBoolean(
						actionRequest, "migrateRepeatableFields")
				).put(
					"rewritePages",
					ParamUtil.getBoolean(actionRequest, "rewritePages")
				).put(
					"sourceGroupId", sourceGroup.getGroupId()
				).build(),
				ServiceContextFactory.getInstance(actionRequest));

			SessionMessages.add(actionRequest, "migrationStarted");
		}
		catch (Exception exception) {
			SessionErrors.add(actionRequest, exception.getClass(), exception);

			hideDefaultErrorMessage(actionRequest);
		}
	}

	private DepotEntry _getDepotEntry(ActionRequest actionRequest)
		throws Exception {

		long depotEntryId = ParamUtil.getLong(actionRequest, "depotEntryId");

		if (depotEntryId > 0) {
			return _depotEntryLocalService.getDepotEntry(depotEntryId);
		}

		String spaceName = ParamUtil.getString(actionRequest, "spaceName");

		if (Validator.isNull(spaceName)) {
			throw new DepotEntryNameException();
		}

		ServiceContext serviceContext = ServiceContextFactory.getInstance(
			DepotEntry.class.getName(), actionRequest);

		return _depotEntryLocalService.addDepotEntry(
			Collections.singletonMap(LocaleUtil.getDefault(), spaceName),
			Collections.singletonMap(LocaleUtil.getDefault(), StringPool.BLANK),
			DepotConstants.TYPE_SPACE, serviceContext);
	}

	@Reference
	private BackgroundTaskManager _backgroundTaskManager;

	@Reference
	private DepotEntryLocalService _depotEntryLocalService;

	@Reference
	private GroupLocalService _groupLocalService;

}