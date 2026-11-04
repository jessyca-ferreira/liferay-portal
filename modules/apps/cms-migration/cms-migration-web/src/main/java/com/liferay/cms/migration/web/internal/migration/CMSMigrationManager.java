/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.migration;

import com.liferay.asset.kernel.model.AssetEntry;
import com.liferay.asset.kernel.service.AssetEntryLocalService;
import com.liferay.cms.migration.web.internal.background.task.CMSMigrationBackgroundTaskStatusMessageSender;
import com.liferay.cms.migration.web.internal.constants.CMSMigrationConstants;
import com.liferay.cms.migration.web.internal.content.BlogsEntryMigrator;
import com.liferay.cms.migration.web.internal.content.FileEntryMigrator;
import com.liferay.cms.migration.web.internal.content.FolderMigrator;
import com.liferay.cms.migration.web.internal.content.JournalArticleMigrator;
import com.liferay.cms.migration.web.internal.reference.AssetListEntryReferenceRewriter;
import com.liferay.cms.migration.web.internal.reference.DisplayPageTemplateMigrator;
import com.liferay.cms.migration.web.internal.reference.HTMLReferenceRewriter;
import com.liferay.cms.migration.web.internal.reference.LayoutReferenceRewriter;
import com.liferay.cms.migration.web.internal.reference.PortletReferenceRewriter;
import com.liferay.cms.migration.web.internal.reference.SiteNavigationMenuItemReferenceRewriter;
import com.liferay.cms.migration.web.internal.template.TemplateMigrator;
import com.liferay.depot.service.DepotEntryGroupRelLocalService;
import com.liferay.document.library.kernel.model.DLFileEntry;
import com.liferay.object.constants.ObjectFieldConstants;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.model.ObjectField;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.object.service.ObjectFieldLocalService;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.dao.orm.DynamicQuery;
import com.liferay.portal.kernel.dao.orm.OrderFactoryUtil;
import com.liferay.portal.kernel.dao.orm.ProjectionFactoryUtil;
import com.liferay.portal.kernel.dao.orm.RestrictionsFactoryUtil;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.module.service.Snapshot;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.service.LayoutLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.workflow.WorkflowConstants;

import java.io.Serializable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runs a migration from classic content to the CMS. Content migrates first;
 * then, when requested, every page reference to the classic content is
 * repointed to the migrated entries.
 *
 * @author Lino Alves
 */
public class CMSMigrationManager {

