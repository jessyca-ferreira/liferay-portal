/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.template;

import com.liferay.asset.display.page.portlet.AssetDisplayPageFriendlyURLProvider;
import com.liferay.asset.kernel.service.AssetTagLocalService;
import com.liferay.document.library.kernel.service.DLAppLocalService;
import com.liferay.document.library.util.DLURLHelper;
import com.liferay.dynamic.data.mapping.io.DDMFormDeserializer;
import com.liferay.dynamic.data.mapping.io.DDMFormDeserializerDeserializeRequest;
import com.liferay.dynamic.data.mapping.io.DDMFormDeserializerDeserializeResponse;
import com.liferay.dynamic.data.mapping.model.DDMForm;
import com.liferay.friendly.url.service.FriendlyURLEntryLocalService;
import com.liferay.journal.util.JournalContent;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.object.service.ObjectEntryService;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.service.ClassNameLocalService;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.template.TemplateContextContributor;
import com.liferay.reading.time.message.ReadingTimeMessageProvider;
import com.liferay.reading.time.service.ReadingTimeEntryLocalService;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Exposes the helper that migrated information templates use to rebuild the
 * Web Content template variables.
 *
 * @author Lino Alves
 */
@Component(
	property = "type=" + TemplateContextContributor.TYPE_GLOBAL,
	service = TemplateContextContributor.class
)
public class CMSMigrationTemplateContextContributor
	implements TemplateContextContributor {

	@Override
	public void prepare(
		Map<String, Object> contextObjects,
		HttpServletRequest httpServletRequest) {

		if (httpServletRequest == null) {
			return;
		}

		contextObjects.put(
			"cmsMigrationTemplateContextHelper",
			new CMSMigrationTemplateContextHelper(
				_assetDisplayPageFriendlyURLProvider, _assetTagLocalService,
				_classNameLocalService, _dlAppLocalService, _dlURLHelper,
				this::_getDDMForm, _friendlyURLEntryLocalService,
				_groupLocalService, httpServletRequest, _journalContent,
				_language, _objectDefinitionLocalService,
				_objectEntryLocalService, _objectEntryService,
				_readingTimeEntryLocalService, _readingTimeMessageProvider,
				_userLocalService));
	}

	private DDMForm _getDDMForm(String definition) {
		return _ddmForms.computeIfAbsent(
			definition,
			key -> {
				DDMFormDeserializerDeserializeResponse
					ddmFormDeserializerDeserializeResponse =
						_ddmFormDeserializer.deserialize(
							DDMFormDeserializerDeserializeRequest.Builder.
								newBuilder(
									key
								).build());

				return ddmFormDeserializerDeserializeResponse.getDDMForm();
			});
	}

	@Reference
	private AssetDisplayPageFriendlyURLProvider
		_assetDisplayPageFriendlyURLProvider;

	@Reference
	private AssetTagLocalService _assetTagLocalService;

	@Reference
	private ClassNameLocalService _classNameLocalService;

	@Reference(target = "(ddm.form.deserializer.type=json)")
	private DDMFormDeserializer _ddmFormDeserializer;

	private final Map<String, DDMForm> _ddmForms = new ConcurrentHashMap<>();

	@Reference
	private DLAppLocalService _dlAppLocalService;

	@Reference
	private DLURLHelper _dlURLHelper;

	@Reference
	private FriendlyURLEntryLocalService _friendlyURLEntryLocalService;

	@Reference
	private GroupLocalService _groupLocalService;

	@Reference
	private JournalContent _journalContent;

	@Reference
	private Language _language;

	@Reference
	private ObjectDefinitionLocalService _objectDefinitionLocalService;

	@Reference
	private ObjectEntryLocalService _objectEntryLocalService;

	@Reference
	private ObjectEntryService _objectEntryService;

	@Reference
	private ReadingTimeEntryLocalService _readingTimeEntryLocalService;

	@Reference(target = "(display.style=descriptive)")
	private ReadingTimeMessageProvider _readingTimeMessageProvider;

	@Reference
	private UserLocalService _userLocalService;

}