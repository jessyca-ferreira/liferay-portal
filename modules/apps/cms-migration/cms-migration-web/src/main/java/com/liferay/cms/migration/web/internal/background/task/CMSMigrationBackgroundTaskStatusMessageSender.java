/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.background.task;

import com.liferay.portal.kernel.backgroundtask.BackgroundTaskStatusMessageSender;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskThreadLocal;
import com.liferay.portal.kernel.backgroundtask.constants.BackgroundTaskConstants;
import com.liferay.portal.kernel.messaging.Message;

/**
 * @author Lino Alves
 */
public class CMSMigrationBackgroundTaskStatusMessageSender {

	public CMSMigrationBackgroundTaskStatusMessageSender(
		BackgroundTaskStatusMessageSender backgroundTaskStatusMessageSender) {

		_backgroundTaskStatusMessageSender = backgroundTaskStatusMessageSender;
	}

	public void sendItemStatusMessage(String itemTitle) {
		Message message = _createMessage("item");

		message.put("itemTitle", itemTitle);

		_backgroundTaskStatusMessageSender.sendBackgroundTaskStatusMessage(
			message);
	}

	public void sendPhaseStatusMessage(String phase, long total) {
		Message message = _createMessage("phase");

		message.put("phase", phase);
		message.put("total", total);

		_backgroundTaskStatusMessageSender.sendBackgroundTaskStatusMessage(
			message);
	}

	private Message _createMessage(String messageType) {
		Message message = new Message();

		message.put(
			BackgroundTaskConstants.MESSAGE_KEY_BACKGROUND_TASK_ID,
			BackgroundTaskThreadLocal.getBackgroundTaskId());
		message.put("messageType", messageType);

		return message;
	}

	private final BackgroundTaskStatusMessageSender
		_backgroundTaskStatusMessageSender;

}