	public void migrate(CMSMigrationContext cmsMigrationContext)
		throws Exception {

		_depotEntryGroupRelLocalService.addDepotEntryGroupRel(
			cmsMigrationContext.getDepotEntry(
			).getDepotEntryId(),
			cmsMigrationContext.getSourceGroupId());

		cmsMigrationContext.setFileEntryResolver(
			fileEntry -> {
				if (!cmsMigrationContext.isRewriteSourceGroup() &&
					(fileEntry.getGroupId() !=
						cmsMigrationContext.getSourceGroupId())) {

					return cmsMigrationContext.getObjectEntry(
						DLFileEntry.class.getName(),
						fileEntry.getFileEntryId());
				}

				return _fileEntryMigrator.migrate(
					cmsMigrationContext, fileEntry);
			});

		_sendPhase(CMSMigrationConstants.PHASE_FOLDERS, 0);

		if (cmsMigrationContext.isMigrateFileEntries()) {
			_folderMigrator.migrateDLFolders(cmsMigrationContext);
		}
		else {
			_folderMigrator.registerDLRootFolder(cmsMigrationContext);
		}

		if (cmsMigrationContext.isMigrateBlogsEntries() ||
			cmsMigrationContext.isMigrateJournalArticles()) {

			_folderMigrator.migrateJournalFolders(cmsMigrationContext);
		}

		if (cmsMigrationContext.isMigrateFileEntries()) {
			_sendPhase(
				CMSMigrationConstants.PHASE_FILE_ENTRIES,
				_fileEntryMigrator.getCount(cmsMigrationContext));

			_fileEntryMigrator.migrateAll(cmsMigrationContext);
		}

		if (cmsMigrationContext.isMigrateJournalArticles()) {
			_sendPhase(
				CMSMigrationConstants.PHASE_JOURNAL_ARTICLES,
				_journalArticleMigrator.getJournalArticleResources(
					cmsMigrationContext
				).size());

			_journalArticleMigrator.migrateAll(cmsMigrationContext);
		}

		if (cmsMigrationContext.isMigrateBlogsEntries()) {
			_sendPhase(
				CMSMigrationConstants.PHASE_BLOGS_ENTRIES,
				_blogsEntryMigrator.getBlogsEntries(
					cmsMigrationContext
				).size());

			_blogsEntryMigrator.migrateAll(cmsMigrationContext);
		}

		_rewriteContentLinks(cmsMigrationContext);

		if (cmsMigrationContext.isMigrateJournalArticles()) {
			_sendPhase(
				CMSMigrationConstants.PHASE_TEMPLATES,
				cmsMigrationContext.getDDMStructures(
				).size());

			_templateMigrator.migrate(cmsMigrationContext);
		}

		if (!cmsMigrationContext.isRewritePages()) {
			return;
		}

		_rewriteReferences(cmsMigrationContext);

		Group sourceGroup = cmsMigrationContext.getSourceGroup();

		if (!sourceGroup.isCompany()) {
			return;
		}

		try {
			for (Group group : _getReferencingGroups(cmsMigrationContext)) {
				cmsMigrationContext.setRewriteGroup(group);

				_rewriteReferences(cmsMigrationContext);

				Group liveGroup = group;

				if (group.isStagingGroup()) {
					liveGroup = group.getLiveGroup();
				}

				if (liveGroup.isSite()) {
					_depotEntryGroupRelLocalService.addDepotEntryGroupRel(
						cmsMigrationContext.getDepotEntry(
						).getDepotEntryId(),
						liveGroup.getGroupId());
				}
			}
		}
		finally {
			cmsMigrationContext.setRewriteGroup(sourceGroup);
		}
	}

	private List<Layout> _getLayouts(long groupId) {
		DynamicQuery dynamicQuery = _layoutLocalService.dynamicQuery();

		dynamicQuery.add(RestrictionsFactoryUtil.eq("groupId", groupId));

		return _layoutLocalService.dynamicQuery(dynamicQuery);
	}

	/**
	 * Returns every group, other than the source group and the target space,
	 * that has pages. Their pages may reference the content of the Global
	 * site.
	 */
	private List<Group> _getReferencingGroups(
		CMSMigrationContext cmsMigrationContext) {

		DynamicQuery dynamicQuery = _layoutLocalService.dynamicQuery();

		dynamicQuery.add(
			RestrictionsFactoryUtil.eq(
				"companyId", cmsMigrationContext.getCompanyId()));
		dynamicQuery.add(
			RestrictionsFactoryUtil.ne(
				"groupId", cmsMigrationContext.getSourceGroupId()));
		dynamicQuery.add(
			RestrictionsFactoryUtil.ne(
				"groupId", cmsMigrationContext.getDepotGroupId()));
		dynamicQuery.addOrder(OrderFactoryUtil.asc("groupId"));
		dynamicQuery.setProjection(
			ProjectionFactoryUtil.distinct(
				ProjectionFactoryUtil.property("groupId")));

		List<Group> groups = new ArrayList<>();

		for (Object groupId : _layoutLocalService.dynamicQuery(dynamicQuery)) {
			Group group = _groupLocalService.fetchGroup((Long)groupId);

			if ((group != null) && !group.isDepot()) {
				groups.add(group);
			}
		}

		return groups;
	}

	private void _rewriteContentLinks(CMSMigrationContext cmsMigrationContext) {
		for (ObjectEntry objectEntry :
				new ArrayList<>(cmsMigrationContext.getObjectEntries())) {

			try {
				_rewriteContentLinks(cmsMigrationContext, objectEntry);
			}
			catch (Exception exception) {
				cmsMigrationContext.getCMSMigrationReport(
				).addWarning(
					StringBundler.concat(
						"Unable to rewrite the links of ",
						objectEntry.getExternalReferenceCode(), ": ",
						exception.getMessage())
				);
			}
		}
	}

