/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.background.task.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.asset.kernel.model.AssetEntry;
import com.liferay.asset.kernel.service.AssetEntryLocalService;
import com.liferay.asset.publisher.constants.AssetPublisherPortletKeys;
import com.liferay.blogs.constants.BlogsPortletKeys;
import com.liferay.blogs.model.BlogsEntry;
import com.liferay.blogs.service.BlogsEntryLocalService;
import com.liferay.depot.constants.DepotConstants;
import com.liferay.depot.model.DepotEntry;
import com.liferay.depot.model.DepotEntryGroupRel;
import com.liferay.depot.service.DepotEntryGroupRelLocalService;
import com.liferay.depot.service.DepotEntryLocalService;
import com.liferay.document.library.kernel.model.DLFolderConstants;
import com.liferay.document.library.kernel.service.DLAppLocalService;
import com.liferay.dynamic.data.mapping.form.field.type.constants.DDMFormFieldTypeConstants;
import com.liferay.dynamic.data.mapping.model.DDMForm;
import com.liferay.dynamic.data.mapping.model.DDMStructure;
import com.liferay.dynamic.data.mapping.test.util.DDMFormTestUtil;
import com.liferay.dynamic.data.mapping.test.util.DDMStructureTestUtil;
import com.liferay.fragment.model.FragmentEntryLink;
import com.liferay.fragment.service.FragmentEntryLinkLocalService;
import com.liferay.journal.constants.JournalContentPortletKeys;
import com.liferay.journal.constants.JournalFolderConstants;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.service.JournalArticleLocalService;
import com.liferay.journal.test.util.JournalTestUtil;
import com.liferay.layout.test.util.ContentLayoutTestUtil;
import com.liferay.layout.test.util.LayoutTestUtil;
import com.liferay.object.constants.ObjectFieldConstants;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.model.ObjectEntryFolder;
import com.liferay.object.model.ObjectField;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectEntryFolderLocalService;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.object.service.ObjectFieldLocalService;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.configuration.test.util.ConfigurationTemporarySwapper;
import com.liferay.portal.configuration.test.util.ConfigurationTestUtil;
import com.liferay.portal.kernel.backgroundtask.BackgroundTask;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskExecutor;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskResult;
import com.liferay.portal.kernel.backgroundtask.constants.BackgroundTaskConstants;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.json.JSONUtil;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.model.LayoutTypePortlet;
import com.liferay.portal.kernel.model.ModelHintsUtil;
import com.liferay.portal.kernel.portlet.PortletIdCodec;
import com.liferay.portal.kernel.portletdisplaytemplate.PortletDisplayTemplateManager;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.repository.model.Folder;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.service.LayoutLocalService;
import com.liferay.portal.kernel.service.PortletPreferencesLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.GroupTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.ContentTypes;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.HashMapDictionaryBuilder;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.PortletKeys;
import com.liferay.portal.kernel.util.ProxyUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.test.rule.PermissionCheckerMethodTestRule;
import com.liferay.reading.time.model.ReadingTimeEntry;
import com.liferay.reading.time.service.ReadingTimeEntryLocalService;
import com.liferay.segments.service.SegmentsExperienceLocalService;

import jakarta.portlet.PortletPreferences;

import java.io.Serializable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * @author Lino Alves
 */
