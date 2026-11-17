/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.background.task;

import com.liferay.asset.display.page.service.AssetDisplayPageEntryLocalService;
import com.liferay.asset.kernel.service.AssetEntryLocalService;
import com.liferay.asset.list.service.AssetListEntryAssetEntryRelLocalService;
import com.liferay.asset.list.service.AssetListEntryLocalService;
import com.liferay.asset.list.service.AssetListEntrySegmentsEntryRelLocalService;
import com.liferay.asset.publisher.util.AssetPublisherHelper;
import com.liferay.blogs.service.BlogsEntryLocalService;
import com.liferay.cms.migration.web.internal.migration.CMSMigrationContext;
import com.liferay.cms.migration.web.internal.migration.CMSMigrationManager;
import com.liferay.cms.migration.web.internal.migration.CMSMigrationReport;
import com.liferay.cms.migration.web.internal.migration.content.BlogsEntryMigrator;
import com.liferay.cms.migration.web.internal.migration.content.FileEntryMigrator;
import com.liferay.cms.migration.web.internal.migration.content.FolderMigrator;
import com.liferay.cms.migration.web.internal.migration.content.JournalArticleMigrator;
import com.liferay.cms.migration.web.internal.migration.content.ObjectEntryScopeMigrator;
import com.liferay.cms.migration.web.internal.migration.entry.AttachmentAlternativeTextWriter;
import com.liferay.cms.migration.web.internal.migration.entry.AttachmentCopier;
import com.liferay.cms.migration.web.internal.migration.entry.DDMFormValuesConverter;
import com.liferay.cms.migration.web.internal.migration.entry.ObjectEntryWriter;
import com.liferay.cms.migration.web.internal.migration.entry.ResourcePermissionMigrator;
import com.liferay.cms.migration.web.internal.migration.reference.AssetListEntryReferenceRewriter;
import com.liferay.cms.migration.web.internal.migration.reference.AssetQueryReferenceRewriter;
import com.liferay.cms.migration.web.internal.migration.reference.HTMLReferenceRewriter;
import com.liferay.cms.migration.web.internal.migration.reference.InfoItemReferenceMapper;
import com.liferay.cms.migration.web.internal.migration.reference.JSONReferenceRewriter;
import com.liferay.cms.migration.web.internal.migration.reference.LayoutReferenceRewriter;
import com.liferay.cms.migration.web.internal.migration.reference.PortletReferenceRewriter;
import com.liferay.cms.migration.web.internal.migration.reference.SiteNavigationMenuItemReferenceRewriter;
import com.liferay.cms.migration.web.internal.migration.reference.WidgetTemplateScriptRewriter;
import com.liferay.cms.migration.web.internal.migration.structure.ObjectDefinitionMigrator;
import com.liferay.cms.migration.web.internal.migration.structure.ObjectDefinitionScopeConverter;
import com.liferay.cms.migration.web.internal.migration.structure.StructureEditPageResetter;
import com.liferay.cms.migration.web.internal.migration.template.DisplayPageTemplateMigrator;
import com.liferay.cms.migration.web.internal.migration.template.JournalTemplateMigrator;
import com.liferay.cms.migration.web.internal.migration.template.WidgetTemplateMigrator;
import com.liferay.depot.model.DepotEntry;
import com.liferay.depot.service.DepotEntryGroupRelLocalService;
import com.liferay.depot.service.DepotEntryLocalService;
import com.liferay.document.library.helper.DLURLHelper;
import com.liferay.document.library.kernel.service.DLAppLocalService;
import com.liferay.document.library.kernel.service.DLFileEntryLocalService;
import com.liferay.document.library.kernel.service.DLFileEntryMetadataLocalService;
import com.liferay.document.library.kernel.service.DLFileEntryTypeLocalService;
import com.liferay.document.library.kernel.service.DLFolderLocalService;
import com.liferay.document.library.video.external.shortcut.resolver.DLVideoExternalShortcutResolver;
import com.liferay.dynamic.data.mapping.service.DDMFieldLocalService;
import com.liferay.dynamic.data.mapping.service.DDMStructureLocalService;
import com.liferay.dynamic.data.mapping.service.DDMStructureVersionLocalService;
import com.liferay.dynamic.data.mapping.service.DDMTemplateLocalService;
import com.liferay.dynamic.data.mapping.storage.DDMStorageEngineManager;
import com.liferay.fragment.service.FragmentEntryLinkLocalService;
import com.liferay.friendly.url.service.FriendlyURLEntryLocalService;
import com.liferay.journal.service.JournalArticleLocalService;
import com.liferay.journal.service.JournalArticleResourceLocalService;
import com.liferay.journal.service.JournalFolderLocalService;
import com.liferay.layout.page.template.service.LayoutPageTemplateEntryLocalService;
import com.liferay.layout.page.template.service.LayoutPageTemplateStructureLocalService;
import com.liferay.layout.page.template.service.LayoutPageTemplateStructureRelLocalService;
import com.liferay.list.type.service.ListTypeDefinitionLocalService;
import com.liferay.list.type.service.ListTypeEntryLocalService;
import com.liferay.object.field.attachment.AttachmentManager;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectDefinitionSettingLocalService;
import com.liferay.object.service.ObjectEntryFolderLocalService;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.object.service.ObjectEntryVersionLocalService;
import com.liferay.object.service.ObjectFieldLocalService;
import com.liferay.object.service.ObjectFieldSettingLocalService;
import com.liferay.object.service.ObjectFolderLocalService;
import com.liferay.object.service.ObjectRelationshipLocalService;
import com.liferay.portal.kernel.backgroundtask.BackgroundTask;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskExecutor;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskResult;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskStatusMessageSender;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskStatusMessageTranslator;
import com.liferay.portal.kernel.backgroundtask.BaseBackgroundTaskExecutor;
import com.liferay.portal.kernel.backgroundtask.constants.BackgroundTaskConstants;
import com.liferay.portal.kernel.backgroundtask.display.BackgroundTaskDisplay;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.security.auth.PrincipalThreadLocal;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactory;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.security.permission.ResourceActions;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.service.ImageLocalService;
import com.liferay.portal.kernel.service.LayoutLocalService;
import com.liferay.portal.kernel.service.PortletPreferenceValueLocalService;
import com.liferay.portal.kernel.service.PortletPreferencesLocalService;
import com.liferay.portal.kernel.service.ResourceLocalService;
import com.liferay.portal.kernel.service.ResourcePermissionLocalService;
import com.liferay.portal.kernel.service.RoleLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.ServiceContextThreadLocal;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.xml.SAXReader;
import com.liferay.reading.time.calculator.ReadingTimeCalculator;
import com.liferay.reading.time.service.ReadingTimeEntryLocalService;
import com.liferay.site.navigation.service.SiteNavigationMenuItemLocalService;
import com.liferay.site.navigation.service.SiteNavigationMenuLocalService;
import com.liferay.template.service.TemplateEntryLocalService;