	private void _rewriteContentLinks(
			CMSMigrationContext cmsMigrationContext, ObjectEntry objectEntry)
		throws Exception {

		Map<String, Serializable> values = new HashMap<>(
			objectEntry.getValues());

		boolean changed = false;

		for (ObjectField objectField :
				_objectFieldLocalService.getObjectFields(
					objectEntry.getObjectDefinitionId())) {

			if (!objectField.compareBusinessType(
					ObjectFieldConstants.BUSINESS_TYPE_RICH_TEXT)) {

				continue;
			}

			Object value = values.get(objectField.getI18nObjectFieldName());

			if (!(value instanceof Map)) {
				continue;
			}

			HashMap<String, Serializable> localizedValues = new HashMap<>(
				(Map<String, Serializable>)value);

			for (Map.Entry<String, Serializable> entry :
					localizedValues.entrySet()) {

				String html = String.valueOf(entry.getValue());

				String newHTML = _htmlReferenceRewriter.rewriteContentLinks(
					cmsMigrationContext, html);

				if (!Objects.equals(html, newHTML)) {
					entry.setValue(newHTML);

					changed = true;
				}
			}

			values.put(objectField.getI18nObjectFieldName(), localizedValues);
		}

		if (!changed) {
			return;
		}

		ServiceContext serviceContext =
			(ServiceContext)cmsMigrationContext.getServiceContext(
			).clone();

		ObjectDefinition objectDefinition =
			_objectDefinitionLocalService.getObjectDefinition(
				objectEntry.getObjectDefinitionId());

		AssetEntry assetEntry = _assetEntryLocalService.fetchEntry(
			objectDefinition.getClassName(), objectEntry.getObjectEntryId());

		if (assetEntry != null) {
			serviceContext.setAssetCategoryIds(assetEntry.getCategoryIds());
			serviceContext.setAssetPriority(assetEntry.getPriority());
			serviceContext.setAssetTagNames(assetEntry.getTagNames());
		}

		if (objectEntry.isApproved()) {
			serviceContext.setWorkflowAction(WorkflowConstants.ACTION_PUBLISH);
		}
		else {
			serviceContext.setWorkflowAction(
				WorkflowConstants.ACTION_SAVE_DRAFT);
		}

		ObjectEntry updatedObjectEntry =
			_objectEntryLocalService.updateObjectEntry(
				cmsMigrationContext.getUserId(), objectEntry.getObjectEntryId(),
				objectEntry.getObjectEntryFolderId(), values, serviceContext);

		for (Map.Entry<String, ObjectEntry> entry :
				cmsMigrationContext.getObjectEntriesMap(
				).entrySet()) {

			ObjectEntry entryObjectEntry = entry.getValue();

			if (entryObjectEntry.getObjectEntryId() ==
					updatedObjectEntry.getObjectEntryId()) {

				entry.setValue(updatedObjectEntry);
			}
		}
	}

	private void _rewriteReferences(CMSMigrationContext cmsMigrationContext) {
		_sendPhase(CMSMigrationConstants.PHASE_DISPLAY_PAGE_TEMPLATES, 0);

		_assetListEntryReferenceRewriter.rewrite(cmsMigrationContext);

		_displayPageTemplateMigrator.migrate(cmsMigrationContext);

		List<Layout> layouts = _getLayouts(
			cmsMigrationContext.getRewriteGroupId());

		_sendPhase(CMSMigrationConstants.PHASE_PAGES, layouts.size());

		for (Layout layout : layouts) {
			cmsMigrationContext.sendStatus(layout.getFriendlyURL());

			try {
				_layoutReferenceRewriter.rewrite(
					cmsMigrationContext, layout, null);
				_portletReferenceRewriter.rewrite(cmsMigrationContext, layout);
			}
			catch (Exception exception) {
				cmsMigrationContext.getCMSMigrationReport(
				).addError(
					StringBundler.concat(
						"Unable to rewrite the page ", layout.getFriendlyURL(),
						": ", exception.getMessage())
				);
			}
		}

		_siteNavigationMenuItemReferenceRewriter.rewrite(cmsMigrationContext);
	}