@RunWith(Arquillian.class)
public class CMSMigrationBackgroundTaskExecutorTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new AggregateTestRule(
			new LiferayIntegrationTestRule(),
			PermissionCheckerMethodTestRule.INSTANCE);

	@Before
	public void setUp() throws Exception {
		_group = GroupTestUtil.addGroup();

		_depotEntry = _depotEntryLocalService.addDepotEntry(
			HashMapBuilder.put(
				LocaleUtil.getDefault(), RandomTestUtil.randomString()
			).build(),
			HashMapBuilder.put(
				LocaleUtil.getDefault(), RandomTestUtil.randomString()
			).build(),
			DepotConstants.TYPE_SPACE,
			ServiceContextTestUtil.getServiceContext(_group.getGroupId()));
	}

	@After
	public void tearDown() throws Exception {
		for (String externalReferenceCode :
				new String[] {
					"CMS_MIGRATION_BLOGS_ENTRY",
					"CMS_MIGRATION_DL_FILE_ENTRY_TYPE_0"
				}) {

			ObjectDefinition objectDefinition =
				_objectDefinitionLocalService.
					fetchObjectDefinitionByExternalReferenceCode(
						externalReferenceCode, _group.getCompanyId());

			if (objectDefinition != null) {
				_objectDefinitionLocalService.deleteObjectDefinition(
					objectDefinition);
			}
		}

		if (_journalArticle != null) {
			ObjectDefinition objectDefinition =
				_objectDefinitionLocalService.
					fetchObjectDefinitionByExternalReferenceCode(
						"CMS_MIGRATION_DDM_STRUCTURE_" +
							_journalArticle.getDDMStructureId(),
						_group.getCompanyId());

			if (objectDefinition != null) {
				_objectDefinitionLocalService.deleteObjectDefinition(
					objectDefinition);
			}
		}

		for (DepotEntryGroupRel depotEntryGroupRel :
				_depotEntryGroupRelLocalService.getDepotEntryGroupRels(
					_depotEntry)) {

			_depotEntryGroupRelLocalService.deleteDepotEntryGroupRel(
				depotEntryGroupRel);
		}

		_depotEntryLocalService.deleteDepotEntry(_depotEntry);
	}

	@Test
	public void testExecute() throws Exception {
		ServiceContext serviceContext =
			ServiceContextTestUtil.getServiceContext(_group.getGroupId());

		BlogsEntry blogsEntry = _blogsEntryLocalService.addEntry(
			TestPropsValues.getUserId(), RandomTestUtil.randomString(),
			"<p>" + RandomTestUtil.randomString() + "</p>", serviceContext);

		FileEntry fileEntry = _dlAppLocalService.addFileEntry(
			null, TestPropsValues.getUserId(), _group.getGroupId(),
			DLFolderConstants.DEFAULT_PARENT_FOLDER_ID,
			RandomTestUtil.randomString() + ".txt", ContentTypes.TEXT_PLAIN,
			RandomTestUtil.randomBytes(), null, null, null, serviceContext);

		_journalArticle = JournalTestUtil.addArticle(
			_group.getGroupId(),
			JournalFolderConstants.DEFAULT_PARENT_FOLDER_ID);

		_assertSuccessful(_execute(_group.getGroupId(), false));

		ObjectEntry objectEntry = _getObjectEntry(
			"CMS_MIGRATION_BLOGS_ENTRY",
			"CMS_MIGRATION_BLOGS_ENTRY_" + blogsEntry.getEntryId());

		Assert.assertEquals(
			blogsEntry.getTitle(), objectEntry.getTitleValue("en_US"));

		objectEntry = _getObjectEntry(
			"CMS_MIGRATION_DL_FILE_ENTRY_TYPE_0",
			"CMS_MIGRATION_FILE_ENTRY_" + fileEntry.getFileEntryId());

		Map<String, Serializable> values = objectEntry.getValues();

		Assert.assertNotEquals(0L, values.get("file"));

		objectEntry = _getObjectEntry(
			"CMS_MIGRATION_DDM_STRUCTURE_" +
				_journalArticle.getDDMStructureId(),
			"CMS_MIGRATION_JOURNAL_ARTICLE_" +
				_journalArticle.getResourcePrimKey());

		Assert.assertEquals(
			_journalArticle.getTitle(LocaleUtil.getSiteDefault()),
			objectEntry.getTitleValue(
				LocaleUtil.toLanguageId(LocaleUtil.getSiteDefault())));

		_assertSuccessful(_execute(_group.getGroupId(), false));

		Assert.assertEquals(
			objectEntry.getObjectEntryId(),
			_getObjectEntry(
				"CMS_MIGRATION_DDM_STRUCTURE_" +
					_journalArticle.getDDMStructureId(),
				"CMS_MIGRATION_JOURNAL_ARTICLE_" +
					_journalArticle.getResourcePrimKey()
			).getObjectEntryId());
	}

	@Test
	public void testExecuteWithBlogsEntryReadingTime() throws Exception {
		BlogsEntry blogsEntry = _blogsEntryLocalService.addEntry(
			TestPropsValues.getUserId(), RandomTestUtil.randomString(),
			_getContent(530),
			ServiceContextTestUtil.getServiceContext(_group.getGroupId()));

		_assertSuccessful(_execute(_group.getGroupId(), false));

		ReadingTimeEntry readingTimeEntry =
			_readingTimeEntryLocalService.fetchReadingTimeEntry(
				_getObjectEntry(
					"CMS_MIGRATION_BLOGS_ENTRY",
					"CMS_MIGRATION_BLOGS_ENTRY_" + blogsEntry.getEntryId()));

		Assert.assertEquals(120000, readingTimeEntry.getReadingTime());
	}

	@Test
	public void testExecuteWithBlogsEntryWithoutReadingTimeEntry()
		throws Exception {

		BlogsEntry blogsEntry = _blogsEntryLocalService.addEntry(
			TestPropsValues.getUserId(), RandomTestUtil.randomString(),
			_getContent(530),
			ServiceContextTestUtil.getServiceContext(_group.getGroupId()));

		_readingTimeEntryLocalService.deleteReadingTimeEntry(blogsEntry);

		_assertSuccessful(_execute(_group.getGroupId(), false));

		Assert.assertNull(
			_readingTimeEntryLocalService.fetchReadingTimeEntry(blogsEntry));

		ReadingTimeEntry readingTimeEntry =
			_readingTimeEntryLocalService.fetchReadingTimeEntry(
				_getObjectEntry(
					"CMS_MIGRATION_BLOGS_ENTRY",
					"CMS_MIGRATION_BLOGS_ENTRY_" + blogsEntry.getEntryId()));

		Assert.assertEquals(120000, readingTimeEntry.getReadingTime());
	}

	@Test
	public void testExecuteWithGlobalSourceGroup() throws Exception {
		Group companyGroup = _groupLocalService.getCompanyGroup(
			_group.getCompanyId());

		_journalArticle = JournalTestUtil.addArticle(
			companyGroup.getGroupId(),
			JournalFolderConstants.DEFAULT_PARENT_FOLDER_ID);

		try {
			JournalArticle journalArticle = JournalTestUtil.addArticle(
				_group.getGroupId(),
				JournalFolderConstants.DEFAULT_PARENT_FOLDER_ID);

			Layout layout = LayoutTestUtil.addTypePortletLayout(_group);

			String globalPortletId = _addJournalContentPortlet(
				layout, _journalArticle);
			String portletId = _addJournalContentPortlet(
				layout, journalArticle);

			_assertSuccessful(_execute(companyGroup.getGroupId(), true));

			ObjectEntry objectEntry = _getObjectEntry(
				"CMS_MIGRATION_DDM_STRUCTURE_" +
					_journalArticle.getDDMStructureId(),
				"CMS_MIGRATION_JOURNAL_ARTICLE_" +
					_journalArticle.getResourcePrimKey());

			Assert.assertEquals(
				_journalArticle.getTitle(LocaleUtil.getSiteDefault()),
				objectEntry.getTitleValue(
					LocaleUtil.toLanguageId(LocaleUtil.getSiteDefault())));

			layout = _layoutLocalService.getLayout(layout.getPlid());

			LayoutTypePortlet layoutTypePortlet =
				(LayoutTypePortlet)layout.getLayoutType();

			List<String> portletIds = layoutTypePortlet.getPortletIds();

			String newPortletId = PortletIdCodec.encode(
				_CMS_CONTENT_DISPLAY_PORTLET_NAME,
				PortletIdCodec.decodeInstanceId(globalPortletId));

			Assert.assertTrue(portletIds.contains(newPortletId));

			Assert.assertFalse(portletIds.contains(globalPortletId));
			Assert.assertTrue(portletIds.contains(portletId));

			PortletPreferences portletPreferences =
				_portletPreferencesLocalService.getPreferences(
					layout.getCompanyId(), PortletKeys.PREFS_OWNER_ID_DEFAULT,
					PortletKeys.PREFS_OWNER_TYPE_LAYOUT, layout.getPlid(),
					newPortletId);

			Assert.assertEquals(
				String.valueOf(objectEntry.getObjectEntryId()),
				portletPreferences.getValue("objectEntryId", StringPool.BLANK));

			Assert.assertNotNull(
				_depotEntryGroupRelLocalService.
					fetchDepotEntryGroupRelByDepotEntryIdToGroupId(
						_depotEntry.getDepotEntryId(), _group.getGroupId()));
		}
		finally {
			_journalArticleLocalService.deleteArticle(_journalArticle);
		}
	}

	@Test
	public void testExecuteWithLongFolderNames() throws Exception {
		int maxLength = ModelHintsUtil.getMaxLength(
			ObjectEntryFolder.class.getName(), "name");

		String name = RandomTestUtil.randomString(maxLength);

		Folder folder1 = _dlAppLocalService.addFolder(
			null, TestPropsValues.getUserId(), _group.getGroupId(),
			DLFolderConstants.DEFAULT_PARENT_FOLDER_ID, name + "1",
			StringPool.BLANK,
			ServiceContextTestUtil.getServiceContext(_group.getGroupId()));
		Folder folder2 = _dlAppLocalService.addFolder(
			null, TestPropsValues.getUserId(), _group.getGroupId(),
			DLFolderConstants.DEFAULT_PARENT_FOLDER_ID, name + "2",
			StringPool.BLANK,
			ServiceContextTestUtil.getServiceContext(_group.getGroupId()));

		_assertSuccessful(_execute(_group.getGroupId(), false));

		for (Folder folder : new Folder[] {folder1, folder2}) {
			ObjectEntryFolder objectEntryFolder =
				_objectEntryFolderLocalService.
					getObjectEntryFolderByExternalReferenceCode(
						"CMS_MIGRATION_DL_FOLDER_" + folder.getFolderId(),
						_depotEntry.getGroupId(), _group.getCompanyId());

			String suffix = StringPool.DASH + folder.getFolderId();

			Assert.assertEquals(
				name.substring(0, maxLength - suffix.length()) + suffix,
				objectEntryFolder.getName());
		}
	}

	@Test
	public void testExecuteWithNumericDDMFormField() throws Exception {
		DDMForm ddmForm = DDMFormTestUtil.createDDMForm("name");

		ddmForm.addDDMFormField(
			DDMFormTestUtil.createDDMFormField(
				"numeric", "numeric", DDMFormFieldTypeConstants.NUMERIC,
				"double", false, false, false));

		DDMStructure ddmStructure = DDMStructureTestUtil.addStructure(
			_group.getGroupId(), JournalArticle.class.getName(), ddmForm);

		_journalArticle = JournalTestUtil.addArticleWithXMLContent(
			_group.getGroupId(),
			DDMStructureTestUtil.getSampleStructuredContent(),
			ddmStructure.getStructureKey(), null);

		_assertSuccessful(_execute(_group.getGroupId(), false));

		ObjectEntry objectEntry = _getObjectEntry(
			"CMS_MIGRATION_DDM_STRUCTURE_" + ddmStructure.getStructureId(),
			"CMS_MIGRATION_JOURNAL_ARTICLE_" +
				_journalArticle.getResourcePrimKey());

		ObjectField objectField = _objectFieldLocalService.getObjectField(
			objectEntry.getObjectDefinitionId(), "numeric");

		Assert.assertEquals(
			ObjectFieldConstants.DB_TYPE_DOUBLE, objectField.getDBType());
		Assert.assertTrue(Validator.isNull(objectField.getIndexedLanguageId()));
	}

	@Test
	public void testExecuteWithRewritePages() throws Exception {
		_journalArticle = JournalTestUtil.addArticle(
			_group.getGroupId(),
			JournalFolderConstants.DEFAULT_PARENT_FOLDER_ID);

		Layout contentLayout = LayoutTestUtil.addTypeContentLayout(_group);

		FragmentEntryLink fragmentEntryLink =
			ContentLayoutTestUtil.addFragmentEntryLinkToLayout(
				JSONUtil.put(
					"com.liferay.fragment.entry.processor.editable." +
						"EditableFragmentEntryProcessor",
					JSONUtil.put(
						"element-text",
						JSONUtil.put(
							"classNameId",
							String.valueOf(
								_portal.getClassNameId(JournalArticle.class))
						).put(
							"classPK",
							String.valueOf(_journalArticle.getResourcePrimKey())
						).put(
							"defaultValue", RandomTestUtil.randomString()
						).put(
							"fieldId", "JournalArticle_title"
						))
				).toString(),
				contentLayout,
				_segmentsExperienceLocalService.
					fetchDefaultSegmentsExperienceId(contentLayout.getPlid()));

		Layout portletLayout = LayoutTestUtil.addTypePortletLayout(_group);

		String portletId = _addJournalContentPortlet(
			portletLayout, _journalArticle);

		_assertSuccessful(_execute(_group.getGroupId(), true));

		ObjectEntry objectEntry = _getObjectEntry(
			"CMS_MIGRATION_DDM_STRUCTURE_" +
				_journalArticle.getDDMStructureId(),
			"CMS_MIGRATION_JOURNAL_ARTICLE_" +
				_journalArticle.getResourcePrimKey());

		ObjectDefinition objectDefinition =
			_objectDefinitionLocalService.getObjectDefinition(
				objectEntry.getObjectDefinitionId());

		fragmentEntryLink = _fragmentEntryLinkLocalService.getFragmentEntryLink(
			fragmentEntryLink.getFragmentEntryLinkId());

		JSONObject editableValuesJSONObject = JSONFactoryUtil.createJSONObject(
			fragmentEntryLink.getEditableValues());

		JSONObject elementTextJSONObject =
			editableValuesJSONObject.getJSONObject(
				"com.liferay.fragment.entry.processor.editable." +
					"EditableFragmentEntryProcessor"
			).getJSONObject(
				"element-text"
			);

		Assert.assertEquals(
			String.valueOf(
				_portal.getClassNameId(objectDefinition.getClassName())),
			elementTextJSONObject.getString("classNameId"));
		Assert.assertEquals(
			String.valueOf(objectEntry.getObjectEntryId()),
			elementTextJSONObject.getString("classPK"));
		Assert.assertEquals(
			"ObjectField_title", elementTextJSONObject.getString("fieldId"));

		portletLayout = _layoutLocalService.getLayout(portletLayout.getPlid());

		LayoutTypePortlet layoutTypePortlet =
			(LayoutTypePortlet)portletLayout.getLayoutType();

		String newPortletId = PortletIdCodec.encode(
			_CMS_CONTENT_DISPLAY_PORTLET_NAME,
			PortletIdCodec.decodeInstanceId(portletId));

		Assert.assertTrue(
			layoutTypePortlet.getPortletIds(
			).contains(
				newPortletId
			));

		Assert.assertFalse(
			layoutTypePortlet.getPortletIds(
			).contains(
				portletId
			));

		PortletPreferences portletPreferences =
			_portletPreferencesLocalService.getPreferences(
				portletLayout.getCompanyId(),
				PortletKeys.PREFS_OWNER_ID_DEFAULT,
				PortletKeys.PREFS_OWNER_TYPE_LAYOUT, portletLayout.getPlid(),
				newPortletId);

		Assert.assertEquals(
			String.valueOf(objectEntry.getObjectEntryId()),
			portletPreferences.getValue("objectEntryId", StringPool.BLANK));
	}

	@Test
	public void testExecuteWithRewritePagesAndBlogsWidgets() throws Exception {
		BlogsEntry blogsEntry = _blogsEntryLocalService.addEntry(
			TestPropsValues.getUserId(), RandomTestUtil.randomString(),
			_getContent(530),
			ServiceContextTestUtil.getServiceContext(_group.getGroupId()));

		Layout readingTimeDisabledLayout = LayoutTestUtil.addTypePortletLayout(
			_group);

		LayoutTestUtil.addPortletToLayout(
			readingTimeDisabledLayout, BlogsPortletKeys.BLOGS,
			HashMapBuilder.put(
				"enableReadingTime", new String[] {"false"}
			).build());

		Layout readingTimeUnsetLayout = LayoutTestUtil.addTypePortletLayout(
			_group);

		LayoutTestUtil.addPortletToLayout(
			readingTimeUnsetLayout, BlogsPortletKeys.BLOGS,
			HashMapBuilder.put(
				"pageDelta",
				new String[] {String.valueOf(RandomTestUtil.randomInt())}
			).build());

		try (ConfigurationTemporarySwapper configurationTemporarySwapper =
				new ConfigurationTemporarySwapper(
					_PID_BLOGS_PORTLET_INSTANCE_CONFIGURATION,
					HashMapDictionaryBuilder.<String, Object>put(
						"enableReadingTime", true
					).build())) {

			_assertSuccessful(_execute(_group.getGroupId(), true));
		}

		Assert.assertEquals(
			"false",
			_getAssetPublisherPreferenceValue(
				readingTimeDisabledLayout, "enableReadingTime"));
		Assert.assertEquals(
			"true",
			_getAssetPublisherPreferenceValue(
				readingTimeUnsetLayout, "enableReadingTime"));

		String content = _renderAssetPublisherWidgetTemplate(
			blogsEntry, readingTimeDisabledLayout);

		Assert.assertFalse(content, content.contains("2 Minute Read"));

		content = _renderAssetPublisherWidgetTemplate(
			blogsEntry, readingTimeUnsetLayout);

		Assert.assertTrue(content, content.contains("2 Minute Read"));
	}

	@Test
	public void testExecuteWithRewritePagesAndBlogsWidgetsWhenSiteReadingTimeIsEnabled()
		throws Exception {

		_blogsEntryLocalService.addEntry(
			TestPropsValues.getUserId(), RandomTestUtil.randomString(),
			_getContent(530),
			ServiceContextTestUtil.getServiceContext(_group.getGroupId()));

		Layout layout = LayoutTestUtil.addTypePortletLayout(_group);

		LayoutTestUtil.addPortletToLayout(
			layout, BlogsPortletKeys.BLOGS,
			HashMapBuilder.put(
				"pageDelta",
				new String[] {String.valueOf(RandomTestUtil.randomInt())}
			).build());

		String pid = ConfigurationTestUtil.createFactoryConfiguration(
			_PID_BLOGS_PORTLET_INSTANCE_CONFIGURATION + ".scoped",
			HashMapDictionaryBuilder.<String, Object>put(
				"companyId", _group.getCompanyId()
			).put(
				"enableReadingTime", true
			).put(
				"groupId", _group.getGroupId()
			).build());

		try (ConfigurationTemporarySwapper configurationTemporarySwapper =
				new ConfigurationTemporarySwapper(
					_PID_BLOGS_PORTLET_INSTANCE_CONFIGURATION,
					HashMapDictionaryBuilder.<String, Object>put(
						"enableReadingTime", false
					).build())) {

			_assertSuccessful(_execute(_group.getGroupId(), true));
		}
		finally {
			ConfigurationTestUtil.deleteFactoryConfiguration(
				pid, _PID_BLOGS_PORTLET_INSTANCE_CONFIGURATION + ".scoped");
		}

		Assert.assertEquals(
			"true",
			_getAssetPublisherPreferenceValue(layout, "enableReadingTime"));
	}

	private String _addJournalContentPortlet(
			Layout layout, JournalArticle journalArticle)
		throws Exception {

		return LayoutTestUtil.addPortletToLayout(
			layout, JournalContentPortletKeys.JOURNAL_CONTENT,
			HashMapBuilder.put(
				"articleId", new String[] {journalArticle.getArticleId()}
			).put(
				"groupId",
				new String[] {String.valueOf(journalArticle.getGroupId())}
			).build());
	}

	private void _assertSuccessful(BackgroundTaskResult backgroundTaskResult) {
		Assert.assertEquals(
			backgroundTaskResult.getStatusMessage(),
			BackgroundTaskConstants.STATUS_SUCCESSFUL,
			backgroundTaskResult.getStatus());
	}

	private BackgroundTaskResult _execute(
			long sourceGroupId, boolean rewritePages)
		throws Exception {

		Map<String, Serializable> taskContextMap =
			HashMapBuilder.<String, Serializable>put(
				"depotEntryId", _depotEntry.getDepotEntryId()
			).put(
				"migrateBlogsEntries", true
			).put(
				"migrateFileEntries", true
			).put(
				"migrateJournalArticles", true
			).put(
				"rewritePages", rewritePages
			).put(
				"sourceGroupId", sourceGroupId
			).build();

		long userId = TestPropsValues.getUserId();

		BackgroundTask backgroundTask =
			(BackgroundTask)ProxyUtil.newProxyInstance(
				BackgroundTask.class.getClassLoader(),
				new Class<?>[] {BackgroundTask.class},
				(proxy, method, arguments) -> {
					String methodName = method.getName();

					if (methodName.equals("getTaskContextMap")) {
						return taskContextMap;
					}
					else if (methodName.equals("getUserId")) {
						return userId;
					}

					return null;
				});

		return _backgroundTaskExecutor.execute(backgroundTask);
	}

	private PortletPreferences _getAssetPublisherPortletPreferences(
			Layout layout)
		throws Exception {

		layout = _layoutLocalService.getLayout(layout.getPlid());

		LayoutTypePortlet layoutTypePortlet =
			(LayoutTypePortlet)layout.getLayoutType();

		for (String portletId : layoutTypePortlet.getPortletIds()) {
			if (!Objects.equals(
					PortletIdCodec.decodePortletName(portletId),
					AssetPublisherPortletKeys.ASSET_PUBLISHER)) {

				continue;
			}

			return _portletPreferencesLocalService.getPreferences(
				layout.getCompanyId(), PortletKeys.PREFS_OWNER_ID_DEFAULT,
				PortletKeys.PREFS_OWNER_TYPE_LAYOUT, layout.getPlid(),
				portletId);
		}

		throw new AssertionError(
			"Layout " + layout.getPlid() + " has no Asset Publisher");
	}

	private String _getAssetPublisherPreferenceValue(Layout layout, String name)
		throws Exception {

		PortletPreferences portletPreferences =
			_getAssetPublisherPortletPreferences(layout);

		return portletPreferences.getValue(name, null);
	}

	private String _getContent(int wordCount) {
		StringBundler sb = new StringBundler((wordCount * 2) + 2);

		sb.append("<p>");

		for (int i = 0; i < wordCount; i++) {
			sb.append(RandomTestUtil.randomString());
			sb.append(StringPool.SPACE);
		}

		sb.append("</p>");

		return sb.toString();
	}

	private ObjectEntry _getObjectEntry(
			String objectDefinitionExternalReferenceCode,
			String objectEntryExternalReferenceCode)
		throws Exception {

		ObjectDefinition objectDefinition =
			_objectDefinitionLocalService.
				getObjectDefinitionByExternalReferenceCode(
					objectDefinitionExternalReferenceCode,
					_group.getCompanyId());

		return _objectEntryLocalService.getObjectEntry(
			objectEntryExternalReferenceCode, _depotEntry.getGroupId(),
			objectDefinition.getObjectDefinitionId());
	}

	private String _renderAssetPublisherWidgetTemplate(
			BlogsEntry blogsEntry, Layout layout)
		throws Exception {

		MockHttpServletRequest mockHttpServletRequest =
			new MockHttpServletRequest();

		ThemeDisplay themeDisplay = ContentLayoutTestUtil.getThemeDisplay(
			_companyLocalService.getCompany(_group.getCompanyId()), _group,
			layout);

		themeDisplay.setRequest(mockHttpServletRequest);

		mockHttpServletRequest.setAttribute(
			WebKeys.THEME_DISPLAY, themeDisplay);

		ObjectEntry objectEntry = _getObjectEntry(
			"CMS_MIGRATION_BLOGS_ENTRY",
			"CMS_MIGRATION_BLOGS_ENTRY_" + blogsEntry.getEntryId());

		Map<String, Object> contextObjects = new HashMap<>();

		PortletPreferences portletPreferences =
			_getAssetPublisherPortletPreferences(layout);

		Map<String, String[]> map = portletPreferences.getMap();

		for (Map.Entry<String, String[]> entry : map.entrySet()) {
			String[] values = entry.getValue();

			if (ArrayUtil.isEmpty(values)) {
				continue;
			}

			contextObjects.put(entry.getKey(), values[0]);
		}

		return _portletDisplayTemplateManager.renderDDMTemplate(
			_portal.getClassNameId(AssetEntry.class), contextObjects,
			"CMS-MIGRATION-BLOGS",
			Collections.singletonList(
				_assetEntryLocalService.getEntry(
					objectEntry.getModelClassName(),
					objectEntry.getObjectEntryId())),
			_group.getGroupId(), mockHttpServletRequest,
			new MockHttpServletResponse(), false);
	}

	private static final String _CMS_CONTENT_DISPLAY_PORTLET_NAME =
		"com_liferay_cms_migration_web_internal_portlet_" +
			"CMSContentDisplayPortlet";

	private static final String _PID_BLOGS_PORTLET_INSTANCE_CONFIGURATION =
		"com.liferay.blogs.web.internal.configuration." +
			"BlogsPortletInstanceConfiguration";

	@Inject
	private AssetEntryLocalService _assetEntryLocalService;

	@Inject(
		filter = "background.task.executor.class.name=com.liferay.cms.migration.web.internal.background.task.CMSMigrationBackgroundTaskExecutor"
	)
	private BackgroundTaskExecutor _backgroundTaskExecutor;

	@Inject
	private BlogsEntryLocalService _blogsEntryLocalService;

	@Inject
	private CompanyLocalService _companyLocalService;

	private DepotEntry _depotEntry;

	@Inject
	private DepotEntryGroupRelLocalService _depotEntryGroupRelLocalService;

	@Inject
	private DepotEntryLocalService _depotEntryLocalService;

	@Inject
	private DLAppLocalService _dlAppLocalService;

	@Inject
	private FragmentEntryLinkLocalService _fragmentEntryLinkLocalService;

	@DeleteAfterTestRun
	private Group _group;

	@Inject
	private GroupLocalService _groupLocalService;

	private JournalArticle _journalArticle;

	@Inject
	private JournalArticleLocalService _journalArticleLocalService;

	@Inject
	private LayoutLocalService _layoutLocalService;

	@Inject
	private ObjectDefinitionLocalService _objectDefinitionLocalService;

	@Inject
	private ObjectEntryFolderLocalService _objectEntryFolderLocalService;

	@Inject
	private ObjectEntryLocalService _objectEntryLocalService;

	@Inject
	private ObjectFieldLocalService _objectFieldLocalService;

	@Inject
	private Portal _portal;

	@Inject
	private PortletDisplayTemplateManager _portletDisplayTemplateManager;

	@Inject
	private PortletPreferencesLocalService _portletPreferencesLocalService;

	@Inject
	private ReadingTimeEntryLocalService _readingTimeEntryLocalService;

	@Inject
	private SegmentsExperienceLocalService _segmentsExperienceLocalService;

}