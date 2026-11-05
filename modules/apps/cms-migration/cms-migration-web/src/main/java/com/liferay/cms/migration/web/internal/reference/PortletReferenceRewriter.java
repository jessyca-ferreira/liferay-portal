/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.reference;

import com.liferay.asset.kernel.model.AssetEntry;
import com.liferay.asset.kernel.service.AssetEntryLocalService;
import com.liferay.asset.publisher.constants.AssetPublisherPortletKeys;
import com.liferay.asset.publisher.util.AssetPublisherHelper;
import com.liferay.blogs.constants.BlogsPortletKeys;
import com.liferay.blogs.model.BlogsEntry;
import com.liferay.cms.migration.web.internal.constants.CMSMigrationConstants;
import com.liferay.cms.migration.web.internal.constants.CMSMigrationPortletKeys;
import com.liferay.cms.migration.web.internal.migration.CMSMigrationContext;
import com.liferay.document.library.constants.DLPortletKeys;
import com.liferay.dynamic.data.mapping.constants.DDMTemplateConstants;
import com.liferay.dynamic.data.mapping.model.DDMTemplate;
import com.liferay.dynamic.data.mapping.service.DDMTemplateLocalService;
import com.liferay.fragment.constants.FragmentConstants;
import com.liferay.fragment.model.FragmentEntryLink;
import com.liferay.fragment.service.FragmentEntryLinkLocalService;
import com.liferay.journal.constants.JournalContentPortletKeys;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.service.JournalArticleLocalService;
import com.liferay.layout.util.UpdateLayoutModifiedDateThreadLocal;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.petra.lang.SafeCloseable;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.module.service.Snapshot;
import com.liferay.portal.kernel.portlet.PortletIdCodec;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.service.LayoutLocalService;
import com.liferay.portal.kernel.service.PortletPreferencesLocalService;
import com.liferay.portal.kernel.settings.FallbackKeysSettingsUtil;
import com.liferay.portal.kernel.settings.PortletInstanceSettingsLocator;
import com.liferay.portal.kernel.settings.Settings;
import com.liferay.portal.kernel.template.TemplateConstants;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.PortletKeys;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.UnicodeProperties;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.kernel.xml.Document;
import com.liferay.portal.kernel.xml.Element;
import com.liferay.portal.kernel.xml.SAXReader;
import com.liferay.portlet.display.template.PortletDisplayTemplate;

import jakarta.portlet.PortletPreferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Repoints the widgets of a layout. Web Content Display widgets become CMS
 * Content Display widgets that render the migrated entry with the migrated
 * template, Asset Publishers select the migrated entries, and Blogs widgets
 * become Asset Publishers with a widget template that reproduces the Blogs
 * abstract view.
 *
 * @author Lino Alves
 */
public class PortletReferenceRewriter {

