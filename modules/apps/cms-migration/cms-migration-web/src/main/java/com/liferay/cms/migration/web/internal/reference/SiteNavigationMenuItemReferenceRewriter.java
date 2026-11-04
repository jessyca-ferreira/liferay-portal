/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.reference;

import com.liferay.cms.migration.web.internal.migration.CMSMigrationContext;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.UnicodeProperties;
import com.liferay.portal.kernel.util.UnicodePropertiesBuilder;
import com.liferay.site.navigation.model.SiteNavigationMenu;
import com.liferay.site.navigation.model.SiteNavigationMenuItem;
import com.liferay.site.navigation.service.SiteNavigationMenuItemLocalService;
import com.liferay.site.navigation.service.SiteNavigationMenuLocalService;

/**
 * Repoints the navigation menu items that link to the display page of classic
 * content.
 *
 * @author Lino Alves
 */
public class SiteNavigationMenuItemReferenceRewriter {

	public SiteNavigationMenuItemReferenceRewriter(
		InfoItemReferenceMapper infoItemReferenceMapper,
		SiteNavigationMenuItemLocalService siteNavigationMenuItemLocalService,
		SiteNavigationMenuLocalService siteNavigationMenuLocalService) {

		_infoItemReferenceMapper = infoItemReferenceMapper;
		_siteNavigationMenuItemLocalService =
			siteNavigationMenuItemLocalService;
		_siteNavigationMenuLocalService = siteNavigationMenuLocalService;
	}

	public void rewrite(CMSMigrationContext cmsMigrationContext) {
		for (SiteNavigationMenu siteNavigationMenu :
				_siteNavigationMenuLocalService.getSiteNavigationMenus(
					cmsMigrationContext.getRewriteGroupId())) {

			for (SiteNavigationMenuItem siteNavigationMenuItem :
					_siteNavigationMenuItemLocalService.
						getSiteNavigationMenuItems(
							siteNavigationMenu.getSiteNavigationMenuId())) {

				try {
					_rewrite(cmsMigrationContext, siteNavigationMenuItem);
				}
				catch (Exception exception) {
					cmsMigrationContext.getCMSMigrationReport(
					).addError(
						StringBundler.concat(
							"Unable to rewrite the navigation menu item ",
							siteNavigationMenuItem.getName(), ": ",
							exception.getMessage())
					);
				}
			}
		}
	}

	private void _rewrite(
			CMSMigrationContext cmsMigrationContext,
			SiteNavigationMenuItem siteNavigationMenuItem)
		throws Exception {

		String sourceClassName = _infoItemReferenceMapper.getSourceClassName(
			siteNavigationMenuItem.getType());

		if (sourceClassName == null) {
			return;
		}

		UnicodeProperties typeSettingsUnicodeProperties =
			UnicodePropertiesBuilder.fastLoad(
				siteNavigationMenuItem.getTypeSettings()
			).build();

		long classPK = GetterUtil.getLong(
			typeSettingsUnicodeProperties.getProperty("classPK"));

		if (classPK <= 0) {
			classPK = _infoItemReferenceMapper.getClassPK(
				cmsMigrationContext, sourceClassName,
				typeSettingsUnicodeProperties.getProperty(
					"externalReferenceCode"),
				typeSettingsUnicodeProperties.getProperty(
					"scopeExternalReferenceCode"));
		}

		ObjectEntry objectEntry = _infoItemReferenceMapper.getObjectEntry(
			cmsMigrationContext, sourceClassName, classPK);

		if (objectEntry == null) {
			return;
		}

		ObjectDefinition objectDefinition =
			_infoItemReferenceMapper.getObjectDefinition(objectEntry);

		typeSettingsUnicodeProperties.setProperty(
			"className", objectDefinition.getClassName());

		typeSettingsUnicodeProperties.setProperty(
			"classPK", String.valueOf(objectEntry.getObjectEntryId()));
		typeSettingsUnicodeProperties.setProperty(
			"externalReferenceCode", objectEntry.getExternalReferenceCode());
		typeSettingsUnicodeProperties.setProperty(
			"groupId", String.valueOf(objectEntry.getGroupId()));
		typeSettingsUnicodeProperties.setProperty(
			"scopeExternalReferenceCode",
			cmsMigrationContext.getDepotGroup(
			).getExternalReferenceCode());
		typeSettingsUnicodeProperties.setProperty(
			"type",
			objectDefinition.getLabel(objectDefinition.getDefaultLanguageId()));

		_siteNavigationMenuItemLocalService.updateSiteNavigationMenuItem(
			cmsMigrationContext.getUserId(),
			siteNavigationMenuItem.getSiteNavigationMenuItemId(),
			siteNavigationMenuItem.getGroupId(),
			siteNavigationMenuItem.getSiteNavigationMenuId(),
			siteNavigationMenuItem.getParentSiteNavigationMenuItemId(),
			objectDefinition.getClassName(), siteNavigationMenuItem.getOrder(),
			typeSettingsUnicodeProperties.toString());

		cmsMigrationContext.getCMSMigrationReport(
		).increment(
			"navigation-menu-items"
		);
	}

	private final InfoItemReferenceMapper _infoItemReferenceMapper;
	private final SiteNavigationMenuItemLocalService
		_siteNavigationMenuItemLocalService;
	private final SiteNavigationMenuLocalService
		_siteNavigationMenuLocalService;

}