/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.reference;

import com.liferay.cms.migration.web.internal.migration.CMSMigrationContext;
import com.liferay.fragment.constants.FragmentConstants;
import com.liferay.fragment.model.FragmentEntryLink;
import com.liferay.fragment.service.FragmentEntryLinkLocalService;
import com.liferay.layout.page.template.model.LayoutPageTemplateStructure;
import com.liferay.layout.page.template.model.LayoutPageTemplateStructureRel;
import com.liferay.layout.page.template.service.LayoutPageTemplateStructureLocalService;
import com.liferay.layout.page.template.service.LayoutPageTemplateStructureRelLocalService;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.util.Validator;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Repoints the references that a content layout stores in its layout
 * structure and fragment entry links. Layouts include pages, page templates,
 * master pages, display page templates, and their drafts.
 *
 * @author Lino Alves
 */
public class LayoutReferenceRewriter {

	public LayoutReferenceRewriter(
		FragmentEntryLinkLocalService fragmentEntryLinkLocalService,
		InfoItemReferenceMapper infoItemReferenceMapper,
		JSONFactory jsonFactory, JSONReferenceRewriter jsonReferenceRewriter,
		LayoutPageTemplateStructureLocalService
			layoutPageTemplateStructureLocalService,
		LayoutPageTemplateStructureRelLocalService
			layoutPageTemplateStructureRelLocalService) {

		_fragmentEntryLinkLocalService = fragmentEntryLinkLocalService;
		_infoItemReferenceMapper = infoItemReferenceMapper;
		_jsonFactory = jsonFactory;
		_jsonReferenceRewriter = jsonReferenceRewriter;
		_layoutPageTemplateStructureLocalService =
			layoutPageTemplateStructureLocalService;
		_layoutPageTemplateStructureRelLocalService =
			layoutPageTemplateStructureRelLocalService;
	}

	/**
	 * @param displayPageReferenceScope the displayed item type, when the
	 *        layout belongs to a display page template, or <code>null</code>
	 */
	public void rewrite(
			CMSMigrationContext cmsMigrationContext, Layout layout,
			ReferenceScope displayPageReferenceScope)
		throws Exception {

		Map<Long, ReferenceScope> referenceScopes = new HashMap<>();

		LayoutPageTemplateStructure layoutPageTemplateStructure =
			_layoutPageTemplateStructureLocalService.
				fetchLayoutPageTemplateStructure(
					layout.getGroupId(), layout.getPlid());

		if (layoutPageTemplateStructure != null) {
			for (LayoutPageTemplateStructureRel layoutPageTemplateStructureRel :
					_layoutPageTemplateStructureRelLocalService.
						getLayoutPageTemplateStructureRels(
							layoutPageTemplateStructure.
								getLayoutPageTemplateStructureId())) {

				_rewriteLayoutPageTemplateStructureRel(
					cmsMigrationContext, displayPageReferenceScope,
					layoutPageTemplateStructureRel, referenceScopes);
			}
		}

		for (FragmentEntryLink fragmentEntryLink :
				_fragmentEntryLinkLocalService.getFragmentEntryLinksByPlid(
					layout.getGroupId(), layout.getPlid())) {

			if (fragmentEntryLink.getType() == FragmentConstants.TYPE_PORTLET) {
				continue;
			}

			String editableValues = fragmentEntryLink.getEditableValues();

			if (Validator.isNull(editableValues)) {
				continue;
			}

			JSONObject editableValuesJSONObject = _jsonFactory.createJSONObject(
				editableValues);

			ReferenceScope referenceScope = referenceScopes.getOrDefault(
				fragmentEntryLink.getFragmentEntryLinkId(),
				displayPageReferenceScope);

			if (_jsonReferenceRewriter.rewrite(
					cmsMigrationContext, editableValuesJSONObject,
					referenceScope)) {

				_fragmentEntryLinkLocalService.updateFragmentEntryLink(
					cmsMigrationContext.getUserId(),
					fragmentEntryLink.getFragmentEntryLinkId(),
					editableValuesJSONObject.toString(), false);
			}
		}
	}

	private void _addReferenceScopes(
		JSONObject itemsJSONObject, String itemId,
		ReferenceScope referenceScope,
		Map<Long, ReferenceScope> referenceScopes) {

		JSONObject itemJSONObject = itemsJSONObject.getJSONObject(itemId);

		if (itemJSONObject == null) {
			return;
		}

		JSONObject configJSONObject = itemJSONObject.getJSONObject("config");

		if ((configJSONObject != null) &&
			configJSONObject.has("fragmentEntryLinkId")) {

			referenceScopes.put(
				configJSONObject.getLong("fragmentEntryLinkId"),
				referenceScope);
		}

		JSONArray childrenJSONArray = itemJSONObject.getJSONArray("children");

		if (childrenJSONArray == null) {
			return;
		}

		for (int i = 0; i < childrenJSONArray.length(); i++) {
			_addReferenceScopes(
				itemsJSONObject, childrenJSONArray.getString(i), referenceScope,
				referenceScopes);
		}
	}