	public void rewrite(CMSMigrationContext cmsMigrationContext, Layout layout)
		throws Exception {

		boolean updateLayoutModifiedDate =
			UpdateLayoutModifiedDateThreadLocal.isUpdateLayoutModifiedDate() &&
			!layout.isDraftLayout();

		try (SafeCloseable safeCloseable =
				UpdateLayoutModifiedDateThreadLocal.
					setUpdateLayoutModifiedDateWithSafeCloseable(
						updateLayoutModifiedDate)) {

			for (com.liferay.portal.kernel.model.PortletPreferences
					portletPreferences :
						_portletPreferencesLocalService.
							getPortletPreferencesByPlid(layout.getPlid())) {

				if (portletPreferences.getOwnerType() !=
						PortletKeys.PREFS_OWNER_TYPE_LAYOUT) {

					continue;
				}

				String portletId = portletPreferences.getPortletId();

				String portletName = PortletIdCodec.decodePortletName(
					portletId);

				try {
					if (portletName.equals(
							AssetPublisherPortletKeys.ASSET_PUBLISHER)) {

						_rewriteAssetPublisher(
							cmsMigrationContext, layout, portletId);
					}
					else if (cmsMigrationContext.isRewriteSourceGroup() &&
							 (portletName.equals(BlogsPortletKeys.BLOGS) ||
							  portletName.equals(
								  BlogsPortletKeys.BLOGS_AGGREGATOR))) {

						_replaceBlogs(cmsMigrationContext, layout, portletId);
					}
					else if (portletName.equals(
								JournalContentPortletKeys.JOURNAL_CONTENT)) {

						_replaceWebContentDisplay(
							cmsMigrationContext, layout, portletId);
					}
					else if (portletName.equals(
								DLPortletKeys.DOCUMENT_LIBRARY) ||
							 portletName.equals(
								 DLPortletKeys.MEDIA_GALLERY_DISPLAY)) {

						cmsMigrationContext.getCMSMigrationReport(
						).addWarning(
							StringBundler.concat(
								"The Documents and Media widget on the page ",
								layout.getFriendlyURL(),
								" browses the classic library and has no ",
								"counterpart for CMS files")
						);
					}
				}
				catch (Exception exception) {
					cmsMigrationContext.getCMSMigrationReport(
					).addError(
						StringBundler.concat(
							"Unable to rewrite the widget ", portletId, " on ",
							layout.getFriendlyURL(), ": ",
							exception.getMessage())
					);
				}
			}
		}
	}

	private Map<String, String[]> _copyDecorationPreferences(
		PortletPreferences portletPreferences) {

		Map<String, String[]> preferences = new HashMap<>();

		Map<String, String[]> map = portletPreferences.getMap();

		for (Map.Entry<String, String[]> entry : map.entrySet()) {
			if (entry.getKey(
				).startsWith(
					"portletSetup"
				) ||
				entry.getKey(
				).startsWith(
					"lfrWap"
				)) {

				preferences.put(entry.getKey(), entry.getValue());
			}
		}

		return preferences;
	}

	private String _getBlogsWidgetTemplateKey(
			CMSMigrationContext cmsMigrationContext)
		throws Exception {

		long classNameId = _portal.getClassNameId(AssetEntry.class);

		DDMTemplate ddmTemplate = _ddmTemplateLocalService.fetchTemplate(
			cmsMigrationContext.getSourceGroupId(), classNameId,
			_BLOGS_WIDGET_TEMPLATE_KEY);

		if (ddmTemplate != null) {
			return ddmTemplate.getTemplateKey();
		}

		ddmTemplate = _ddmTemplateLocalService.addTemplate(
			CMSMigrationConstants.
				EXTERNAL_REFERENCE_CODE_PREFIX_WIDGET_TEMPLATE + "BLOGS",
			cmsMigrationContext.getUserId(),
			cmsMigrationContext.getSourceGroupId(), classNameId, 0,
			_portal.getClassNameId(PortletDisplayTemplate.class),
			_BLOGS_WIDGET_TEMPLATE_KEY,
			HashMapBuilder.put(
				LocaleUtil.getSiteDefault(), "Blogs (CMS Migration)"
			).build(),
			null, DDMTemplateConstants.TEMPLATE_TYPE_DISPLAY, StringPool.BLANK,
			TemplateConstants.LANG_TYPE_FTL,
			StringUtil.read(
				PortletReferenceRewriter.class,
				"dependencies/blogs_entries.ftl"),
			true, false, StringPool.BLANK, null,
			cmsMigrationContext.getServiceContext());

		return ddmTemplate.getTemplateKey();
	}