	private void _sendPhase(String phase, long total) {
		_cmsMigrationBackgroundTaskStatusMessageSender.sendPhaseStatusMessage(
			phase, total);
	}

	private static final Snapshot<AssetEntryLocalService>
		_assetEntryLocalServiceSnapshot = new Snapshot<>(
			CMSMigrationManager.class, AssetEntryLocalService.class);
	private static final Snapshot<DepotEntryGroupRelLocalService>
		_depotEntryGroupRelLocalServiceSnapshot = new Snapshot<>(
			CMSMigrationManager.class, DepotEntryGroupRelLocalService.class);
	private static final Snapshot<GroupLocalService>
		_groupLocalServiceSnapshot = new Snapshot<>(
			CMSMigrationManager.class, GroupLocalService.class);
	private static final Snapshot<LayoutLocalService>
		_layoutLocalServiceSnapshot = new Snapshot<>(
			CMSMigrationManager.class, LayoutLocalService.class);
	private static final Snapshot<ObjectDefinitionLocalService>
		_objectDefinitionLocalServiceSnapshot = new Snapshot<>(
			CMSMigrationManager.class, ObjectDefinitionLocalService.class);
	private static final Snapshot<ObjectEntryLocalService>
		_objectEntryLocalServiceSnapshot = new Snapshot<>(
			CMSMigrationManager.class, ObjectEntryLocalService.class);
	private static final Snapshot<ObjectFieldLocalService>
		_objectFieldLocalServiceSnapshot = new Snapshot<>(
			CMSMigrationManager.class, ObjectFieldLocalService.class);

	private final AssetEntryLocalService _assetEntryLocalService =
		_assetEntryLocalServiceSnapshot.get();
	private final AssetListEntryReferenceRewriter
		_assetListEntryReferenceRewriter =
			new AssetListEntryReferenceRewriter();
	private final BlogsEntryMigrator _blogsEntryMigrator =
		new BlogsEntryMigrator();
	private final CMSMigrationBackgroundTaskStatusMessageSender
		_cmsMigrationBackgroundTaskStatusMessageSender =
			new CMSMigrationBackgroundTaskStatusMessageSender();
	private final DepotEntryGroupRelLocalService
		_depotEntryGroupRelLocalService =
			_depotEntryGroupRelLocalServiceSnapshot.get();
	private final DisplayPageTemplateMigrator _displayPageTemplateMigrator =
		new DisplayPageTemplateMigrator();
	private final FileEntryMigrator _fileEntryMigrator =
		new FileEntryMigrator();
	private final FolderMigrator _folderMigrator = new FolderMigrator();
	private final GroupLocalService _groupLocalService =
		_groupLocalServiceSnapshot.get();
	private final HTMLReferenceRewriter _htmlReferenceRewriter =
		new HTMLReferenceRewriter();
	private final JournalArticleMigrator _journalArticleMigrator =
		new JournalArticleMigrator();
	private final LayoutLocalService _layoutLocalService =
		_layoutLocalServiceSnapshot.get();
	private final LayoutReferenceRewriter _layoutReferenceRewriter =
		new LayoutReferenceRewriter();
	private final ObjectDefinitionLocalService _objectDefinitionLocalService =
		_objectDefinitionLocalServiceSnapshot.get();
	private final ObjectEntryLocalService _objectEntryLocalService =
		_objectEntryLocalServiceSnapshot.get();
	private final ObjectFieldLocalService _objectFieldLocalService =
		_objectFieldLocalServiceSnapshot.get();
	private final PortletReferenceRewriter _portletReferenceRewriter =
		new PortletReferenceRewriter();
	private final SiteNavigationMenuItemReferenceRewriter
		_siteNavigationMenuItemReferenceRewriter =
			new SiteNavigationMenuItemReferenceRewriter();
	private final TemplateMigrator _templateMigrator = new TemplateMigrator();

}