import java.io.Serializable;

import java.util.Map;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Lino Alves
 */
@Component(
	property = "background.task.executor.class.name=com.liferay.cms.migration.web.internal.background.task.CMSMigrationBackgroundTaskExecutor",
	service = BackgroundTaskExecutor.class
)
public class CMSMigrationBackgroundTaskExecutor
	extends BaseBackgroundTaskExecutor {

	public CMSMigrationBackgroundTaskExecutor() {
		setIsolationLevel(BackgroundTaskConstants.ISOLATION_LEVEL_COMPANY);
	}

	@Override
	public BackgroundTaskExecutor clone() {
		return this;
	}

	@Override
	public BackgroundTaskResult execute(BackgroundTask backgroundTask)
		throws Exception {

		Map<String, Serializable> taskContextMap =
			backgroundTask.getTaskContextMap();

		User user = _userLocalService.getUser(backgroundTask.getUserId());

		Group sourceGroup = _groupLocalService.getGroup(
			GetterUtil.getLong(taskContextMap.get("sourceGroupId")));

		DepotEntry depotEntry = _depotEntryLocalService.getDepotEntry(
			GetterUtil.getLong(taskContextMap.get("depotEntryId")));

		ServiceContext serviceContext = new ServiceContext();

		serviceContext.setAddGroupPermissions(true);
		serviceContext.setAddGuestPermissions(true);
		serviceContext.setCompanyId(user.getCompanyId());
		serviceContext.setLanguageId(sourceGroup.getDefaultLanguageId());
		serviceContext.setScopeGroupId(sourceGroup.getGroupId());
		serviceContext.setUserId(user.getUserId());

		String name = PrincipalThreadLocal.getName();
		PermissionChecker permissionChecker =
			PermissionThreadLocal.getPermissionChecker();

		CMSMigrationBackgroundTaskStatusMessageSender
			cmsMigrationBackgroundTaskStatusMessageSender =
				new CMSMigrationBackgroundTaskStatusMessageSender(
					_backgroundTaskStatusMessageSender);

		CMSMigrationManager cmsMigrationManager = _createCMSMigrationManager(
			cmsMigrationBackgroundTaskStatusMessageSender);

		CMSMigrationReport cmsMigrationReport = new CMSMigrationReport();

		try {
			PrincipalThreadLocal.setName(user.getUserId());
			PermissionThreadLocal.setPermissionChecker(
				_permissionCheckerFactory.create(user));
			ServiceContextThreadLocal.pushServiceContext(serviceContext);

			cmsMigrationManager.migrate(
				new CMSMigrationContext(
					depotEntry, depotEntry.getGroup(),
					GetterUtil.getBoolean(
						taskContextMap.get("migrateBlogsEntries")),
					GetterUtil.getBoolean(
						taskContextMap.get("migrateFileEntries")),
					GetterUtil.getBoolean(
						taskContextMap.get("migrateJournalArticles")),
					GetterUtil.getBoolean(
						taskContextMap.get("migrateObjectEntries")),
					GetterUtil.getBoolean(
						taskContextMap.get("migrateRepeatableFields")),
					cmsMigrationReport,
					GetterUtil.getBoolean(taskContextMap.get("rewritePages")),
					serviceContext,
					GetterUtil.getBoolean(
						taskContextMap.get("skipUnchangedEntries")),
					sourceGroup,
					cmsMigrationBackgroundTaskStatusMessageSender::
						sendItemStatusMessage,
					StringUtil.split(
						GetterUtil.getString(
							taskContextMap.get("titleObjectFieldNames"))),
					user.getUserId()));
		}
		catch (Exception exception) {
			cmsMigrationReport.addError(
				"The migration stopped: " + exception.getMessage());
		}
		finally {
			PrincipalThreadLocal.setName(name);
			PermissionThreadLocal.setPermissionChecker(permissionChecker);
			ServiceContextThreadLocal.popServiceContext();
		}

		int status = BackgroundTaskConstants.STATUS_SUCCESSFUL;

		if (cmsMigrationReport.hasErrors()) {
			status = BackgroundTaskConstants.STATUS_COMPLETED_WITH_ERRORS;
		}

		return new BackgroundTaskResult(
			status,
			cmsMigrationReport.toJSONObject(
			).toString());
	}

	@Override
	public BackgroundTaskDisplay getBackgroundTaskDisplay(
		BackgroundTask backgroundTask) {

		return null;
	}

	@Override
	public BackgroundTaskStatusMessageTranslator
		getBackgroundTaskStatusMessageTranslator() {

		return _backgroundTaskStatusMessageTranslator;
	}

	private CMSMigrationManager _createCMSMigrationManager(
			CMSMigrationBackgroundTaskStatusMessageSender
				cmsMigrationBackgroundTaskStatusMessageSender)
		throws Exception {

		InfoItemReferenceMapper infoItemReferenceMapper =
			new InfoItemReferenceMapper(
				_blogsEntryLocalService, _dlFileEntryLocalService,
				_groupLocalService, _journalArticleLocalService,
				_objectDefinitionLocalService, _objectFieldLocalService,
				_portal);

		AssetQueryReferenceRewriter assetQueryReferenceRewriter =
			new AssetQueryReferenceRewriter(
				_ddmStructureLocalService, infoItemReferenceMapper, _portal);

		AssetListEntryReferenceRewriter assetListEntryReferenceRewriter =
			new AssetListEntryReferenceRewriter(
				_assetEntryLocalService,
				_assetListEntryAssetEntryRelLocalService,
				_assetListEntryLocalService,
				_assetListEntrySegmentsEntryRelLocalService,
				assetQueryReferenceRewriter, infoItemReferenceMapper);

		AttachmentCopier attachmentCopier = new AttachmentCopier(
			_attachmentManager, _dlAppLocalService, _imageLocalService,
			_objectFieldLocalService);

		HTMLReferenceRewriter htmlReferenceRewriter = new HTMLReferenceRewriter(
			_blogsEntryLocalService, _dlAppLocalService, _dlURLHelper,
			_friendlyURLEntryLocalService, _groupLocalService,
			_journalArticleLocalService, _objectDefinitionLocalService,
			_portal);

		ObjectDefinitionMigrator objectDefinitionMigrator =
			new ObjectDefinitionMigrator(
				_ddmStructureLocalService, _ddmStructureVersionLocalService,
				_language, _listTypeDefinitionLocalService,
				_listTypeEntryLocalService, _objectDefinitionLocalService,
				_objectDefinitionSettingLocalService, _objectEntryLocalService,
				_objectFieldLocalService, _objectFieldSettingLocalService,
				_objectFolderLocalService, _objectRelationshipLocalService);

		ResourcePermissionMigrator resourcePermissionMigrator =
			new ResourcePermissionMigrator(
				_resourcePermissionLocalService, _roleLocalService);

		ObjectEntryWriter objectEntryWriter = new ObjectEntryWriter(
			_assetEntryLocalService, _language, _objectEntryLocalService,
			_objectFieldLocalService, _objectFieldSettingLocalService, _portal,
			resourcePermissionMigrator, _userLocalService);

		BlogsEntryMigrator blogsEntryMigrator = new BlogsEntryMigrator(
			attachmentCopier, _blogsEntryLocalService, htmlReferenceRewriter,
			objectDefinitionMigrator, objectEntryWriter, _portal,
			_readingTimeCalculator, _readingTimeEntryLocalService);

		JSONReferenceRewriter jsonReferenceRewriter = new JSONReferenceRewriter(
			_dlAppLocalService, htmlReferenceRewriter, infoItemReferenceMapper,
			_journalArticleLocalService, _portal);

		LayoutReferenceRewriter layoutReferenceRewriter =
			new LayoutReferenceRewriter(
				_fragmentEntryLinkLocalService, infoItemReferenceMapper,
				_jsonFactory, jsonReferenceRewriter,
				_layoutPageTemplateStructureLocalService,
				_layoutPageTemplateStructureRelLocalService);

		JournalTemplateMigrator journalTemplateMigrator =
			new JournalTemplateMigrator(
				_ddmTemplateLocalService, infoItemReferenceMapper, _portal,
				_templateEntryLocalService);

		DisplayPageTemplateMigrator displayPageTemplateMigrator =
			new DisplayPageTemplateMigrator(
				_assetDisplayPageEntryLocalService, _ddmStructureLocalService,
				_dlFileEntryTypeLocalService, infoItemReferenceMapper,
				journalTemplateMigrator, _layoutLocalService,
				_layoutPageTemplateEntryLocalService, layoutReferenceRewriter,
				objectDefinitionMigrator, _portal);

		AttachmentAlternativeTextWriter attachmentAlternativeTextWriter =
			new AttachmentAlternativeTextWriter(_dlAppLocalService);

		DDMFormValuesConverter ddmFormValuesConverter =
			new DDMFormValuesConverter(
				attachmentAlternativeTextWriter, _dlAppLocalService,
				htmlReferenceRewriter, _journalArticleLocalService,
				_jsonFactory, objectDefinitionMigrator);

		FileEntryMigrator fileEntryMigrator = new FileEntryMigrator(
			attachmentAlternativeTextWriter, attachmentCopier,
			ddmFormValuesConverter, _ddmStorageEngineManager,
			_ddmStructureLocalService, _dlAppLocalService,
			_dlFileEntryLocalService, _dlFileEntryMetadataLocalService,
			_dlFileEntryTypeLocalService, _dlVideoExternalShortcutResolver,
			_friendlyURLEntryLocalService, objectDefinitionMigrator,
			objectEntryWriter, _objectFieldLocalService, _portal);

		FolderMigrator folderMigrator = new FolderMigrator(
			_dlFolderLocalService, _journalFolderLocalService,
			_objectEntryFolderLocalService);

		JournalArticleMigrator journalArticleMigrator =
			new JournalArticleMigrator(
				attachmentCopier, _ddmFieldLocalService, ddmFormValuesConverter,
				_journalArticleLocalService,
				_journalArticleResourceLocalService, _jsonFactory, _language,
				objectDefinitionMigrator, _objectEntryLocalService,
				objectEntryWriter);

		ObjectEntryScopeMigrator objectEntryScopeMigrator =
			new ObjectEntryScopeMigrator(
				_assetEntryLocalService, attachmentCopier, _dlAppLocalService,
				_objectDefinitionLocalService,
				new ObjectDefinitionScopeConverter(
					_objectDefinitionLocalService, objectDefinitionMigrator,
					_objectDefinitionSettingLocalService,
					_objectEntryLocalService, _objectFieldLocalService,
					_objectFieldSettingLocalService, _objectFolderLocalService),
				_objectEntryFolderLocalService, _objectEntryLocalService,
				_objectEntryVersionLocalService, _objectFieldLocalService,
				_objectRelationshipLocalService);

		WidgetTemplateMigrator widgetTemplateMigrator =
			new WidgetTemplateMigrator(
				_ddmTemplateLocalService, _portal,
				_portletPreferencesLocalService,
				_portletPreferenceValueLocalService, _saxReader,
				new WidgetTemplateScriptRewriter(_jsonFactory));

		PortletReferenceRewriter portletReferenceRewriter =
			new PortletReferenceRewriter(
				_assetEntryLocalService, _assetPublisherHelper,
				assetQueryReferenceRewriter, _fragmentEntryLinkLocalService,
				_groupLocalService, infoItemReferenceMapper,
				_journalArticleLocalService, _jsonFactory, _layoutLocalService,
				_portal, _portletPreferencesLocalService, _resourceActions,
				_resourceLocalService, _resourcePermissionLocalService,
				_saxReader, widgetTemplateMigrator);

		SiteNavigationMenuItemReferenceRewriter
			siteNavigationMenuItemReferenceRewriter =
				new SiteNavigationMenuItemReferenceRewriter(
					infoItemReferenceMapper,
					_siteNavigationMenuItemLocalService,
					_siteNavigationMenuLocalService);

		StructureEditPageResetter structureEditPageResetter =
			new StructureEditPageResetter(
				_groupLocalService, _layoutLocalService,
				_layoutPageTemplateEntryLocalService, _portal);

		return new CMSMigrationManager(
			_assetEntryLocalService, assetListEntryReferenceRewriter,
			blogsEntryMigrator, cmsMigrationBackgroundTaskStatusMessageSender,
			_depotEntryGroupRelLocalService, displayPageTemplateMigrator,
			fileEntryMigrator, folderMigrator, _groupLocalService,
			htmlReferenceRewriter, journalArticleMigrator,
			journalTemplateMigrator, _layoutLocalService,
			layoutReferenceRewriter, _objectDefinitionLocalService,
			objectDefinitionMigrator, _objectEntryLocalService,
			objectEntryScopeMigrator, _objectFieldLocalService,
			portletReferenceRewriter, siteNavigationMenuItemReferenceRewriter,
			structureEditPageResetter, widgetTemplateMigrator);
	}

	@Reference
	private AssetDisplayPageEntryLocalService
		_assetDisplayPageEntryLocalService;

	@Reference
	private AssetEntryLocalService _assetEntryLocalService;

	@Reference
	private AssetListEntryAssetEntryRelLocalService
		_assetListEntryAssetEntryRelLocalService;

	@Reference
	private AssetListEntryLocalService _assetListEntryLocalService;

	@Reference
	private AssetListEntrySegmentsEntryRelLocalService
		_assetListEntrySegmentsEntryRelLocalService;

	@Reference
	private AssetPublisherHelper _assetPublisherHelper;

	@Reference
	private AttachmentManager _attachmentManager;

	@Reference
	private BackgroundTaskStatusMessageSender
		_backgroundTaskStatusMessageSender;

	private final BackgroundTaskStatusMessageTranslator
		_backgroundTaskStatusMessageTranslator =
			new CMSMigrationBackgroundTaskStatusMessageTranslator();

	@Reference
	private BlogsEntryLocalService _blogsEntryLocalService;

	@Reference
	private DDMFieldLocalService _ddmFieldLocalService;

	@Reference
	private DDMStorageEngineManager _ddmStorageEngineManager;

	@Reference
	private DDMStructureLocalService _ddmStructureLocalService;

	@Reference
	private DDMStructureVersionLocalService _ddmStructureVersionLocalService;

	@Reference
	private DDMTemplateLocalService _ddmTemplateLocalService;

	@Reference
	private DepotEntryGroupRelLocalService _depotEntryGroupRelLocalService;

	@Reference
	private DepotEntryLocalService _depotEntryLocalService;

	@Reference
	private DLAppLocalService _dlAppLocalService;

	@Reference
	private DLFileEntryLocalService _dlFileEntryLocalService;

	@Reference
	private DLFileEntryMetadataLocalService _dlFileEntryMetadataLocalService;

	@Reference
	private DLFileEntryTypeLocalService _dlFileEntryTypeLocalService;

	@Reference
	private DLFolderLocalService _dlFolderLocalService;

	@Reference
	private DLURLHelper _dlURLHelper;

	@Reference
	private DLVideoExternalShortcutResolver _dlVideoExternalShortcutResolver;

	@Reference
	private FragmentEntryLinkLocalService _fragmentEntryLinkLocalService;

	@Reference
	private FriendlyURLEntryLocalService _friendlyURLEntryLocalService;

	@Reference
	private GroupLocalService _groupLocalService;

	@Reference
	private ImageLocalService _imageLocalService;

	@Reference
	private JournalArticleLocalService _journalArticleLocalService;

	@Reference
	private JournalArticleResourceLocalService
		_journalArticleResourceLocalService;

	@Reference
	private JournalFolderLocalService _journalFolderLocalService;

	@Reference
	private JSONFactory _jsonFactory;

	@Reference
	private Language _language;

	@Reference
	private LayoutLocalService _layoutLocalService;

	@Reference
	private LayoutPageTemplateEntryLocalService
		_layoutPageTemplateEntryLocalService;

	@Reference
	private LayoutPageTemplateStructureLocalService
		_layoutPageTemplateStructureLocalService;

	@Reference
	private LayoutPageTemplateStructureRelLocalService
		_layoutPageTemplateStructureRelLocalService;

	@Reference
	private ListTypeDefinitionLocalService _listTypeDefinitionLocalService;

	@Reference
	private ListTypeEntryLocalService _listTypeEntryLocalService;

	@Reference
	private ObjectDefinitionLocalService _objectDefinitionLocalService;

	@Reference
	private ObjectDefinitionSettingLocalService
		_objectDefinitionSettingLocalService;

	@Reference
	private ObjectEntryFolderLocalService _objectEntryFolderLocalService;

	@Reference
	private ObjectEntryLocalService _objectEntryLocalService;

	@Reference
	private ObjectEntryVersionLocalService _objectEntryVersionLocalService;

	@Reference
	private ObjectFieldLocalService _objectFieldLocalService;

	@Reference
	private ObjectFieldSettingLocalService _objectFieldSettingLocalService;

	@Reference
	private ObjectFolderLocalService _objectFolderLocalService;

	@Reference
	private ObjectRelationshipLocalService _objectRelationshipLocalService;

	@Reference
	private PermissionCheckerFactory _permissionCheckerFactory;

	@Reference
	private Portal _portal;

	@Reference
	private PortletPreferenceValueLocalService
		_portletPreferenceValueLocalService;

	@Reference
	private PortletPreferencesLocalService _portletPreferencesLocalService;

	@Reference
	private ReadingTimeCalculator _readingTimeCalculator;

	@Reference
	private ReadingTimeEntryLocalService _readingTimeEntryLocalService;

	@Reference
	private ResourceActions _resourceActions;

	@Reference
	private ResourceLocalService _resourceLocalService;

	@Reference
	private ResourcePermissionLocalService _resourcePermissionLocalService;

	@Reference
	private RoleLocalService _roleLocalService;

	@Reference
	private SAXReader _saxReader;

	@Reference
	private SiteNavigationMenuItemLocalService
		_siteNavigationMenuItemLocalService;

	@Reference
	private SiteNavigationMenuLocalService _siteNavigationMenuLocalService;

	@Reference
	private TemplateEntryLocalService _templateEntryLocalService;

	@Reference
	private UserLocalService _userLocalService;

}