	private JournalArticle _getJournalArticle(
		Layout layout, PortletPreferences portletPreferences) {

		long groupId = GetterUtil.getLong(
			portletPreferences.getValue("groupId", null), layout.getGroupId());

		String groupExternalReferenceCode = portletPreferences.getValue(
			"groupExternalReferenceCode", null);

		if (Validator.isNotNull(groupExternalReferenceCode)) {
			Group group = _groupLocalService.fetchGroupByExternalReferenceCode(
				groupExternalReferenceCode, layout.getCompanyId());

			if (group != null) {
				groupId = group.getGroupId();
			}
		}

		String articleExternalReferenceCode = portletPreferences.getValue(
			"articleExternalReferenceCode", null);

		if (Validator.isNotNull(articleExternalReferenceCode)) {
			JournalArticle journalArticle =
				_journalArticleLocalService.
					fetchLatestArticleByExternalReferenceCode(
						groupId, articleExternalReferenceCode);

			if (journalArticle != null) {
				return journalArticle;
			}
		}

		String articleId = portletPreferences.getValue("articleId", null);

		if (Validator.isNull(articleId)) {
			return null;
		}

		return _journalArticleLocalService.fetchLatestArticle(
			groupId, articleId, WorkflowConstants.STATUS_ANY);
	}

	private boolean _isReadingTimeEnabled(Layout layout, String portletId)
		throws Exception {

		Settings settings = FallbackKeysSettingsUtil.getSettings(
			new PortletInstanceSettingsLocator(layout, portletId));

		return GetterUtil.getBoolean(
			settings.getValue("enableReadingTime", "false"));
	}

	private void _replaceBlogs(
			CMSMigrationContext cmsMigrationContext, Layout layout,
			String portletId)
		throws Exception {

		ObjectDefinition objectDefinition =
			cmsMigrationContext.getObjectDefinition(
				BlogsEntry.class.getName(), 0);

		if (objectDefinition == null) {
			return;
		}

		PortletPreferences portletPreferences =
			_portletPreferencesLocalService.getPreferences(
				layout.getCompanyId(), PortletKeys.PREFS_OWNER_ID_DEFAULT,
				PortletKeys.PREFS_OWNER_TYPE_LAYOUT, layout.getPlid(),
				portletId);

		Map<String, String[]> preferences = _copyDecorationPreferences(
			portletPreferences);

		String classNameId = String.valueOf(
			_portal.getClassNameId(objectDefinition.getClassName()));
		Group sourceGroup = cmsMigrationContext.getSourceGroup();

		preferences.put("anyAssetType", new String[] {classNameId});
		preferences.put("classNameIds", new String[] {classNameId});
		preferences.put(
			"delta",
			new String[] {portletPreferences.getValue("pageDelta", "5")});
		preferences.put(
			"displayStyle",
			new String[] {
				PortletDisplayTemplate.DISPLAY_STYLE_PREFIX +
					_getBlogsWidgetTemplateKey(cmsMigrationContext)
			});
		preferences.put(
			"displayStyleGroupExternalReferenceCode",
			new String[] {sourceGroup.getExternalReferenceCode()});
		preferences.put(
			"enableReadingTime",
			new String[] {
				String.valueOf(_isReadingTimeEnabled(layout, portletId))
			});
		preferences.put("orderByColumn1", new String[] {"displayDate"});
		preferences.put("orderByType1", new String[] {"DESC"});
		preferences.put("paginationType", new String[] {"regular"});
		preferences.put(
			"scopeIds",
			new String[] {
				_assetPublisherHelper.getScopeId(
					cmsMigrationContext.getDepotGroup(), layout.getGroupId())
			});
		preferences.put("selectionStyle", new String[] {"dynamic"});

		_replacePortlet(
			layout, portletId, AssetPublisherPortletKeys.ASSET_PUBLISHER,
			preferences);

		cmsMigrationContext.getCMSMigrationReport(
		).increment(
			"widgets"
		);
	}

