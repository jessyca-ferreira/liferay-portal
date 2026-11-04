/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.object.deployer;

import com.liferay.cms.migration.web.internal.constants.CMSMigrationConstants;
import com.liferay.cms.migration.web.internal.info.item.renderer.CMSMigrationInfoItemRenderer;
import com.liferay.document.library.kernel.service.DLAppLocalService;
import com.liferay.document.library.util.DLURLHelper;
import com.liferay.info.item.renderer.InfoItemRenderer;
import com.liferay.object.deployer.ObjectDefinitionDeployer;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.util.HashMapDictionaryBuilder;
import com.liferay.portal.kernel.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Registers the renderers that reproduce the classic abstract, title, and
 * full content views for every object definition the migration creates.
 *
 * @author Lino Alves
 */
@Component(service = ObjectDefinitionDeployer.class)
public class CMSMigrationObjectDefinitionDeployer
	implements ObjectDefinitionDeployer {

	@Override
	public List<ServiceRegistration<?>> deploy(
		ObjectDefinition objectDefinition) {

		String externalReferenceCode =
			objectDefinition.getExternalReferenceCode();

		if (!StringUtil.startsWith(
				externalReferenceCode,
				CMSMigrationConstants.EXTERNAL_REFERENCE_CODE_PREFIX)) {

			return Collections.emptyList();
		}

		List<ServiceRegistration<?>> serviceRegistrations = new ArrayList<>();

		if (externalReferenceCode.equals(
				CMSMigrationConstants.
					OBJECT_DEFINITION_EXTERNAL_REFERENCE_CODE_BLOGS_ENTRY)) {

			serviceRegistrations.add(
				_register(
					objectDefinition,
					CMSMigrationInfoItemRenderer.STYLE_ABSTRACT));
			serviceRegistrations.add(
				_register(
					objectDefinition,
					CMSMigrationInfoItemRenderer.STYLE_BLOGS_FULL_CONTENT));
		}
		else if (externalReferenceCode.startsWith(
					CMSMigrationConstants.
						EXTERNAL_REFERENCE_CODE_PREFIX_DL_FILE_ENTRY_TYPE)) {

			serviceRegistrations.add(
				_register(
					objectDefinition,
					CMSMigrationInfoItemRenderer.STYLE_FILE_ABSTRACT));
			serviceRegistrations.add(
				_register(
					objectDefinition,
					CMSMigrationInfoItemRenderer.STYLE_FILE_TITLE));
		}
		else {
			serviceRegistrations.add(
				_register(
					objectDefinition,
					CMSMigrationInfoItemRenderer.STYLE_ABSTRACT));
			serviceRegistrations.add(
				_register(
					objectDefinition,
					CMSMigrationInfoItemRenderer.STYLE_TITLE));
		}

		return serviceRegistrations;
	}

	@Activate
	protected void activate(BundleContext bundleContext) {
		_bundleContext = bundleContext;
	}

	private ServiceRegistration<?> _register(
		ObjectDefinition objectDefinition, String style) {

		return _bundleContext.registerService(
			InfoItemRenderer.class,
			new CMSMigrationInfoItemRenderer(
				_dlAppLocalService, _dlURLHelper, _language, style),
			HashMapDictionaryBuilder.<String, Object>put(
				"company.id", objectDefinition.getCompanyId()
			).put(
				"item.class.name", objectDefinition.getClassName()
			).put(
				"service.ranking", 50
			).build());
	}

	private BundleContext _bundleContext;

	@Reference
	private DLAppLocalService _dlAppLocalService;

	@Reference
	private DLURLHelper _dlURLHelper;

	@Reference
	private Language _language;

}