/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.reference;

import com.liferay.asset.kernel.model.AssetEntry;
import com.liferay.asset.kernel.service.AssetEntryLocalService;
import com.liferay.asset.list.constants.AssetListEntryTypeConstants;
import com.liferay.asset.list.model.AssetListEntry;
import com.liferay.asset.list.model.AssetListEntryAssetEntryRel;
import com.liferay.asset.list.model.AssetListEntrySegmentsEntryRel;
import com.liferay.asset.list.service.AssetListEntryAssetEntryRelLocalService;
import com.liferay.asset.list.service.AssetListEntryLocalService;
import com.liferay.asset.list.service.AssetListEntrySegmentsEntryRelLocalService;
import com.liferay.cms.migration.web.internal.migration.CMSMigrationContext;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.UnicodeProperties;
import com.liferay.portal.kernel.util.UnicodePropertiesBuilder;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Repoints the collections of a site: manual collections select the migrated
 * entries, and dynamic collections query the migrated object definitions in
 * the space.
 *
 * @author Lino Alves
 */
public class AssetListEntryReferenceRewriter {

	public AssetListEntryReferenceRewriter(
		AssetEntryLocalService assetEntryLocalService,
		AssetListEntryAssetEntryRelLocalService
			assetListEntryAssetEntryRelLocalService,
		AssetListEntryLocalService assetListEntryLocalService,
		AssetListEntrySegmentsEntryRelLocalService
			assetListEntrySegmentsEntryRelLocalService,
		AssetQueryReferenceRewriter assetQueryReferenceRewriter,
		InfoItemReferenceMapper infoItemReferenceMapper) {

		_assetEntryLocalService = assetEntryLocalService;
		_assetListEntryAssetEntryRelLocalService =
			assetListEntryAssetEntryRelLocalService;
		_assetListEntryLocalService = assetListEntryLocalService;
		_assetListEntrySegmentsEntryRelLocalService =
			assetListEntrySegmentsEntryRelLocalService;
		_assetQueryReferenceRewriter = assetQueryReferenceRewriter;
		_infoItemReferenceMapper = infoItemReferenceMapper;
	}

	public void rewrite(CMSMigrationContext cmsMigrationContext) {
		for (AssetListEntry assetListEntry :
				_assetListEntryLocalService.getAssetListEntries(
					cmsMigrationContext.getRewriteGroupId())) {

			cmsMigrationContext.sendStatus(assetListEntry.getTitle());

			try {
				if (assetListEntry.getType() ==
						AssetListEntryTypeConstants.TYPE_MANUAL) {

					_rewriteManual(cmsMigrationContext, assetListEntry);
				}
				else {
					_rewriteDynamic(cmsMigrationContext, assetListEntry);
				}
			}
			catch (Exception exception) {
				cmsMigrationContext.getCMSMigrationReport(
				).addError(
					StringBundler.concat(
						"Unable to rewrite the collection ",
						assetListEntry.getTitle(), ": ", exception.getMessage())
				);
			}
		}
	}

	private boolean _isOnlyType(
		AssetListEntry assetListEntry, ObjectDefinition objectDefinition) {

		for (AssetListEntryAssetEntryRel assetListEntryAssetEntryRel :
				_assetListEntryAssetEntryRelLocalService.
					getAssetListEntryAssetEntryRels(
						assetListEntry.getAssetListEntryId(), QueryUtil.ALL_POS,
						QueryUtil.ALL_POS)) {

			AssetEntry assetEntry = _assetEntryLocalService.fetchEntry(
				assetListEntryAssetEntryRel.getAssetEntryId());

			if ((assetEntry != null) &&
				!Objects.equals(
					assetEntry.getClassName(),
					objectDefinition.getClassName())) {

				return false;
			}
		}

		return true;
	}