	private void _replacePortlet(
			Layout layout, String oldPortletId, String newPortletName,
			Map<String, String[]> newPreferences)
		throws Exception {

		String instanceId = PortletIdCodec.decodeInstanceId(oldPortletId);

		if (Validator.isNull(instanceId)) {
			instanceId = PortletIdCodec.generateInstanceId();
		}

		String newPortletId = PortletIdCodec.encode(newPortletName, instanceId);

		PortletPreferences portletPreferences =
			_portletPreferencesLocalService.getPreferences(
				layout.getCompanyId(), PortletKeys.PREFS_OWNER_ID_DEFAULT,
				PortletKeys.PREFS_OWNER_TYPE_LAYOUT, layout.getPlid(),
				newPortletId);

		for (Map.Entry<String, String[]> entry : newPreferences.entrySet()) {
			portletPreferences.setValues(entry.getKey(), entry.getValue());
		}

		_portletPreferencesLocalService.updatePreferences(
			PortletKeys.PREFS_OWNER_ID_DEFAULT,
			PortletKeys.PREFS_OWNER_TYPE_LAYOUT, layout.getPlid(), newPortletId,
			portletPreferences);

		if (layout.isTypeContent()) {
			_replacePortletInFragmentEntryLink(
				layout, oldPortletId, newPortletName, instanceId);
		}
		else {
			layout = _layoutLocalService.getLayout(layout.getPlid());

			UnicodeProperties typeSettingsUnicodeProperties =
				layout.getTypeSettingsProperties();

			for (Map.Entry<String, String> entry :
					typeSettingsUnicodeProperties.entrySet()) {

				String[] portletIds = StringUtil.split(entry.getValue());

				if (!ArrayUtil.contains(portletIds, oldPortletId)) {
					continue;
				}

				for (int i = 0; i < portletIds.length; i++) {
					if (portletIds[i].equals(oldPortletId)) {
						portletIds[i] = newPortletId;
					}
				}

				entry.setValue(StringUtil.merge(portletIds));
			}

			layout.setTypeSettingsProperties(typeSettingsUnicodeProperties);

			layout = _layoutLocalService.updateLayout(layout);
		}

		_portletPreferencesLocalService.deletePortletPreferences(
			PortletKeys.PREFS_OWNER_ID_DEFAULT,
			PortletKeys.PREFS_OWNER_TYPE_LAYOUT, layout.getPlid(),
			oldPortletId);
	}

	private void _replacePortletInFragmentEntryLink(
			Layout layout, String oldPortletId, String newPortletName,
			String instanceId)
		throws Exception {

		String oldInstanceId = PortletIdCodec.decodeInstanceId(oldPortletId);
		String oldPortletName = PortletIdCodec.decodePortletName(oldPortletId);

		for (FragmentEntryLink fragmentEntryLink :
				_fragmentEntryLinkLocalService.getFragmentEntryLinksByPlid(
					layout.getGroupId(), layout.getPlid())) {

			if (fragmentEntryLink.getType() != FragmentConstants.TYPE_PORTLET) {
				continue;
			}

			JSONObject editableValuesJSONObject = _jsonFactory.createJSONObject(
				fragmentEntryLink.getEditableValues());

			if (!Objects.equals(
					editableValuesJSONObject.getString("portletId"),
					oldPortletName) ||
				!Objects.equals(
					editableValuesJSONObject.getString("instanceId"),
					GetterUtil.getString(oldInstanceId))) {

				continue;
			}

			editableValuesJSONObject.put(
				"instanceId", instanceId
			).put(
				"portletId", newPortletName
			);

			fragmentEntryLink.setEditableValues(
				editableValuesJSONObject.toString());
			fragmentEntryLink.setNamespace(instanceId);

			_fragmentEntryLinkLocalService.updateFragmentEntryLink(
				fragmentEntryLink);
		}
	}