	private ReferenceScope _rewriteCollection(
		CMSMigrationContext cmsMigrationContext, JSONObject configJSONObject) {

		JSONObject collectionJSONObject = configJSONObject.getJSONObject(
			"collection");

		if (collectionJSONObject == null) {
			return null;
		}

		ReferenceScope referenceScope = null;
		String sourceClassName = _infoItemReferenceMapper.getSourceClassName(
			collectionJSONObject.getString("itemType"));

		if (Objects.equals(
				collectionJSONObject.getString("type"),
				_TYPE_INFO_LIST_ITEM_SELECTOR_RETURN_TYPE)) {

			referenceScope =
				cmsMigrationContext.getAssetListEntryReferenceScope(
					collectionJSONObject.getLong("classPK"));
		}
		else if (sourceClassName != null) {
			cmsMigrationContext.getCMSMigrationReport(
			).addWarning(
				"The collection provider " +
					collectionJSONObject.getString("key") +
						" lists classic content and has no counterpart"
			);
		}

		if (referenceScope == null) {
			return null;
		}

		collectionJSONObject.put(
			"itemSubtype", ""
		).put(
			"itemType",
			referenceScope.getObjectDefinition(
			).getClassName()
		);

		String[] rendererKeys = _infoItemReferenceMapper.getRendererKeys(
			cmsMigrationContext, referenceScope.getDDMStructure(), null,
			configJSONObject.getString("listItemStyle"),
			configJSONObject.getString("templateKey"));

		if (rendererKeys.length > 0) {
			configJSONObject.put(
				"listItemStyle", rendererKeys[0]
			).put(
				"templateKey", rendererKeys[1]
			);
		}

		return referenceScope;
	}

	private void _rewriteLayoutPageTemplateStructureRel(
			CMSMigrationContext cmsMigrationContext,
			ReferenceScope displayPageReferenceScope,
			LayoutPageTemplateStructureRel layoutPageTemplateStructureRel,
			Map<Long, ReferenceScope> referenceScopes)
		throws Exception {

		String data = layoutPageTemplateStructureRel.getData();

		if (Validator.isNull(data)) {
			return;
		}

		JSONObject dataJSONObject = _jsonFactory.createJSONObject(data);

		JSONObject itemsJSONObject = dataJSONObject.getJSONObject("items");

		if (itemsJSONObject == null) {
			return;
		}

		boolean changed = false;

		for (String itemId : itemsJSONObject.keySet()) {
			JSONObject itemJSONObject = itemsJSONObject.getJSONObject(itemId);

			JSONObject configJSONObject = itemJSONObject.getJSONObject(
				"config");

			if (configJSONObject == null) {
				continue;
			}

			if (Objects.equals(
					itemJSONObject.getString("type"), "collection")) {

				String configJSON = configJSONObject.toString();

				ReferenceScope referenceScope = _rewriteCollection(
					cmsMigrationContext, configJSONObject);

				if (referenceScope != null) {
					_addReferenceScopes(
						itemsJSONObject, itemId, referenceScope,
						referenceScopes);
				}

				if (!configJSON.equals(configJSONObject.toString())) {
					changed = true;
				}

				continue;
			}

			if (_jsonReferenceRewriter.rewrite(
					cmsMigrationContext, configJSONObject,
					displayPageReferenceScope)) {

				changed = true;
			}
		}

		if (changed) {
			_layoutPageTemplateStructureRelLocalService.
				updateLayoutPageTemplateStructureRel(
					layoutPageTemplateStructureRel.
						getLayoutPageTemplateStructureId(),
					layoutPageTemplateStructureRel.getSegmentsExperienceId(),
					dataJSONObject.toString());
		}
	}

	private static final String _TYPE_INFO_LIST_ITEM_SELECTOR_RETURN_TYPE =
		"com.liferay.item.selector.criteria.InfoListItemSelectorReturnType";

	private final FragmentEntryLinkLocalService _fragmentEntryLinkLocalService;
	private final InfoItemReferenceMapper _infoItemReferenceMapper;
	private final JSONFactory _jsonFactory;
	private final JSONReferenceRewriter _jsonReferenceRewriter;
	private final LayoutPageTemplateStructureLocalService
		_layoutPageTemplateStructureLocalService;
	private final LayoutPageTemplateStructureRelLocalService
		_layoutPageTemplateStructureRelLocalService;

}