	private void _rewriteDynamic(
			CMSMigrationContext cmsMigrationContext,
			AssetListEntry assetListEntry)
		throws PortalException {

		ReferenceScope referenceScope = null;

		for (AssetListEntrySegmentsEntryRel assetListEntrySegmentsEntryRel :
				_assetListEntrySegmentsEntryRelLocalService.
					getAssetListEntrySegmentsEntryRels(
						assetListEntry.getAssetListEntryId(), QueryUtil.ALL_POS,
						QueryUtil.ALL_POS)) {

			UnicodeProperties unicodeProperties =
				UnicodePropertiesBuilder.fastLoad(
					assetListEntrySegmentsEntryRel.getTypeSettings()
				).build();

			Set<String> groupIds = new LinkedHashSet<>();

			for (String groupId :
					StringUtil.split(
						unicodeProperties.getProperty(
							"groupIds",
							String.valueOf(
								cmsMigrationContext.getRewriteGroupId())))) {

				groupIds.add(groupId);
			}

			if (!cmsMigrationContext.isRewriteSourceGroup() &&
				!groupIds.contains(
					String.valueOf(cmsMigrationContext.getSourceGroupId()))) {

				continue;
			}

			Map<String, String[]> settings = new HashMap<>();

			for (Map.Entry<String, String> entry :
					unicodeProperties.entrySet()) {

				settings.put(
					entry.getKey(), StringUtil.split(entry.getValue()));
			}

			String originalSettings = unicodeProperties.toString();

			ReferenceScope segmentReferenceScope =
				_assetQueryReferenceRewriter.rewrite(
					cmsMigrationContext, settings,
					"The collection " + assetListEntry.getTitle());

			if (segmentReferenceScope != null) {
				referenceScope = segmentReferenceScope;
			}

			for (Map.Entry<String, String[]> entry : settings.entrySet()) {
				unicodeProperties.setProperty(
					entry.getKey(), StringUtil.merge(entry.getValue()));
			}

			groupIds.add(String.valueOf(cmsMigrationContext.getDepotGroupId()));

			unicodeProperties.setProperty(
				"groupIds", StringUtil.merge(groupIds, StringPool.COMMA));

			if (originalSettings.equals(unicodeProperties.toString())) {
				continue;
			}

			_assetListEntryLocalService.updateAssetListEntryTypeSettings(
				assetListEntry.getAssetListEntryId(),
				assetListEntrySegmentsEntryRel.getSegmentsEntryId(),
				unicodeProperties.toString());
		}

		if (referenceScope != null) {
			cmsMigrationContext.addAssetListEntryReferenceScope(
				assetListEntry.getAssetListEntryId(), referenceScope);
		}
	}

	private void _rewriteManual(
			CMSMigrationContext cmsMigrationContext,
			AssetListEntry assetListEntry)
		throws PortalException {

		Map<String, ReferenceScope> referenceScopes = new HashMap<>();

		for (AssetListEntryAssetEntryRel assetListEntryAssetEntryRel :
				_assetListEntryAssetEntryRelLocalService.
					getAssetListEntryAssetEntryRels(
						assetListEntry.getAssetListEntryId(), QueryUtil.ALL_POS,
						QueryUtil.ALL_POS)) {

			AssetEntry assetEntry = _assetEntryLocalService.fetchEntry(
				assetListEntryAssetEntryRel.getAssetEntryId());

			if (assetEntry == null) {
				continue;
			}

			String sourceClassName =
				_infoItemReferenceMapper.getSourceClassName(
					assetEntry.getClassName());

			ObjectEntry objectEntry = _infoItemReferenceMapper.getObjectEntry(
				cmsMigrationContext, sourceClassName, assetEntry.getClassPK());

			if (objectEntry == null) {
				continue;
			}

			ObjectDefinition objectDefinition =
				_infoItemReferenceMapper.getObjectDefinition(objectEntry);

			AssetEntry objectAssetEntry = _assetEntryLocalService.fetchEntry(
				objectDefinition.getClassName(),
				objectEntry.getObjectEntryId());

			if (objectAssetEntry == null) {
				continue;
			}

			referenceScopes.put(
				objectDefinition.getClassName(),
				new ReferenceScope(
					_infoItemReferenceMapper.getDDMStructure(
						sourceClassName, assetEntry.getClassPK()),
					objectDefinition, sourceClassName));

			_assetListEntryAssetEntryRelLocalService.
				updateAssetListEntryAssetEntryRel(
					assetListEntryAssetEntryRel.
						getAssetListEntryAssetEntryRelId(),
					assetListEntry.getAssetListEntryId(),
					objectAssetEntry.getEntryId(),
					assetListEntryAssetEntryRel.getSegmentsEntryId(),
					assetListEntryAssetEntryRel.getPosition());
		}

		if (referenceScopes.size() != 1) {
			return;
		}

		ReferenceScope referenceScope = referenceScopes.values(
		).iterator(
		).next();

		ObjectDefinition objectDefinition =
			referenceScope.getObjectDefinition();

		if (_isOnlyType(assetListEntry, objectDefinition)) {
			assetListEntry.setAssetEntrySubtype(StringPool.BLANK);
			assetListEntry.setAssetEntryType(objectDefinition.getClassName());

			assetListEntry = _assetListEntryLocalService.updateAssetListEntry(
				assetListEntry);
		}

		cmsMigrationContext.addAssetListEntryReferenceScope(
			assetListEntry.getAssetListEntryId(), referenceScope);
	}

	private final AssetEntryLocalService _assetEntryLocalService;
	private final AssetListEntryAssetEntryRelLocalService
		_assetListEntryAssetEntryRelLocalService;
	private final AssetListEntryLocalService _assetListEntryLocalService;
	private final AssetListEntrySegmentsEntryRelLocalService
		_assetListEntrySegmentsEntryRelLocalService;
	private final AssetQueryReferenceRewriter _assetQueryReferenceRewriter;
	private final InfoItemReferenceMapper _infoItemReferenceMapper;

}