	private void _replaceWebContentDisplay(
			CMSMigrationContext cmsMigrationContext, Layout layout,
			String portletId)
		throws Exception {

		PortletPreferences portletPreferences =
			_portletPreferencesLocalService.getPreferences(
				layout.getCompanyId(), PortletKeys.PREFS_OWNER_ID_DEFAULT,
				PortletKeys.PREFS_OWNER_TYPE_LAYOUT, layout.getPlid(),
				portletId);

		JournalArticle journalArticle = _getJournalArticle(
			layout, portletPreferences);

		if (journalArticle == null) {
			return;
		}

		ObjectEntry objectEntry = cmsMigrationContext.getObjectEntry(
			JournalArticle.class.getName(),
			journalArticle.getResourcePrimKey());

		if (objectEntry == null) {
			return;
		}

		String ddmTemplateKey = portletPreferences.getValue(
			"ddmTemplateKey", null);

		if (Validator.isNull(ddmTemplateKey)) {
			ddmTemplateKey = journalArticle.getDDMTemplateKey();
		}

		long templateEntryId = _infoItemReferenceMapper.getTemplateEntryId(
			cmsMigrationContext, journalArticle.getDDMStructure(),
			ddmTemplateKey);

		if (templateEntryId == 0) {
			cmsMigrationContext.getCMSMigrationReport(
			).addWarning(
				StringBundler.concat(
					"The template ", ddmTemplateKey, " of the web content ",
					journalArticle.getArticleId(), " on ",
					layout.getFriendlyURL(), " was not migrated")
			);
		}

		Map<String, String[]> preferences = _copyDecorationPreferences(
			portletPreferences);

		preferences.put(
			"objectEntryId",
			new String[] {String.valueOf(objectEntry.getObjectEntryId())});
		preferences.put(
			"templateEntryId", new String[] {String.valueOf(templateEntryId)});

		_replacePortlet(
			layout, portletId, CMSMigrationPortletKeys.CMS_CONTENT_DISPLAY,
			preferences);

		cmsMigrationContext.getCMSMigrationReport(
		).increment(
			"widgets"
		);
	}

	private String _rewriteAssetEntryXml(
			CMSMigrationContext cmsMigrationContext, String assetEntryXml)
		throws Exception {

		Document document = _saxReader.read(assetEntryXml);

		Element rootElement = document.getRootElement();

		Element assetEntryTypeElement = rootElement.element("asset-entry-type");
		Element assetEntryUuidElement = rootElement.element("asset-entry-uuid");

		if ((assetEntryTypeElement == null) ||
			(assetEntryUuidElement == null)) {

			return assetEntryXml;
		}

		String sourceClassName = _infoItemReferenceMapper.getSourceClassName(
			assetEntryTypeElement.getText());

		if (sourceClassName == null) {
			return assetEntryXml;
		}

		AssetEntry assetEntry = null;

		for (long groupId :
				new long[] {
					cmsMigrationContext.getSourceGroupId(),
					_groupLocalService.getCompanyGroup(
						cmsMigrationContext.getCompanyId()
					).getGroupId()
				}) {

			assetEntry = _assetEntryLocalService.fetchEntry(
				groupId, assetEntryUuidElement.getText());

			if (assetEntry != null) {
				break;
			}
		}

		if (assetEntry == null) {
			return assetEntryXml;
		}

		ObjectEntry objectEntry = _infoItemReferenceMapper.getObjectEntry(
			cmsMigrationContext, sourceClassName, assetEntry.getClassPK());

		if (objectEntry == null) {
			return assetEntryXml;
		}

		ObjectDefinition objectDefinition =
			_infoItemReferenceMapper.getObjectDefinition(objectEntry);

		assetEntryTypeElement.setText(objectDefinition.getClassName());

		assetEntryUuidElement.setText(objectEntry.getUuid());

		return document.formattedString(StringPool.BLANK);
	}

	private void _rewriteAssetPublisher(
			CMSMigrationContext cmsMigrationContext, Layout layout,
			String portletId)
		throws Exception {

		PortletPreferences portletPreferences =
			_portletPreferencesLocalService.getPreferences(
				layout.getCompanyId(), PortletKeys.PREFS_OWNER_ID_DEFAULT,
				PortletKeys.PREFS_OWNER_TYPE_LAYOUT, layout.getPlid(),
				portletId);

		Set<String> scopeIds = new LinkedHashSet<>();

		for (String scopeId :
				portletPreferences.getValues(
					"scopeIds",
					new String[] {
						AssetPublisherHelper.SCOPE_ID_GROUP_PREFIX +
							layout.getGroupId()
					})) {

			scopeIds.add(scopeId);
		}

		String label = "The Asset Publisher on " + layout.getFriendlyURL();
		String selectionStyle = portletPreferences.getValue(
			"selectionStyle", "dynamic");

		boolean changed = false;

		if (selectionStyle.equals("manual")) {
			List<String> assetEntryXmls = new ArrayList<>();

			for (String assetEntryXml :
					portletPreferences.getValues(
						"assetEntryXml", new String[0])) {

				String newAssetEntryXml = _rewriteAssetEntryXml(
					cmsMigrationContext, assetEntryXml);

				if (!newAssetEntryXml.equals(assetEntryXml)) {
					changed = true;
				}

				assetEntryXmls.add(newAssetEntryXml);
			}

			if (changed) {
				portletPreferences.setValues(
					"assetEntryXml", assetEntryXmls.toArray(new String[0]));
			}
		}
		else if (selectionStyle.equals("dynamic") &&
				 (cmsMigrationContext.isRewriteSourceGroup() ||
				  scopeIds.contains(
					  _assetPublisherHelper.getScopeId(
						  cmsMigrationContext.getSourceGroup(),
						  layout.getGroupId())))) {

			Map<String, String[]> settings = new HashMap<>(
				portletPreferences.getMap());

			String originalSettings = _toString(settings);

			_assetQueryReferenceRewriter.rewrite(
				cmsMigrationContext, settings, label);

			if (!originalSettings.equals(_toString(settings))) {
				for (Map.Entry<String, String[]> entry : settings.entrySet()) {
					portletPreferences.setValues(
						entry.getKey(), entry.getValue());
				}

				changed = true;
			}
		}

		if (!changed) {
			return;
		}

		scopeIds.add(
			_assetPublisherHelper.getScopeId(
				cmsMigrationContext.getDepotGroup(), layout.getGroupId()));

		portletPreferences.setValues(
			"scopeIds", scopeIds.toArray(new String[0]));

		_portletPreferencesLocalService.updatePreferences(
			PortletKeys.PREFS_OWNER_ID_DEFAULT,
			PortletKeys.PREFS_OWNER_TYPE_LAYOUT, layout.getPlid(), portletId,
			portletPreferences);

		String displayStyle = portletPreferences.getValue(
			"displayStyle", StringPool.BLANK);

		if (displayStyle.startsWith(
				PortletDisplayTemplate.DISPLAY_STYLE_PREFIX)) {

			_warnJournalSpecificWidgetTemplate(
				cmsMigrationContext, displayStyle, label);
		}
		else if (displayStyle.equals("abstracts") ||
				 displayStyle.equals("full-content")) {

			cmsMigrationContext.getCMSMigrationReport(
			).addWarning(
				StringBundler.concat(
					label, " uses the ", displayStyle,
					" display style, which renders object entries with their ",
					"form view, so select a widget template to keep the look")
			);
		}

		cmsMigrationContext.getCMSMigrationReport(
		).increment(
			"widgets"
		);
	}

	private String _toString(Map<String, String[]> settings) {
		StringBuilder sb = new StringBuilder();

		for (Map.Entry<String, String[]> entry : settings.entrySet()) {
			sb.append(entry.getKey());
			sb.append(StringPool.EQUAL);
			sb.append(StringUtil.merge(entry.getValue()));
			sb.append(StringPool.NEW_LINE);
		}

		return sb.toString();
	}

	private void _warnJournalSpecificWidgetTemplate(
		CMSMigrationContext cmsMigrationContext, String displayStyle,
		String label) {

		String templateKey = displayStyle.substring(
			PortletDisplayTemplate.DISPLAY_STYLE_PREFIX.length());

		DDMTemplate ddmTemplate = _ddmTemplateLocalService.fetchTemplate(
			cmsMigrationContext.getSourceGroupId(),
			_portal.getClassNameId(AssetEntry.class), templateKey);

		if (ddmTemplate == null) {
			return;
		}

		String script = ddmTemplate.getScript();

		if (script.contains("getArticle") ||
			script.contains("JournalArticle") ||
			script.contains("getDocument") || script.contains("BlogsEntry")) {

			cmsMigrationContext.getCMSMigrationReport(
			).addWarning(
				StringBundler.concat(
					label, " uses the widget template ", templateKey,
					", which reads Web Content or Blogs APIs and must be ",
					"adapted to object entries")
			);
		}
	}

	private static final String _BLOGS_WIDGET_TEMPLATE_KEY =
		"CMS-MIGRATION-BLOGS";

	private static final Snapshot<AssetEntryLocalService>
		_assetEntryLocalServiceSnapshot = new Snapshot<>(
			PortletReferenceRewriter.class, AssetEntryLocalService.class);
	private static final Snapshot<AssetPublisherHelper>
		_assetPublisherHelperSnapshot = new Snapshot<>(
			PortletReferenceRewriter.class, AssetPublisherHelper.class);
	private static final Snapshot<DDMTemplateLocalService>
		_ddmTemplateLocalServiceSnapshot = new Snapshot<>(
			PortletReferenceRewriter.class, DDMTemplateLocalService.class);
	private static final Snapshot<FragmentEntryLinkLocalService>
		_fragmentEntryLinkLocalServiceSnapshot = new Snapshot<>(
			PortletReferenceRewriter.class,
			FragmentEntryLinkLocalService.class);
	private static final Snapshot<GroupLocalService>
		_groupLocalServiceSnapshot = new Snapshot<>(
			PortletReferenceRewriter.class, GroupLocalService.class);
	private static final Snapshot<JournalArticleLocalService>
		_journalArticleLocalServiceSnapshot = new Snapshot<>(
			PortletReferenceRewriter.class, JournalArticleLocalService.class);
	private static final Snapshot<JSONFactory> _jsonFactorySnapshot =
		new Snapshot<>(PortletReferenceRewriter.class, JSONFactory.class);
	private static final Snapshot<LayoutLocalService>
		_layoutLocalServiceSnapshot = new Snapshot<>(
			PortletReferenceRewriter.class, LayoutLocalService.class);
	private static final Snapshot<Portal> _portalSnapshot = new Snapshot<>(
		PortletReferenceRewriter.class, Portal.class);
	private static final Snapshot<PortletPreferencesLocalService>
		_portletPreferencesLocalServiceSnapshot = new Snapshot<>(
			PortletReferenceRewriter.class,
			PortletPreferencesLocalService.class);
	private static final Snapshot<SAXReader> _saxReaderSnapshot =
		new Snapshot<>(PortletReferenceRewriter.class, SAXReader.class);

	private final AssetEntryLocalService _assetEntryLocalService =
		_assetEntryLocalServiceSnapshot.get();
	private final AssetPublisherHelper _assetPublisherHelper =
		_assetPublisherHelperSnapshot.get();
	private final AssetQueryReferenceRewriter _assetQueryReferenceRewriter =
		new AssetQueryReferenceRewriter();
	private final DDMTemplateLocalService _ddmTemplateLocalService =
		_ddmTemplateLocalServiceSnapshot.get();
	private final FragmentEntryLinkLocalService _fragmentEntryLinkLocalService =
		_fragmentEntryLinkLocalServiceSnapshot.get();
	private final GroupLocalService _groupLocalService =
		_groupLocalServiceSnapshot.get();
	private final InfoItemReferenceMapper _infoItemReferenceMapper =
		new InfoItemReferenceMapper();
	private final JournalArticleLocalService _journalArticleLocalService =
		_journalArticleLocalServiceSnapshot.get();
	private final JSONFactory _jsonFactory = _jsonFactorySnapshot.get();
	private final LayoutLocalService _layoutLocalService =
		_layoutLocalServiceSnapshot.get();
	private final Portal _portal = _portalSnapshot.get();
	private final PortletPreferencesLocalService
		_portletPreferencesLocalService =
			_portletPreferencesLocalServiceSnapshot.get();
	private final SAXReader _saxReader = _saxReaderSnapshot.get();

}