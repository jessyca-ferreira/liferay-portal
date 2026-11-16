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
import com.liferay.depot.constants.DepotRolesConstants;
import com.liferay.depot.model.DepotEntry;
import com.liferay.depot.model.DepotEntryGroupRel;
import com.liferay.depot.service.DepotEntryGroupRelLocalService;
import com.liferay.depot.service.DepotEntryLocalService;
import com.liferay.document.library.kernel.model.DLFileEntry;
import com.liferay.document.library.kernel.model.DLFileEntryType;
import com.liferay.document.library.kernel.model.DLFileEntryTypeConstants;
import com.liferay.document.library.kernel.model.DLFolderConstants;
import com.liferay.document.library.kernel.service.DLAppLocalService;
import com.liferay.document.library.kernel.service.DLFileEntryTypeLocalService;
import com.liferay.dynamic.data.mapping.constants.DDMTemplateConstants;
import com.liferay.dynamic.data.mapping.form.field.type.constants.DDMFormFieldTypeConstants;
import com.liferay.dynamic.data.mapping.model.DDMForm;
import com.liferay.dynamic.data.mapping.model.DDMFormField;
import com.liferay.dynamic.data.mapping.model.DDMFormFieldOptions;
import com.liferay.dynamic.data.mapping.model.DDMStructure;
import com.liferay.dynamic.data.mapping.model.DDMTemplate;
import com.liferay.dynamic.data.mapping.service.DDMStructureLocalService;
import com.liferay.dynamic.data.mapping.service.DDMTemplateLocalService;
import com.liferay.dynamic.data.mapping.test.util.DDMFormTestUtil;
import com.liferay.dynamic.data.mapping.test.util.DDMStructureTestUtil;
import com.liferay.dynamic.data.mapping.test.util.DDMTemplateTestUtil;
import com.liferay.dynamic.data.mapping.util.DDMUtil;
import com.liferay.fragment.model.FragmentEntryLink;
import com.liferay.fragment.service.FragmentEntryLinkLocalService;
import com.liferay.journal.constants.JournalArticleConstants;
import com.liferay.journal.constants.JournalContentPortletKeys;
import com.liferay.journal.constants.JournalFolderConstants;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.service.JournalArticleLocalService;
import com.liferay.journal.test.util.JournalTestUtil;
import com.liferay.layout.page.template.constants.LayoutPageTemplateEntryTypeConstants;
import com.liferay.layout.page.template.model.LayoutPageTemplateEntry;
import com.liferay.layout.page.template.service.LayoutPageTemplateEntryLocalService;
import com.liferay.layout.test.util.ContentLayoutTestUtil;
import com.liferay.layout.test.util.LayoutTestUtil;
import com.liferay.list.type.model.ListTypeEntry;
import com.liferay.object.constants.ObjectFieldConstants;
import com.liferay.object.constants.ObjectFolderConstants;
import com.liferay.object.constants.ObjectRelationshipConstants;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.model.ObjectEntryFolder;
import com.liferay.object.model.ObjectField;
import com.liferay.object.model.ObjectFolder;
import com.liferay.object.model.ObjectRelationship;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectEntryFolderLocalService;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.object.service.ObjectEntryService;
import com.liferay.object.service.ObjectFieldLocalService;
import com.liferay.object.service.ObjectFolderLocalService;
import com.liferay.object.service.ObjectRelationshipLocalService;
import com.liferay.object.tree.constants.TreeConstants;
import com.liferay.petra.function.UnsafeRunnable;
import com.liferay.petra.io.unsync.UnsyncStringWriter;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.configuration.test.util.ConfigurationTemporarySwapper;
import com.liferay.portal.configuration.test.util.ConfigurationTestUtil;
import com.liferay.portal.kernel.backgroundtask.BackgroundTask;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskExecutor;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskResult;
import com.liferay.portal.kernel.backgroundtask.constants.BackgroundTaskConstants;
import com.liferay.portal.kernel.dao.orm.DynamicQuery;
import com.liferay.portal.kernel.dao.orm.ProjectionFactoryUtil;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.dao.orm.RestrictionsFactoryUtil;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.json.JSONUtil;
import com.liferay.portal.kernel.language.LanguageUtil;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.model.LayoutTypePortlet;
import com.liferay.portal.kernel.model.ModelHintsUtil;
import com.liferay.portal.kernel.model.ResourceConstants;
import com.liferay.portal.kernel.model.Role;
import com.liferay.portal.kernel.model.role.RoleConstants;
import com.liferay.portal.kernel.portlet.PortletIdCodec;
import com.liferay.portal.kernel.portletdisplaytemplate.PortletDisplayTemplateManager;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.repository.model.Folder;
import com.liferay.portal.kernel.security.permission.ActionKeys;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactoryUtil;
import com.liferay.portal.kernel.security.permission.ResourceActionsUtil;
import com.liferay.portal.kernel.security.permission.resource.ModelResourcePermission;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.service.LayoutLocalService;
import com.liferay.portal.kernel.service.PortletPreferencesLocalService;
import com.liferay.portal.kernel.service.ResourcePermissionLocalService;
import com.liferay.portal.kernel.service.RoleLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.service.permission.PortletPermissionUtil;
import com.liferay.portal.kernel.template.StringTemplateResource;
import com.liferay.portal.kernel.template.Template;
import com.liferay.portal.kernel.template.TemplateConstants;
import com.liferay.portal.kernel.template.TemplateManagerUtil;
import com.liferay.portal.kernel.templateparser.TemplateNode;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.CompanyTestUtil;
import com.liferay.portal.kernel.test.util.GroupTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.RoleTestUtil;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.ContentTypes;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.HashMapDictionaryBuilder;
import com.liferay.portal.kernel.util.LocaleThreadLocal;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.PortletKeys;
import com.liferay.portal.kernel.util.ProxyUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.kernel.xml.Document;
import com.liferay.portal.kernel.xml.Element;
import com.liferay.portal.kernel.xml.SAXReaderUtil;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.test.rule.PermissionCheckerMethodTestRule;
import com.liferay.reading.time.model.ReadingTimeEntry;
import com.liferay.reading.time.service.ReadingTimeEntryLocalService;
import com.liferay.segments.service.SegmentsExperienceLocalService;
import com.liferay.template.model.TemplateEntry;
import com.liferay.template.service.TemplateEntryLocalService;

import jakarta.portlet.PortletPreferences;

import java.io.Serializable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

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

		_depotEntry = _addDepotEntry();

		_originalObjectDefinitionIds = _getObjectDefinitionIds();
	}

	@After
	public void tearDown() throws Exception {
		_deleteDepotEntry(_depotEntry);

		for (long objectDefinitionId : _getObjectDefinitionIds()) {
			if (_originalObjectDefinitionIds.contains(objectDefinitionId)) {
				continue;
			}

			ObjectDefinition objectDefinition =
				_objectDefinitionLocalService.fetchObjectDefinition(
					objectDefinitionId);

			if (objectDefinition != null) {
				_deleteObjectDefinition(objectDefinition);
			}
		}
	}

	@Test
	public void testExecute() throws Exception {
		BlogsEntry blogsEntry = _blogsEntryLocalService.addEntry(
			TestPropsValues.getUserId(), RandomTestUtil.randomString(),
			"<p>" + RandomTestUtil.randomString() + "</p>",
			ServiceContextTestUtil.getServiceContext(_group.getGroupId()));

		FileEntry fileEntry = _addFileEntry(StringPool.BLANK);

		JournalArticle journalArticle = JournalTestUtil.addArticle(
			_group.getGroupId(),
			JournalFolderConstants.DEFAULT_PARENT_FOLDER_ID);

		_assertSuccessful(_execute(_group.getGroupId(), false));

		ObjectEntry objectEntry = _getObjectEntry(
			"CMS_MIGRATION_BLOGS_ENTRY",
			"CMS_MIGRATION_BLOGS_ENTRY_" + blogsEntry.getEntryId());

		Assert.assertEquals(
			blogsEntry.getTitle(), objectEntry.getTitleValue("en_US"));

		objectEntry = _getObjectEntry(fileEntry);

		Map<String, Serializable> values = objectEntry.getValues();

		Assert.assertNotEquals(0L, values.get("file"));

		objectEntry = _getObjectEntry(journalArticle);

		Assert.assertEquals(
			journalArticle.getTitle(LocaleUtil.getSiteDefault()),
			objectEntry.getTitleValue(
				LocaleUtil.toLanguageId(LocaleUtil.getSiteDefault())));

		_assertSuccessful(_execute(_group.getGroupId(), false));

		Assert.assertEquals(
			objectEntry.getObjectEntryId(),
			_getObjectEntry(
				journalArticle
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
	public void testExecuteWithCheckboxDDMFormField() throws Exception {
		DDMForm ddmForm = DDMFormTestUtil.createDDMForm();

		ddmForm.addDDMFormField(
			DDMFormTestUtil.createDDMFormField(
				"checkbox", "checkbox", DDMFormFieldTypeConstants.CHECKBOX,
				"boolean", false, false, false));

		DDMStructure ddmStructure = DDMStructureTestUtil.addStructure(
			_group.getGroupId(), JournalArticle.class.getName(), ddmForm);

		DDMTemplate ddmTemplate = DDMTemplateTestUtil.addTemplate(
			_group.getGroupId(), _portal.getClassNameId(DDMStructure.class),
			ddmStructure.getStructureId(),
			_portal.getClassNameId(JournalArticle.class),
			TemplateConstants.LANG_TYPE_FTL,
			"${getterUtil.getBoolean(checkbox.getData())?c}", LocaleUtil.US);

		JournalArticle journalArticle =
			JournalTestUtil.addArticleWithXMLContent(
				_group.getGroupId(),
				StringBundler.concat(
					"<root available-locales=\"en_US\" ",
					"default-locale=\"en_US\">",
					"<dynamic-element field-reference=\"checkbox\" ",
					"index-type=\"keyword\" instance-id=\"",
					RandomTestUtil.randomString(8),
					"\" name=\"checkbox\" type=\"checkbox\"><dynamic-content ",
					"language-id=\"en_US\"><![CDATA[true]]></dynamic-content>",
					"</dynamic-element></root>"),
				ddmStructure.getStructureKey(), null);

		_assertSuccessful(_execute(_group.getGroupId(), false));

		String content = _render(ddmTemplate, _getObjectEntry(journalArticle));

		Assert.assertTrue(content, content.contains(">true</div>"));
	}

	@Test
	public void testExecuteWithDefaultResourcePermissions() throws Exception {
		JournalArticle journalArticle = JournalTestUtil.addArticle(
			_group.getGroupId(),
			JournalFolderConstants.DEFAULT_PARENT_FOLDER_ID);

		_assertSuccessful(_execute(_group.getGroupId(), false));

		ObjectEntry objectEntry = _getObjectEntry(journalArticle);

		_role = RoleTestUtil.addRole(RoleConstants.TYPE_REGULAR);

		_setResourcePermissions(
			objectEntry.getModelClassName(), objectEntry.getObjectEntryId(),
			_role, ActionKeys.VIEW);

		_assertSuccessful(_execute(_group.getGroupId(), false));

		Assert.assertTrue(_hasViewResourcePermission(objectEntry, _role));
	}

	@Test
	public void testExecuteWithFileEntryDescription() throws Exception {
		String description = RandomTestUtil.randomString();

		FileEntry fileEntry = _addFileEntry(description);

		_assertSuccessful(_execute(_group.getGroupId(), false));

		FileEntry attachmentFileEntry = _getAttachmentFileEntry(fileEntry);

		Assert.assertEquals(description, attachmentFileEntry.getDescription());
	}

	@Test
	public void testExecuteWithGlobalSourceGroup() throws Exception {
		Group companyGroup = _groupLocalService.getCompanyGroup(
			_group.getCompanyId());

		JournalArticle globalJournalArticle = JournalTestUtil.addArticle(
			companyGroup.getGroupId(),
			JournalFolderConstants.DEFAULT_PARENT_FOLDER_ID);

		try {
			JournalArticle journalArticle = JournalTestUtil.addArticle(
				_group.getGroupId(),
				JournalFolderConstants.DEFAULT_PARENT_FOLDER_ID);

			Layout layout = LayoutTestUtil.addTypePortletLayout(_group);

			String globalPortletId = _addJournalContentPortlet(
				layout, globalJournalArticle);
			String portletId = _addJournalContentPortlet(
				layout, journalArticle);

			_assertSuccessful(_execute(companyGroup.getGroupId(), true));

			ObjectEntry objectEntry = _getObjectEntry(globalJournalArticle);

			Assert.assertEquals(
				globalJournalArticle.getTitle(LocaleUtil.getSiteDefault()),
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
			_journalArticleLocalService.deleteArticle(globalJournalArticle);
		}
	}

	@Test
	public void testExecuteWithHTMLDescription() throws Exception {
		String description = RandomTestUtil.randomString();
		Locale locale = LocaleUtil.getSiteDefault();

		JournalArticle journalArticle = JournalTestUtil.addArticle(
			_group.getGroupId(),
			JournalFolderConstants.DEFAULT_PARENT_FOLDER_ID,
			JournalArticleConstants.CLASS_NAME_ID_DEFAULT,
			HashMapBuilder.put(
				locale, RandomTestUtil.randomString()
			).build(),
			HashMapBuilder.put(
				locale, "<p>" + description + "</p>"
			).build(),
			HashMapBuilder.put(
				locale, RandomTestUtil.randomString()
			).build(),
			locale, false, true,
			ServiceContextTestUtil.getServiceContext(_group.getGroupId()));

		_assertSuccessful(_execute(_group.getGroupId(), false));

		ObjectEntry objectEntry = _getObjectEntry(journalArticle);

		Map<String, Serializable> values = objectEntry.getValues();

		Assert.assertEquals(
			HashMapBuilder.put(
				LocaleUtil.toLanguageId(locale), description
			).build(),
			values.get("description_i18n"));
	}

	@Test
	public void testExecuteWithImageAlternativeText() throws Exception {
		FileEntry fileEntry = _addFileEntry(StringPool.BLANK);

		String alternativeText = RandomTestUtil.randomString();
		Locale locale = LocaleUtil.getSiteDefault();

		JournalArticle journalArticle = _addJournalArticleWithImage(
			alternativeText, fileEntry, locale);

		_assertSuccessful(_execute(_group.getGroupId(), false));

		ObjectEntry objectEntry = _getObjectEntry(journalArticle);

		Map<String, Serializable> values = objectEntry.getValues();

		FileEntry attachmentFileEntry = _dlAppLocalService.getFileEntry(
			GetterUtil.getLong(values.get("image")));

		Assert.assertEquals(
			alternativeText, attachmentFileEntry.getDescription());

		Assert.assertEquals(
			HashMapBuilder.put(
				LocaleUtil.toLanguageId(locale), alternativeText
			).build(),
			values.get("imageAlt_i18n"));
	}

	@Test
	public void testExecuteWithImageAlternativeTextWhenFileEntryHasDescription()
		throws Exception {

		String description = RandomTestUtil.randomString();

		FileEntry fileEntry = _addFileEntry(description);

		JournalArticle journalArticle = _addJournalArticleWithImage(
			RandomTestUtil.randomString(), fileEntry,
			LocaleUtil.getSiteDefault());

		_assertSuccessful(_execute(_group.getGroupId(), false));

		ObjectEntry objectEntry = _getObjectEntry(journalArticle);

		Map<String, Serializable> values = objectEntry.getValues();

		FileEntry attachmentFileEntry = _dlAppLocalService.getFileEntry(
			GetterUtil.getLong(values.get("image")));

		Assert.assertEquals(description, attachmentFileEntry.getDescription());
	}

	@Test
	public void testExecuteWithJournalArticlePriority() throws Exception {
		JournalArticle journalArticle = JournalTestUtil.addArticle(
			_group.getGroupId(),
			JournalFolderConstants.DEFAULT_PARENT_FOLDER_ID);

		AssetEntry assetEntry = _assetEntryLocalService.getEntry(
			JournalArticle.class.getName(),
			journalArticle.getResourcePrimKey());

		assetEntry.setPriority(2.5);

		_assetEntryLocalService.updateAssetEntry(assetEntry);

		_assertSuccessful(_execute(_group.getGroupId(), false));

		ObjectEntry objectEntry = _getObjectEntry(journalArticle);

		ObjectDefinition objectDefinition =
			_objectDefinitionLocalService.getObjectDefinition(
				objectEntry.getObjectDefinitionId());

		assetEntry = _assetEntryLocalService.getEntry(
			objectDefinition.getClassName(), objectEntry.getObjectEntryId());

		Assert.assertEquals(2.5, assetEntry.getPriority(), 0);
	}

	@Test
	public void testExecuteWithJournalArticlePriorityAndContentLinks()
		throws Exception {

		DDMForm ddmForm = DDMFormTestUtil.createDDMForm();

		ddmForm.addDDMFormField(
			DDMFormTestUtil.createDDMFormField(
				"content", "content", DDMFormFieldTypeConstants.RICH_TEXT,
				"html", true, false, false));

		DDMStructure ddmStructure = DDMStructureTestUtil.addStructure(
			_group.getGroupId(), JournalArticle.class.getName(), ddmForm);

		JournalArticle linkedJournalArticle = JournalTestUtil.addArticle(
			_group.getGroupId(),
			JournalFolderConstants.DEFAULT_PARENT_FOLDER_ID);

		String link = "/w/" + linkedJournalArticle.getUrlTitle();

		JournalArticle journalArticle =
			JournalTestUtil.addArticleWithXMLContent(
				_group.getGroupId(),
				DDMStructureTestUtil.getSampleStructuredContent(
					"content",
					Collections.singletonList(
						HashMapBuilder.put(
							LocaleUtil.US,
							StringBundler.concat(
								"<p><a href=\"", link, "\">",
								RandomTestUtil.randomString(), "</a></p>")
						).build()),
					LocaleUtil.toLanguageId(LocaleUtil.US)),
				ddmStructure.getStructureKey(), null);

		AssetEntry assetEntry = _assetEntryLocalService.getEntry(
			JournalArticle.class.getName(),
			journalArticle.getResourcePrimKey());

		assetEntry.setPriority(2.5);

		_assetEntryLocalService.updateAssetEntry(assetEntry);

		_assertSuccessful(_execute(_group.getGroupId(), false));

		ObjectEntry objectEntry = _getObjectEntry(
			"CMS_MIGRATION_DDM_STRUCTURE_" + journalArticle.getDDMStructureId(),
			"CMS_MIGRATION_JOURNAL_ARTICLE_" +
				journalArticle.getResourcePrimKey());

		Map<String, Serializable> values = objectEntry.getValues();

		for (Serializable value : values.values()) {
			String valueString = String.valueOf(value);

			Assert.assertFalse(valueString, valueString.contains(link));
		}

		ObjectDefinition objectDefinition =
			_objectDefinitionLocalService.getObjectDefinition(
				objectEntry.getObjectDefinitionId());

		assetEntry = _assetEntryLocalService.getEntry(
			objectDefinition.getClassName(), objectEntry.getObjectEntryId());

		Assert.assertEquals(2.5, assetEntry.getPriority(), 0);
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
	public void testExecuteWithLongSelectDDMFormFieldOptions()
		throws Exception {

		int maxLength = ModelHintsUtil.getMaxLength(
			ListTypeEntry.class.getName(), "key");

		String optionValue = RandomTestUtil.randomString(maxLength);

		String[] optionValues = {optionValue + "1", optionValue + "2"};

		DDMStructure ddmStructure = _addSelectDDMStructure(optionValues);

		Map<String, JournalArticle> journalArticlesByOptionValue =
			new HashMap<>();

		for (String curOptionValue : optionValues) {
			journalArticlesByOptionValue.put(
				curOptionValue,
				JournalTestUtil.addArticleWithXMLContent(
					_group.getGroupId(),
					DDMStructureTestUtil.getSampleStructuredContent(
						"choice", "[\"" + curOptionValue + "\"]"),
					ddmStructure.getStructureKey(), null));
		}

		_assertSuccessful(_execute(_group.getGroupId(), false));

		for (Map.Entry<String, JournalArticle> entry :
				journalArticlesByOptionValue.entrySet()) {

			String curOptionValue = entry.getKey();

			ObjectEntry objectEntry = _getObjectEntry(entry.getValue());

			Map<String, Serializable> values = objectEntry.getValues();

			String suffix = Integer.toHexString(curOptionValue.hashCode());

			Assert.assertEquals(
				curOptionValue.substring(0, maxLength - suffix.length()) +
					suffix,
				values.get("choice"));
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

		JournalArticle journalArticle =
			JournalTestUtil.addArticleWithXMLContent(
				_group.getGroupId(),
				DDMStructureTestUtil.getSampleStructuredContent(),
				ddmStructure.getStructureKey(), null);

		_assertSuccessful(_execute(_group.getGroupId(), false));

		ObjectEntry objectEntry = _getObjectEntry(journalArticle);

		ObjectField objectField = _objectFieldLocalService.getObjectField(
			objectEntry.getObjectDefinitionId(), "numeric");

		Assert.assertEquals(
			ObjectFieldConstants.DB_TYPE_DOUBLE, objectField.getDBType());
		Assert.assertTrue(Validator.isNull(objectField.getIndexedLanguageId()));
	}

	@Test
	public void testExecuteWithRemovedDDMFormFieldOption() throws Exception {
		String optionValue = RandomTestUtil.randomString();
		String removedOptionValue = RandomTestUtil.randomString();

		DDMStructure ddmStructure = _addSelectDDMStructure(
			optionValue, removedOptionValue);

		JournalArticle journalArticle =
			JournalTestUtil.addArticleWithXMLContent(
				_group.getGroupId(),
				DDMStructureTestUtil.getSampleStructuredContent(
					"choice", "[\"" + removedOptionValue + "\"]"),
				ddmStructure.getStructureKey(), null);

		DDMForm ddmForm = ddmStructure.getDDMForm();

		Map<String, DDMFormField> ddmFormFieldsMap =
			ddmForm.getDDMFormFieldsMap(false);

		DDMFormField ddmFormField = ddmFormFieldsMap.get("choice");

		DDMFormFieldOptions ddmFormFieldOptions = new DDMFormFieldOptions();

		ddmFormFieldOptions.addOptionLabel(
			optionValue, LocaleUtil.US, optionValue);

		ddmFormField.setDDMFormFieldOptions(ddmFormFieldOptions);

		_ddmStructureLocalService.updateStructure(
			TestPropsValues.getUserId(), ddmStructure.getStructureId(), ddmForm,
			DDMUtil.getDefaultDDMFormLayout(ddmForm),
			ServiceContextTestUtil.getServiceContext(_group.getGroupId()));

		_assertSuccessful(_execute(_group.getGroupId(), false));

		ObjectEntry objectEntry = _getObjectEntry(journalArticle);

		Map<String, Serializable> values = objectEntry.getValues();

		Assert.assertEquals(removedOptionValue, values.get("choice"));
	}

	@Test
	public void testExecuteWithRepeatableDDMFormFieldAsRepeatableGroupAndResourcePermissions()
		throws Exception {

		DDMForm ddmForm = DDMFormTestUtil.createDDMForm();

		ddmForm.addDDMFormField(
			DDMFormTestUtil.createDDMFormField(
				"text", "text", DDMFormFieldTypeConstants.TEXT, "string", false,
				true, false));

		DDMStructure ddmStructure = DDMStructureTestUtil.addStructure(
			_group.getGroupId(), JournalArticle.class.getName(), ddmForm);

		JournalArticle journalArticle =
			JournalTestUtil.addArticleWithXMLContent(
				_group.getGroupId(),
				_getTextsContent(
					RandomTestUtil.randomString(),
					RandomTestUtil.randomString()),
				ddmStructure.getStructureKey(), null);

		Role guestRole = _roleLocalService.getRole(
			_group.getCompanyId(), RoleConstants.GUEST);

		_deleteResourcePermissions(
			JournalArticle.class.getName(), journalArticle.getResourcePrimKey(),
			guestRole);

		_assertSuccessful(_execute(_group.getGroupId(), true, false));

		ObjectEntry objectEntry = _getObjectEntry(journalArticle);

		List<ObjectEntry> itemObjectEntries = _getItemObjectEntries(
			objectEntry);

		Assert.assertEquals(
			itemObjectEntries.toString(), 2, itemObjectEntries.size());

		ObjectEntry itemObjectEntry = itemObjectEntries.get(0);

		ModelResourcePermission<ObjectEntry> modelResourcePermission =
			_objectEntryService.getModelResourcePermission(
				itemObjectEntry.getObjectDefinitionId());

		PermissionChecker permissionChecker =
			PermissionCheckerFactoryUtil.create(
				_userLocalService.getGuestUser(_group.getCompanyId()));

		Assert.assertFalse(
			modelResourcePermission.contains(
				permissionChecker, itemObjectEntry, ActionKeys.VIEW));

		_setResourcePermissions(
			objectEntry.getModelClassName(), objectEntry.getObjectEntryId(),
			guestRole, ActionKeys.VIEW);

		Assert.assertTrue(
			modelResourcePermission.contains(
				permissionChecker, itemObjectEntry, ActionKeys.VIEW));
	}

	@Test
	public void testExecuteWithRepeatableDDMFormFieldAsRepeatableGroupAndThenAsJSON()
		throws Exception {

		DDMForm ddmForm = DDMFormTestUtil.createDDMForm();

		ddmForm.addDDMFormField(
			DDMFormTestUtil.createDDMFormField(
				"text", "text", DDMFormFieldTypeConstants.TEXT, "string", false,
				true, false));

		DDMStructure ddmStructure = DDMStructureTestUtil.addStructure(
			_group.getGroupId(), JournalArticle.class.getName(), ddmForm);

		DDMTemplate ddmTemplate = DDMTemplateTestUtil.addTemplate(
			_group.getGroupId(), _portal.getClassNameId(DDMStructure.class),
			ddmStructure.getStructureId(),
			_portal.getClassNameId(JournalArticle.class),
			TemplateConstants.LANG_TYPE_FTL,
			"<#list text.getSiblings() as item>[${item.getData()}]</#list>",
			LocaleUtil.US);

		String[] texts = {
			RandomTestUtil.randomString(), RandomTestUtil.randomString()
		};

		JournalArticle journalArticle =
			JournalTestUtil.addArticleWithXMLContent(
				_group.getGroupId(), _getTextsContent(texts),
				ddmStructure.getStructureKey(), null);

		_assertSuccessful(_execute(_group.getGroupId(), true, false));

		ObjectEntry objectEntry = _getObjectEntry(journalArticle);

		List<ObjectEntry> itemObjectEntries = _getItemObjectEntries(
			objectEntry);

		Assert.assertEquals(
			itemObjectEntries.toString(), texts.length,
			itemObjectEntries.size());

		String[] newTexts = {
			RandomTestUtil.randomString(), RandomTestUtil.randomString()
		};

		journalArticle = JournalTestUtil.updateArticle(
			journalArticle, RandomTestUtil.randomString(),
			_getTextsContent(newTexts), false, true,
			ServiceContextTestUtil.getServiceContext(_group.getGroupId()));

		_assertSuccessful(_execute(_group.getGroupId(), false));

		String content = _render(ddmTemplate, objectEntry);

		Assert.assertTrue(
			content,
			content.contains(
				StringBundler.concat(
					"[", newTexts[0], "][", newTexts[1], "]")));
		Assert.assertFalse(content, content.contains(texts[0]));

		JournalTestUtil.updateArticle(
			journalArticle, RandomTestUtil.randomString(), _getTextsContent(),
			false, true,
			ServiceContextTestUtil.getServiceContext(_group.getGroupId()));

		_assertSuccessful(_execute(_group.getGroupId(), false));

		objectEntry = _getObjectEntry(journalArticle);

		Map<String, Serializable> values = objectEntry.getValues();

		Assert.assertEquals("[]", values.get("text"));

		content = _render(ddmTemplate, objectEntry);

		Assert.assertTrue(content, content.contains("[]"));
		Assert.assertFalse(content, content.contains(texts[0]));
	}

	@Test
	public void testExecuteWithRepeatableDDMFormFieldAsRepeatableGroupWithRemovedItems()
		throws Exception {

		DDMForm ddmForm = DDMFormTestUtil.createDDMForm();

		ddmForm.addDDMFormField(
			DDMFormTestUtil.createDDMFormField(
				"text", "text", DDMFormFieldTypeConstants.TEXT, "string", false,
				true, false));

		DDMStructure ddmStructure = DDMStructureTestUtil.addStructure(
			_group.getGroupId(), JournalArticle.class.getName(), ddmForm);

		String[] texts = {
			RandomTestUtil.randomString(), RandomTestUtil.randomString(),
			RandomTestUtil.randomString()
		};

		JournalArticle journalArticle =
			JournalTestUtil.addArticleWithXMLContent(
				_group.getGroupId(), _getTextsContent(texts),
				ddmStructure.getStructureKey(), null);

		_assertSuccessful(_execute(_group.getGroupId(), true, false));

		ObjectEntry objectEntry = _getObjectEntry(journalArticle);

		List<ObjectEntry> itemObjectEntries = _getItemObjectEntries(
			objectEntry);

		Assert.assertEquals(
			itemObjectEntries.toString(), texts.length,
			itemObjectEntries.size());

		journalArticle = JournalTestUtil.updateArticle(
			journalArticle, RandomTestUtil.randomString(),
			_getTextsContent(texts[0]), false, true,
			ServiceContextTestUtil.getServiceContext(_group.getGroupId()));

		_assertSuccessful(_execute(_group.getGroupId(), true, false));

		itemObjectEntries = _getItemObjectEntries(objectEntry);

		Assert.assertEquals(
			itemObjectEntries.toString(), 1, itemObjectEntries.size());

		ObjectEntry itemObjectEntry = itemObjectEntries.get(0);

		Map<String, Serializable> values = itemObjectEntry.getValues();

		Assert.assertEquals(texts[0], GetterUtil.getString(values.get("text")));

		JournalTestUtil.updateArticle(
			journalArticle, RandomTestUtil.randomString(),
			_getTextsContent(StringPool.BLANK), false, true,
			ServiceContextTestUtil.getServiceContext(_group.getGroupId()));

		_assertSuccessful(_execute(_group.getGroupId(), true, false));

		Assert.assertEquals(
			Collections.emptyList(), _getItemObjectEntries(objectEntry));
	}

	@Test
	public void testExecuteWithRepeatableDDMFormFieldNestedBeyondMaximumDepth()
		throws Exception {

		DDMFormField parentDDMFormField = null;

		DDMForm ddmForm = DDMFormTestUtil.createDDMForm();

		for (int i = 1; i <= (TreeConstants.MAX_HEIGHT + 1); i++) {
			DDMFormField ddmFormField = DDMFormTestUtil.createDDMFormField(
				"level" + i, "level" + i, DDMFormFieldTypeConstants.TEXT,
				"string", false, true, false);

			if (parentDDMFormField == null) {
				ddmForm.addDDMFormField(ddmFormField);
			}
			else {
				parentDDMFormField.addNestedDDMFormField(ddmFormField);
			}

			parentDDMFormField = ddmFormField;
		}

		String leafName = StringUtil.randomId();

		parentDDMFormField.addNestedDDMFormField(
			DDMFormTestUtil.createDDMFormField(
				leafName, leafName, DDMFormFieldTypeConstants.TEXT, "string",
				false, false, false));

		DDMStructure ddmStructure = DDMStructureTestUtil.addStructure(
			_group.getGroupId(), JournalArticle.class.getName(), ddmForm);

		String dynamicElement = _getDynamicElement(
			RandomTestUtil.randomString(), leafName,
			DDMFormFieldTypeConstants.TEXT);

		for (int i = TreeConstants.MAX_HEIGHT + 1; i > 0; i--) {
			dynamicElement = _getDynamicElement(
				RandomTestUtil.randomString(), "level" + i,
				DDMFormFieldTypeConstants.TEXT, dynamicElement);
		}

		JournalArticle journalArticle =
			JournalTestUtil.addArticleWithXMLContent(
				_group.getGroupId(),
				StringBundler.concat(
					"<root available-locales=\"en_US\" ",
					"default-locale=\"en_US\">", dynamicElement, "</root>"),
				ddmStructure.getStructureKey(), null);

		BackgroundTaskResult backgroundTaskResult = _execute(
			_group.getGroupId(), true, false);

		_assertSuccessful(backgroundTaskResult);

		String statusMessage = backgroundTaskResult.getStatusMessage();

		Assert.assertTrue(
			statusMessage,
			statusMessage.contains(
				StringBundler.concat(
					"Migrated the repeatable field level",
					TreeConstants.MAX_HEIGHT + 1, "FieldSet of the structure ",
					ddmStructure.getStructureKey(),
					" as JSON because repeatable groups cannot be nested more ",
					"than ", TreeConstants.MAX_HEIGHT, " levels deep")));

		ObjectEntry objectEntry = _getObjectEntry(journalArticle);

		for (int i = 1; i <= TreeConstants.MAX_HEIGHT; i++) {
			List<ObjectEntry> itemObjectEntries = _getItemObjectEntries(
				objectEntry);

			Assert.assertEquals(
				itemObjectEntries.toString(), 1, itemObjectEntries.size());

			objectEntry = itemObjectEntries.get(0);
		}

		ObjectField objectField = _objectFieldLocalService.fetchObjectField(
			objectEntry.getObjectDefinitionId(),
			"level" + (TreeConstants.MAX_HEIGHT + 1) + "FieldSet");

		Assert.assertEquals(
			ObjectFieldConstants.BUSINESS_TYPE_RICH_TEXT,
			objectField.getBusinessType());

		Assert.assertEquals(
			Collections.emptyList(),
			_objectRelationshipLocalService.getObjectRelationships(
				objectEntry.getObjectDefinitionId()));
	}

	@Test
	public void testExecuteWithRepeatableDDMFormFieldWithNestedRepeatableDDMFormFieldAsRepeatableGroups()
		throws Exception {

		DDMForm ddmForm = DDMFormTestUtil.createDDMForm();

		DDMFormField accordionDDMFormField = DDMFormTestUtil.createDDMFormField(
			"accordion", "accordion", DDMFormFieldTypeConstants.TEXT, "string",
			false, true, false);

		DDMFormField accordionDescriptionDDMFormField =
			DDMFormTestUtil.createDDMFormField(
				"accordionDescription", "accordionDescription",
				DDMFormFieldTypeConstants.TEXT, "string", false, true, false);

		accordionDescriptionDDMFormField.addNestedDDMFormField(
			DDMFormTestUtil.createDDMFormField(
				"url", "url", DDMFormFieldTypeConstants.TEXT, "string", false,
				false, false));

		accordionDDMFormField.addNestedDDMFormField(
			accordionDescriptionDDMFormField);

		ddmForm.addDDMFormField(accordionDDMFormField);

		DDMStructure ddmStructure = DDMStructureTestUtil.addStructure(
			_group.getGroupId(), JournalArticle.class.getName(), ddmForm);

		DDMTemplate ddmTemplate = DDMTemplateTestUtil.addTemplate(
			_group.getGroupId(), _portal.getClassNameId(DDMStructure.class),
			ddmStructure.getStructureId(),
			_portal.getClassNameId(JournalArticle.class),
			TemplateConstants.LANG_TYPE_FTL,
			StringBundler.concat(
				"<#list accordionFieldSet.getSiblings() as accordion>[",
				"${accordion.getChild(\"accordion\").getData()}<#list ",
				"accordion.getChild(\"accordionDescriptionFieldSet\").",
				"getSiblings() as accordionDescription>(",
				"${accordionDescription.getChild(\"accordionDescription\").",
				"getData()}|${accordionDescription.getChild(",
				"\"accordionDescriptionFieldSetFieldSet\").getChild(\"url\").",
				"getData()})</#list>]</#list>"),
			LocaleUtil.US);

		StringBundler sb = new StringBundler();

		sb.append("<root available-locales=\"en_US\" ");
		sb.append("default-locale=\"en_US\">");

		String[] accordions = {
			RandomTestUtil.randomString(), RandomTestUtil.randomString()
		};
		String[][] accordionDescriptions = {
			{RandomTestUtil.randomString(), RandomTestUtil.randomString()},
			{RandomTestUtil.randomString()}
		};
		String[][] urls = {
			{RandomTestUtil.randomString(), RandomTestUtil.randomString()},
			{RandomTestUtil.randomString()}
		};

		StringBundler expectedContentSB = new StringBundler();

		for (int i = 0; i < accordions.length; i++) {
			expectedContentSB.append("[");
			expectedContentSB.append(accordions[i]);

			String[] accordionDescriptionDynamicElements =
				new String[accordionDescriptions[i].length];

			for (int j = 0; j < accordionDescriptions[i].length; j++) {
				accordionDescriptionDynamicElements[j] = _getDynamicElement(
					accordionDescriptions[i][j], "accordionDescription",
					DDMFormFieldTypeConstants.TEXT,
					_getDynamicElement(
						urls[i][j], "url", DDMFormFieldTypeConstants.TEXT));

				expectedContentSB.append("(");
				expectedContentSB.append(accordionDescriptions[i][j]);
				expectedContentSB.append("|");
				expectedContentSB.append(urls[i][j]);
				expectedContentSB.append(")");
			}

			expectedContentSB.append("]");

			sb.append(
				_getDynamicElement(
					accordions[i], "accordion", DDMFormFieldTypeConstants.TEXT,
					accordionDescriptionDynamicElements));
		}

		sb.append("</root>");

		JournalArticle journalArticle =
			JournalTestUtil.addArticleWithXMLContent(
				_group.getGroupId(), sb.toString(),
				ddmStructure.getStructureKey(), null);

		_assertSuccessful(_execute(_group.getGroupId(), false));

		String content = _render(ddmTemplate, _getObjectEntry(journalArticle));

		Assert.assertTrue(
			content, content.contains(expectedContentSB.toString()));

		_withNewDepotEntry(
			() -> {
				BackgroundTaskResult backgroundTaskResult = _execute(
					_group.getGroupId(), true, false);

				_assertSuccessful(backgroundTaskResult);

				String statusMessage = backgroundTaskResult.getStatusMessage();

				Assert.assertFalse(
					statusMessage, statusMessage.contains(" as JSON because"));

				ObjectEntry objectEntry = _getObjectEntry(journalArticle);

				List<ObjectRelationship> objectRelationships =
					_objectRelationshipLocalService.getObjectRelationships(
						objectEntry.getObjectDefinitionId());

				ObjectRelationship objectRelationship = objectRelationships.get(
					0);

				List<ObjectRelationship> nestedObjectRelationships =
					_objectRelationshipLocalService.getObjectRelationships(
						objectRelationship.getObjectDefinitionId2());

				ObjectRelationship nestedObjectRelationship =
					nestedObjectRelationships.get(0);

				String externalReferenceCode =
					nestedObjectRelationship.getExternalReferenceCode();

				Assert.assertTrue(
					externalReferenceCode,
					externalReferenceCode.length() <= 75);

				List<ObjectEntry> accordionObjectEntries =
					_getItemObjectEntries(objectEntry);

				Assert.assertEquals(
					accordionObjectEntries.toString(), accordions.length,
					accordionObjectEntries.size());

				for (int i = 0; i < accordions.length; i++) {
					ObjectEntry accordionObjectEntry =
						accordionObjectEntries.get(i);

					Map<String, Serializable> values =
						accordionObjectEntry.getValues();

					Assert.assertEquals(
						accordions[i],
						GetterUtil.getString(values.get("accordion")));

					List<ObjectEntry> accordionDescriptionObjectEntries =
						_getItemObjectEntries(accordionObjectEntry);

					Assert.assertEquals(
						accordionDescriptionObjectEntries.toString(),
						accordionDescriptions[i].length,
						accordionDescriptionObjectEntries.size());

					for (int j = 0; j < accordionDescriptions[i].length; j++) {
						ObjectEntry accordionDescriptionObjectEntry =
							accordionDescriptionObjectEntries.get(j);

						Map<String, Serializable> accordionDescriptionValues =
							accordionDescriptionObjectEntry.getValues();

						Assert.assertEquals(
							accordionDescriptions[i][j],
							GetterUtil.getString(
								accordionDescriptionValues.get(
									"accordionDescription")));
						Assert.assertEquals(
							urls[i][j],
							GetterUtil.getString(
								accordionDescriptionValues.get("url")));
					}
				}
			});
	}

	@Test
	public void testExecuteWithRepeatableDocumentLibraryDDMFormFieldAsRepeatableGroup()
		throws Exception {

		DDMForm ddmForm = DDMFormTestUtil.createDDMForm();

		ddmForm.addDDMFormField(
			DDMFormTestUtil.createDDMFormField(
				"document", "document",
				DDMFormFieldTypeConstants.DOCUMENT_LIBRARY, "document-library",
				false, true, false));

		DDMStructure ddmStructure = DDMStructureTestUtil.addStructure(
			_group.getGroupId(), JournalArticle.class.getName(), ddmForm);

		FileEntry[] fileEntries = {
			_addFileEntry(null), _addFileEntry(null), _addFileEntry(null)
		};

		StringBundler sb = new StringBundler();

		sb.append("<root available-locales=\"en_US\" ");
		sb.append("default-locale=\"en_US\">");

		for (FileEntry fileEntry : fileEntries) {
			sb.append(
				_getDynamicElement(
					_getFileEntryData(fileEntry), "document",
					DDMFormFieldTypeConstants.DOCUMENT_LIBRARY));
		}

		sb.append(
			_getDynamicElement(
				JSONUtil.put(
					"title", RandomTestUtil.randomString()
				).toString(),
				"document", DDMFormFieldTypeConstants.DOCUMENT_LIBRARY));

		sb.append("</root>");

		JournalArticle journalArticle =
			JournalTestUtil.addArticleWithXMLContent(
				_group.getGroupId(), sb.toString(),
				ddmStructure.getStructureKey(), null);

		_assertSuccessful(_execute(_group.getGroupId(), true, false));

		ObjectEntry objectEntry = _getObjectEntry(journalArticle);

		Assert.assertNull(
			_objectFieldLocalService.fetchObjectField(
				objectEntry.getObjectDefinitionId(), "document"));

		List<ObjectRelationship> objectRelationships =
			_objectRelationshipLocalService.getObjectRelationships(
				objectEntry.getObjectDefinitionId());

		Assert.assertEquals(
			objectRelationships.toString(), 1, objectRelationships.size());

		ObjectRelationship objectRelationship = objectRelationships.get(0);

		Assert.assertTrue(objectRelationship.isEdge());
		Assert.assertEquals(
			ObjectRelationshipConstants.DELETION_TYPE_CASCADE,
			objectRelationship.getDeletionType());
		Assert.assertEquals(
			ObjectRelationshipConstants.TYPE_ONE_TO_MANY,
			objectRelationship.getType());

		ObjectDefinition repeatableGroupObjectDefinition =
			_objectDefinitionLocalService.getObjectDefinition(
				objectRelationship.getObjectDefinitionId2());

		Assert.assertFalse(
			repeatableGroupObjectDefinition.isAllowStandaloneObjectEntry());
		Assert.assertEquals(
			"CMS_MIGRATION_DDM_STRUCTURE_" + ddmStructure.getStructureId() +
				"_document",
			repeatableGroupObjectDefinition.getExternalReferenceCode());

		ObjectFolder objectFolder = _objectFolderLocalService.getObjectFolder(
			repeatableGroupObjectDefinition.getObjectFolderId());

		Assert.assertEquals(
			ObjectFolderConstants.
				EXTERNAL_REFERENCE_CODE_STRUCTURE_REPEATABLE_GROUPS,
			objectFolder.getExternalReferenceCode());

		ObjectField objectField = _objectFieldLocalService.fetchObjectField(
			repeatableGroupObjectDefinition.getObjectDefinitionId(),
			"document");

		Assert.assertEquals(
			ObjectFieldConstants.BUSINESS_TYPE_ATTACHMENT,
			objectField.getBusinessType());

		List<ObjectEntry> itemObjectEntries = _getItemObjectEntries(
			objectEntry);

		Assert.assertEquals(
			itemObjectEntries.toString(), fileEntries.length + 1,
			itemObjectEntries.size());

		for (int i = 0; i < fileEntries.length; i++) {
			ObjectEntry itemObjectEntry = itemObjectEntries.get(i);

			Assert.assertEquals(
				StringBundler.concat(
					objectEntry.getExternalReferenceCode(), "_document_", i),
				itemObjectEntry.getExternalReferenceCode());

			Map<String, Serializable> values = itemObjectEntry.getValues();

			FileEntry fileEntry = _dlAppLocalService.getFileEntry(
				GetterUtil.getLong(values.get("document")));

			Assert.assertEquals(
				fileEntries[i].getFileName(), fileEntry.getFileName());
		}

		ObjectEntry itemObjectEntry = itemObjectEntries.get(fileEntries.length);

		Map<String, Serializable> values = itemObjectEntry.getValues();

		Assert.assertEquals(0, GetterUtil.getLong(values.get("document")));
	}

	@Test
	public void testExecuteWithRepeatableFieldsetDDMFormField()
		throws Exception {

		DDMForm ddmForm = DDMFormTestUtil.createDDMForm();

		DDMFormField ddmFormField = DDMFormTestUtil.createDDMFormField(
			"fieldset", "fieldset", DDMFormFieldTypeConstants.FIELDSET,
			StringPool.BLANK, false, true, false);

		DDMFormTestUtil.addNestedTextDDMFormFields(ddmFormField, "text");

		ddmForm.addDDMFormField(ddmFormField);

		DDMStructure ddmStructure = DDMStructureTestUtil.addStructure(
			_group.getGroupId(), JournalArticle.class.getName(), ddmForm);

		DDMTemplate ddmTemplate = DDMTemplateTestUtil.addTemplate(
			_group.getGroupId(), _portal.getClassNameId(DDMStructure.class),
			ddmStructure.getStructureId(),
			_portal.getClassNameId(JournalArticle.class),
			TemplateConstants.LANG_TYPE_FTL, RandomTestUtil.randomString(),
			LocaleUtil.US);

		String[] texts = {
			"<a href=\"https://www.liferay.com\">a</a> & b",
			RandomTestUtil.randomString(65000)
		};

		JournalArticle journalArticle =
			JournalTestUtil.addArticleWithXMLContent(
				_group.getGroupId(),
				StringBundler.concat(
					"<root available-locales=\"en_US\" ",
					"default-locale=\"en_US\">",
					_getDynamicElement(
						null, "fieldset", DDMFormFieldTypeConstants.FIELDSET,
						_getDynamicElement(
							texts[0], "text", DDMFormFieldTypeConstants.TEXT)),
					_getDynamicElement(
						null, "fieldset", DDMFormFieldTypeConstants.FIELDSET,
						_getDynamicElement(
							texts[1], "text", DDMFormFieldTypeConstants.TEXT)),
					"</root>"),
				ddmStructure.getStructureKey(), null);

		_assertSuccessful(_execute(_group.getGroupId(), false));

		ObjectEntry objectEntry = _getObjectEntry(
			"CMS_MIGRATION_DDM_STRUCTURE_" + ddmStructure.getStructureId(),
			"CMS_MIGRATION_JOURNAL_ARTICLE_" +
				journalArticle.getResourcePrimKey());

		Assert.assertNull(
			_objectFieldLocalService.fetchObjectField(
				objectEntry.getObjectDefinitionId(), "text"));

		ObjectField objectField = _objectFieldLocalService.fetchObjectField(
			objectEntry.getObjectDefinitionId(), "fieldsetFieldSet");

		Assert.assertEquals(
			ObjectFieldConstants.BUSINESS_TYPE_RICH_TEXT,
			objectField.getBusinessType());

		Assert.assertEquals(
			Collections.emptyList(),
			_objectRelationshipLocalService.getObjectRelationships(
				objectEntry.getObjectDefinitionId()));

		Map<String, Serializable> values = objectEntry.getValues();

		JSONArray jsonArray = JSONFactoryUtil.createJSONArray(
			StringUtil.replace(
				GetterUtil.getString(values.get("fieldsetFieldSet")),
				new String[] {"&quot;", "&lt;", "&gt;", "&amp;"},
				new String[] {"\"", "<", ">", "&"}));

		Assert.assertEquals(texts.length, jsonArray.length());

		for (int i = 0; i < texts.length; i++) {
			Assert.assertEquals(
				texts[i],
				JSONUtil.getValueAsString(
					jsonArray.getJSONObject(i), "JSONArray/nestedFieldValues",
					"JSONObject/1", "JSONArray/nestedFieldValues",
					"JSONObject/0", "JSONObject/values", "Object/en_US"));
		}

		DDMTemplate migratedDDMTemplate =
			_ddmTemplateLocalService.getDDMTemplateByExternalReferenceCode(
				"CMS_MIGRATION_DDM_TEMPLATE_" + ddmTemplate.getTemplateId(),
				_group.getGroupId());

		String script = migratedDDMTemplate.getScript();

		Assert.assertTrue(
			script.contains("cmsMigrationContext[\"fieldsetFieldSet\"]"));
	}

	@Test
	public void testExecuteWithRepeatableFieldsetDDMFormFieldAsRepeatableGroup()
		throws Exception {

		DDMForm ddmForm = DDMFormTestUtil.createDDMForm();

		DDMFormField ddmFormField = DDMFormTestUtil.createDDMFormField(
			"fieldset", "fieldset", DDMFormFieldTypeConstants.FIELDSET,
			StringPool.BLANK, false, true, false);

		DDMFormTestUtil.addNestedTextDDMFormFields(ddmFormField, "text");

		ddmFormField.addNestedDDMFormField(
			DDMFormTestUtil.createDDMFormField(
				"image", "image", DDMFormFieldTypeConstants.IMAGE, "image",
				false, false, false));

		ddmForm.addDDMFormField(ddmFormField);

		DDMStructure ddmStructure = DDMStructureTestUtil.addStructure(
			_group.getGroupId(), JournalArticle.class.getName(), ddmForm);

		DDMTemplate ddmTemplate = DDMTemplateTestUtil.addTemplate(
			_group.getGroupId(), _portal.getClassNameId(DDMStructure.class),
			ddmStructure.getStructureId(),
			_portal.getClassNameId(JournalArticle.class),
			TemplateConstants.LANG_TYPE_FTL,
			StringBundler.concat(
				"<#list fieldsetFieldSet.getSiblings() as item>[",
				"${item.getChild(\"fieldsetFieldSetFieldSet\").",
				"getChild(\"text\").getData()}|",
				"${item.getChild(\"fieldsetFieldSetFieldSet\").",
				"getChild(\"image\").getAttribute(\"name\")}]</#list>"),
			LocaleUtil.US);

		StringBundler sb = new StringBundler();

		sb.append("<root available-locales=\"en_US\" ");
		sb.append("default-locale=\"en_US\">");

		FileEntry[] fileEntries = {_addFileEntry(null), _addFileEntry(null)};
		String[] texts = {
			RandomTestUtil.randomString(), RandomTestUtil.randomString()
		};

		for (int i = 0; i < texts.length; i++) {
			sb.append(
				_getDynamicElement(
					null, "fieldset", DDMFormFieldTypeConstants.FIELDSET,
					_getDynamicElement(
						texts[i], "text", DDMFormFieldTypeConstants.TEXT),
					_getDynamicElement(
						_getFileEntryData(fileEntries[i]), "image",
						DDMFormFieldTypeConstants.IMAGE)));
		}

		sb.append("</root>");

		JournalArticle journalArticle =
			JournalTestUtil.addArticleWithXMLContent(
				_group.getGroupId(), sb.toString(),
				ddmStructure.getStructureKey(), null);

		_assertSuccessful(_execute(_group.getGroupId(), false));

		String content = _render(ddmTemplate, _getObjectEntry(journalArticle));

		String expectedContent = StringBundler.concat(
			"[", texts[0], "|", fileEntries[0].getFileName(), "][", texts[1],
			"|", fileEntries[1].getFileName(), "]");

		Assert.assertTrue(content, content.contains(expectedContent));

		_withNewDepotEntry(
			() -> {
				BackgroundTaskResult backgroundTaskResult = _execute(
					_group.getGroupId(), true, false);

				_assertSuccessful(backgroundTaskResult);

				String statusMessage = backgroundTaskResult.getStatusMessage();

				Assert.assertTrue(
					statusMessage,
					statusMessage.contains(
						StringBundler.concat(
							"Migrated the repeatable field fieldsetFieldSet ",
							"of the structure ", ddmStructure.getStructureKey(),
							" as a repeatable group and kept the JSON field ",
							"fieldsetFieldSet")));

				ObjectEntry objectEntry = _getObjectEntry(journalArticle);

				Assert.assertNull(
					_objectFieldLocalService.fetchObjectField(
						objectEntry.getObjectDefinitionId(), "text"));

				List<ObjectEntry> itemObjectEntries = _getItemObjectEntries(
					objectEntry);

				Assert.assertEquals(
					itemObjectEntries.toString(), texts.length,
					itemObjectEntries.size());

				for (int i = 0; i < texts.length; i++) {
					ObjectEntry itemObjectEntry = itemObjectEntries.get(i);

					Map<String, Serializable> values =
						itemObjectEntry.getValues();

					Assert.assertEquals(
						texts[i], GetterUtil.getString(values.get("text")));

					FileEntry fileEntry = _dlAppLocalService.getFileEntry(
						GetterUtil.getLong(values.get("image")));

					Assert.assertEquals(
						fileEntries[i].getFileName(), fileEntry.getFileName());
				}
			});
	}

	@Test
	public void testExecuteWithRepeatableFieldsetDDMFormFieldWithUnsupportedDDMFormField()
		throws Exception {

		DDMForm ddmForm = DDMFormTestUtil.createDDMForm();

		DDMFormField ddmFormField = DDMFormTestUtil.createDDMFormField(
			"fieldset", "fieldset", DDMFormFieldTypeConstants.FIELDSET,
			StringPool.BLANK, false, true, false);

		ddmFormField.addNestedDDMFormField(
			DDMFormTestUtil.createDDMFormField(
				"color", "color", DDMFormFieldTypeConstants.COLOR, "string",
				false, false, false));

		ddmForm.addDDMFormField(ddmFormField);

		DDMStructure ddmStructure = DDMStructureTestUtil.addStructure(
			_group.getGroupId(), JournalArticle.class.getName(), ddmForm);

		JournalArticle journalArticle =
			JournalTestUtil.addArticleWithXMLContent(
				_group.getGroupId(),
				StringBundler.concat(
					"<root available-locales=\"en_US\" ",
					"default-locale=\"en_US\">",
					_getDynamicElement(
						null, "fieldset", DDMFormFieldTypeConstants.FIELDSET,
						_getDynamicElement(
							RandomTestUtil.randomString(), "color",
							DDMFormFieldTypeConstants.COLOR)),
					"</root>"),
				ddmStructure.getStructureKey(), null);

		BackgroundTaskResult backgroundTaskResult = _execute(
			_group.getGroupId(), true, false);

		_assertSuccessful(backgroundTaskResult);

		String statusMessage = backgroundTaskResult.getStatusMessage();

		Assert.assertTrue(
			statusMessage,
			statusMessage.contains(
				StringBundler.concat(
					"Migrated the repeatable field fieldsetFieldSet of the ",
					"structure ", ddmStructure.getStructureKey(),
					" as JSON because it contains unsupported field types")));

		ObjectEntry objectEntry = _getObjectEntry(journalArticle);

		ObjectField objectField = _objectFieldLocalService.fetchObjectField(
			objectEntry.getObjectDefinitionId(), "fieldsetFieldSet");

		Assert.assertEquals(
			ObjectFieldConstants.BUSINESS_TYPE_RICH_TEXT,
			objectField.getBusinessType());

		Assert.assertEquals(
			Collections.emptyList(),
			_objectRelationshipLocalService.getObjectRelationships(
				objectEntry.getObjectDefinitionId()));
	}

	@Test
	public void testExecuteWithRepeatableNumericDDMFormFieldAsRepeatableGroup()
		throws Exception {

		DDMForm ddmForm = DDMFormTestUtil.createDDMForm();

		ddmForm.addDDMFormField(
			DDMFormTestUtil.createDDMFormField(
				"numeric", "numeric", DDMFormFieldTypeConstants.NUMERIC,
				"integer", false, true, false));

		DDMStructure ddmStructure = DDMStructureTestUtil.addStructure(
			_group.getGroupId(), JournalArticle.class.getName(), ddmForm);

		JournalArticle journalArticle =
			JournalTestUtil.addArticleWithXMLContent(
				_group.getGroupId(),
				StringBundler.concat(
					"<root available-locales=\"en_US\" ",
					"default-locale=\"en_US\">",
					_getDynamicElement(
						"0", "numeric", DDMFormFieldTypeConstants.NUMERIC),
					_getDynamicElement(
						StringPool.BLANK, "numeric",
						DDMFormFieldTypeConstants.NUMERIC),
					_getDynamicElement(
						"5", "numeric", DDMFormFieldTypeConstants.NUMERIC),
					"</root>"),
				ddmStructure.getStructureKey(), null);

		_assertSuccessful(_execute(_group.getGroupId(), true, false));

		List<ObjectEntry> itemObjectEntries = _getItemObjectEntries(
			_getObjectEntry(journalArticle));

		Assert.assertEquals(
			itemObjectEntries.toString(), 2, itemObjectEntries.size());

		ObjectEntry itemObjectEntry = itemObjectEntries.get(0);

		Map<String, Serializable> values = itemObjectEntry.getValues();

		Assert.assertEquals(0L, GetterUtil.getLong(values.get("numeric"), -1));

		itemObjectEntry = itemObjectEntries.get(1);

		values = itemObjectEntry.getValues();

		Assert.assertEquals(5L, GetterUtil.getLong(values.get("numeric")));
	}

	@Test
	public void testExecuteWithResourcePermissions() throws Exception {
		FileEntry fileEntry = _addFileEntry(StringPool.BLANK);

		Role guestRole = _roleLocalService.getRole(
			_group.getCompanyId(), RoleConstants.GUEST);

		_deleteResourcePermissions(
			DLFileEntry.class.getName(), fileEntry.getFileEntryId(), guestRole);

		JournalArticle journalArticle = JournalTestUtil.addArticle(
			_group.getGroupId(),
			JournalFolderConstants.DEFAULT_PARENT_FOLDER_ID);

		_deleteResourcePermissions(
			JournalArticle.class.getName(), journalArticle.getResourcePrimKey(),
			guestRole);
		_deleteResourcePermissions(
			JournalArticle.class.getName(), journalArticle.getResourcePrimKey(),
			_roleLocalService.getRole(
				_group.getCompanyId(), RoleConstants.SITE_MEMBER));

		_role = RoleTestUtil.addRole(RoleConstants.TYPE_REGULAR);

		_setResourcePermissions(
			JournalArticle.class.getName(), journalArticle.getResourcePrimKey(),
			_role, ActionKeys.ADD_DISCUSSION, ActionKeys.EXPIRE,
			ActionKeys.VIEW);

		_assertSuccessful(_execute(_group.getGroupId(), false));

		ObjectEntry objectEntry = _getObjectEntry(fileEntry);

		Role depotMemberRole = _roleLocalService.getRole(
			_group.getCompanyId(), DepotRolesConstants.ASSET_LIBRARY_MEMBER);

		Assert.assertTrue(
			_hasViewResourcePermission(objectEntry, depotMemberRole));

		Assert.assertFalse(_hasViewResourcePermission(objectEntry, guestRole));

		objectEntry = _getObjectEntry(journalArticle);

		Assert.assertFalse(
			_hasViewResourcePermission(objectEntry, depotMemberRole));

		Assert.assertFalse(_hasViewResourcePermission(objectEntry, guestRole));
		Assert.assertEquals(
			Collections.singletonList(ActionKeys.VIEW),
			_resourcePermissionLocalService.
				getAvailableResourcePermissionActionIds(
					objectEntry.getCompanyId(), objectEntry.getModelClassName(),
					ResourceConstants.SCOPE_INDIVIDUAL,
					String.valueOf(objectEntry.getObjectEntryId()),
					_role.getRoleId(),
					ResourceActionsUtil.getModelResourceActions(
						objectEntry.getModelClassName())));

		_setResourcePermissions(
			objectEntry.getModelClassName(), objectEntry.getObjectEntryId(),
			guestRole, ActionKeys.VIEW);

		_assertSuccessful(_execute(_group.getGroupId(), false));

		Assert.assertFalse(_hasViewResourcePermission(objectEntry, guestRole));
		Assert.assertTrue(_hasViewResourcePermission(objectEntry, _role));
	}

	@Test
	public void testExecuteWithResourcePermissionsAndSiteRole()
		throws Exception {

		JournalArticle journalArticle = JournalTestUtil.addArticle(
			_group.getGroupId(),
			JournalFolderConstants.DEFAULT_PARENT_FOLDER_ID);

		Role guestRole = _roleLocalService.getRole(
			_group.getCompanyId(), RoleConstants.GUEST);

		_deleteResourcePermissions(
			JournalArticle.class.getName(), journalArticle.getResourcePrimKey(),
			guestRole);

		_role = RoleTestUtil.addRole(RoleConstants.TYPE_SITE);

		_setResourcePermissions(
			JournalArticle.class.getName(), journalArticle.getResourcePrimKey(),
			_role, ActionKeys.VIEW);

		BackgroundTaskResult backgroundTaskResult = _execute(
			_group.getGroupId(), false);

		_assertSuccessful(backgroundTaskResult);

		String statusMessage = backgroundTaskResult.getStatusMessage();

		Assert.assertTrue(
			statusMessage,
			statusMessage.contains(
				"Skipped the permissions of the role " + _role.getName()));

		ObjectEntry objectEntry = _getObjectEntry(journalArticle);

		Role depotMemberRole = _roleLocalService.getRole(
			_group.getCompanyId(), DepotRolesConstants.ASSET_LIBRARY_MEMBER);

		Assert.assertTrue(
			_hasViewResourcePermission(objectEntry, depotMemberRole));

		Assert.assertFalse(_hasViewResourcePermission(objectEntry, guestRole));
		Assert.assertFalse(_hasViewResourcePermission(objectEntry, _role));
	}

	@Test
	public void testExecuteWithRewritePages() throws Exception {
		JournalArticle journalArticle = JournalTestUtil.addArticle(
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
							String.valueOf(journalArticle.getResourcePrimKey())
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
			portletLayout, journalArticle);

		_assertSuccessful(_execute(_group.getGroupId(), true));

		ObjectEntry objectEntry = _getObjectEntry(journalArticle);

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
	public void testExecuteWithRewritePagesAndAssetPublisherTagFilters()
		throws Exception {

		String assetTagName = RandomTestUtil.randomString();

		ServiceContext serviceContext =
			ServiceContextTestUtil.getServiceContext(_group.getGroupId());

		serviceContext.setAssetTagNames(new String[] {assetTagName});

		JournalArticle journalArticle = JournalTestUtil.addArticle(
			_group.getGroupId(),
			JournalFolderConstants.DEFAULT_PARENT_FOLDER_ID,
			JournalArticleConstants.CLASS_NAME_ID_DEFAULT,
			RandomTestUtil.randomString(), RandomTestUtil.randomString(),
			RandomTestUtil.randomString(), LocaleUtil.getSiteDefault(), false,
			true, serviceContext);

		Layout layout = LayoutTestUtil.addTypePortletLayout(_group);

		String excludedAssetTagName = RandomTestUtil.randomString();

		LayoutTestUtil.addPortletToLayout(
			layout, AssetPublisherPortletKeys.ASSET_PUBLISHER,
			HashMapBuilder.put(
				"anyAssetType",
				new String[] {
					String.valueOf(_portal.getClassNameId(JournalArticle.class))
				}
			).put(
				"anyClassTypeJournalArticleAssetRendererFactory",
				new String[] {
					String.valueOf(journalArticle.getDDMStructureId())
				}
			).put(
				"queryAndOperator0", new String[] {"false"}
			).put(
				"queryAndOperator1", new String[] {"false"}
			).put(
				"queryContains0", new String[] {"true"}
			).put(
				"queryContains1", new String[] {"false"}
			).put(
				"queryName0", new String[] {"assetTags"}
			).put(
				"queryName1", new String[] {"assetTags"}
			).put(
				"queryValues0", new String[] {assetTagName}
			).put(
				"queryValues1", new String[] {excludedAssetTagName}
			).put(
				"selectionStyle", new String[] {"dynamic"}
			).build());

		BackgroundTaskResult backgroundTaskResult = _execute(
			_group.getGroupId(), true);

		_assertSuccessful(backgroundTaskResult);

		Assert.assertEquals(
			"true",
			_getAssetPublisherPreferenceValue(layout, "queryAndOperator0"));
		Assert.assertEquals(
			"false",
			_getAssetPublisherPreferenceValue(layout, "queryAndOperator1"));

		String statusMessage = backgroundTaskResult.getStatusMessage();

		Assert.assertTrue(
			statusMessage,
			statusMessage.contains(
				"shows content with the tags " + assetTagName));
		Assert.assertTrue(
			statusMessage,
			statusMessage.contains(
				"hides content with the tags " + excludedAssetTagName));
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

		Assert.assertFalse(content.contains("2 Minute Read"));

		content = _renderAssetPublisherWidgetTemplate(
			blogsEntry, readingTimeUnsetLayout);

		Assert.assertTrue(content.contains("2 Minute Read"));
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

	@Test
	public void testExecuteWithRewritePagesAndDisplayPageTemplateWithoutContent()
		throws Exception {

		DDMStructure ddmStructure = DDMStructureTestUtil.addStructure(
			_group.getGroupId(), JournalArticle.class.getName());

		DDMTemplate ddmTemplate = DDMTemplateTestUtil.addTemplate(
			_group.getGroupId(), _portal.getClassNameId(DDMStructure.class),
			ddmStructure.getStructureId(),
			_portal.getClassNameId(JournalArticle.class),
			RandomTestUtil.randomString(), TemplateConstants.LANG_TYPE_FTL,
			"${.vars['reserved-article-title'].data}",
			LocaleUtil.getSiteDefault());

		LayoutPageTemplateEntry layoutPageTemplateEntry =
			_layoutPageTemplateEntryLocalService.addLayoutPageTemplateEntry(
				null, TestPropsValues.getUserId(), _group.getGroupId(), 0, null,
				_portal.getClassNameId(JournalArticle.class),
				ddmStructure.getStructureKey(), RandomTestUtil.randomString(),
				LayoutPageTemplateEntryTypeConstants.DISPLAY_PAGE, 0,
				WorkflowConstants.STATUS_APPROVED,
				ServiceContextTestUtil.getServiceContext(_group.getGroupId()));

		Layout layout = _layoutLocalService.getLayout(
			layoutPageTemplateEntry.getPlid());

		ContentLayoutTestUtil.addFragmentEntryLinkToLayout(
			JSONUtil.put(
				"com.liferay.fragment.entry.processor.editable." +
					"EditableFragmentEntryProcessor",
				JSONUtil.put(
					"element-html",
					JSONUtil.put(
						"defaultValue", RandomTestUtil.randomString()
					).put(
						"mappedField",
						"ddmTemplate_" + ddmTemplate.getTemplateKey()
					))
			).toString(),
			layout,
			_segmentsExperienceLocalService.fetchDefaultSegmentsExperienceId(
				layout.getPlid()));

		_assertSuccessful(_execute(_group.getGroupId(), true));

		LayoutPageTemplateEntry newLayoutPageTemplateEntry =
			_layoutPageTemplateEntryLocalService.
				getLayoutPageTemplateEntryByExternalReferenceCode(
					"CMS_MIGRATION_LAYOUT_PAGE_TEMPLATE_ENTRY_" +
						layoutPageTemplateEntry.getLayoutPageTemplateEntryId(),
					_group.getGroupId());

		List<FragmentEntryLink> fragmentEntryLinks =
			_fragmentEntryLinkLocalService.getFragmentEntryLinksByPlid(
				_group.getGroupId(), newLayoutPageTemplateEntry.getPlid());

		Assert.assertEquals(
			fragmentEntryLinks.toString(), 1, fragmentEntryLinks.size());

		FragmentEntryLink fragmentEntryLink = fragmentEntryLinks.get(0);

		JSONObject editableValuesJSONObject = JSONFactoryUtil.createJSONObject(
			fragmentEntryLink.getEditableValues());

		TemplateEntry templateEntry =
			_templateEntryLocalService.
				fetchTemplateEntryByExternalReferenceCode(
					"CMS_MIGRATION_DDM_TEMPLATE_" + ddmTemplate.getTemplateId(),
					_group.getGroupId());

		Assert.assertEquals(
			"ddmTemplate__ddmTemplate_" + templateEntry.getTemplateEntryId(),
			editableValuesJSONObject.getJSONObject(
				"com.liferay.fragment.entry.processor.editable." +
					"EditableFragmentEntryProcessor"
			).getJSONObject(
				"element-html"
			).getString(
				"mappedField"
			));
	}

	@Test
	public void testExecuteWithRewritePagesAndDisplayPageURL()
		throws Exception {

		JournalArticle journalArticle = JournalTestUtil.addArticle(
			_group.getGroupId(),
			JournalFolderConstants.DEFAULT_PARENT_FOLDER_ID);

		Layout layout = LayoutTestUtil.addTypeContentLayout(_group);

		FragmentEntryLink fragmentEntryLink =
			ContentLayoutTestUtil.addFragmentEntryLinkToLayout(
				JSONUtil.put(
					"com.liferay.fragment.entry.processor.editable." +
						"EditableFragmentEntryProcessor",
					JSONUtil.put(
						"element-link",
						JSONUtil.put(
							"config",
							JSONUtil.put(
								"classNameId",
								String.valueOf(
									_portal.getClassNameId(
										JournalArticle.class))
							).put(
								"classPK",
								String.valueOf(
									journalArticle.getResourcePrimKey())
							).put(
								"fieldId", "JournalArticle_displayPageURL"
							)
						).put(
							"defaultValue", RandomTestUtil.randomString()
						))
				).toString(),
				layout,
				_segmentsExperienceLocalService.
					fetchDefaultSegmentsExperienceId(layout.getPlid()));

		_assertSuccessful(_execute(_group.getGroupId(), true));

		fragmentEntryLink = _fragmentEntryLinkLocalService.getFragmentEntryLink(
			fragmentEntryLink.getFragmentEntryLinkId());

		JSONObject editableValuesJSONObject = JSONFactoryUtil.createJSONObject(
			fragmentEntryLink.getEditableValues());

		JSONObject configJSONObject = editableValuesJSONObject.getJSONObject(
			"com.liferay.fragment.entry.processor.editable." +
				"EditableFragmentEntryProcessor"
		).getJSONObject(
			"element-link"
		).getJSONObject(
			"config"
		);

		Assert.assertEquals(
			"ObjectEntry_displayPageURL",
			configJSONObject.getString("fieldId"));
	}

	@Test
	public void testExecuteWithRewritePagesAndDraftLayout() throws Exception {
		JournalArticle journalArticle = JournalTestUtil.addArticle(
			_group.getGroupId(),
			JournalFolderConstants.DEFAULT_PARENT_FOLDER_ID);

		Layout layout = LayoutTestUtil.addTypeContentLayout(_group);

		Layout draftLayout = layout.fetchDraftLayout();

		String portletId = PortletIdCodec.encode(
			JournalContentPortletKeys.JOURNAL_CONTENT,
			RandomTestUtil.randomString());

		for (Layout curLayout : new Layout[] {layout, draftLayout}) {
			PortletPreferences portletPreferences =
				LayoutTestUtil.getPortletPreferences(curLayout, portletId);

			portletPreferences.setValue(
				"articleId", journalArticle.getArticleId());
			portletPreferences.setValue(
				"groupId", String.valueOf(journalArticle.getGroupId()));

			portletPreferences.store();
		}

		_layoutLocalService.updateStatus(
			TestPropsValues.getUserId(), draftLayout.getPlid(),
			WorkflowConstants.STATUS_APPROVED,
			ServiceContextTestUtil.getServiceContext(_group.getGroupId()));

		_assertSuccessful(_execute(_group.getGroupId(), true));

		String newPortletId = PortletIdCodec.encode(
			_CMS_CONTENT_DISPLAY_PORTLET_NAME,
			PortletIdCodec.decodeInstanceId(portletId));

		ObjectEntry objectEntry = _getObjectEntry(journalArticle);

		for (Layout curLayout : new Layout[] {layout, draftLayout}) {
			Assert.assertNull(
				_portletPreferencesLocalService.fetchPortletPreferences(
					PortletKeys.PREFS_OWNER_ID_DEFAULT,
					PortletKeys.PREFS_OWNER_TYPE_LAYOUT, curLayout.getPlid(),
					portletId));

			PortletPreferences portletPreferences =
				_portletPreferencesLocalService.getPreferences(
					curLayout.getCompanyId(),
					PortletKeys.PREFS_OWNER_ID_DEFAULT,
					PortletKeys.PREFS_OWNER_TYPE_LAYOUT, curLayout.getPlid(),
					newPortletId);

			Assert.assertEquals(
				String.valueOf(objectEntry.getObjectEntryId()),
				portletPreferences.getValue("objectEntryId", StringPool.BLANK));
		}

		draftLayout = _layoutLocalService.getLayout(draftLayout.getPlid());

		Assert.assertEquals(
			WorkflowConstants.STATUS_APPROVED, draftLayout.getStatus());
	}

	@Test
	public void testExecuteWithRewritePagesAndInformationTemplate()
		throws Exception {

		JournalArticle journalArticle = JournalTestUtil.addArticle(
			_group.getGroupId(),
			JournalFolderConstants.DEFAULT_PARENT_FOLDER_ID);

		long templateEntryClassNameId = _portal.getClassNameId(
			TemplateEntry.class);

		ServiceContext serviceContext =
			ServiceContextTestUtil.getServiceContext(_group.getGroupId());

		DDMTemplate ddmTemplate = _ddmTemplateLocalService.addTemplate(
			RandomTestUtil.randomString(), TestPropsValues.getUserId(),
			_group.getGroupId(), templateEntryClassNameId, 0,
			templateEntryClassNameId,
			HashMapBuilder.put(
				LocaleUtil.getSiteDefault(), RandomTestUtil.randomString()
			).build(),
			null, DDMTemplateConstants.TEMPLATE_TYPE_DISPLAY, StringPool.BLANK,
			TemplateConstants.LANG_TYPE_FTL,
			"<h1>${JournalArticle_title.getData()}</h1>" +
				"${.vars[\"DDMStructure_name\"].getData()}",
			serviceContext);

		TemplateEntry templateEntry =
			_templateEntryLocalService.addTemplateEntry(
				RandomTestUtil.randomString(), TestPropsValues.getUserId(),
				_group.getGroupId(), ddmTemplate.getTemplateId(),
				JournalArticle.class.getName(),
				String.valueOf(journalArticle.getDDMStructureId()),
				serviceContext);

		Layout layout = LayoutTestUtil.addTypeContentLayout(_group);

		FragmentEntryLink fragmentEntryLink =
			ContentLayoutTestUtil.addFragmentEntryLinkToLayout(
				JSONUtil.put(
					"com.liferay.fragment.entry.processor.editable." +
						"EditableFragmentEntryProcessor",
					JSONUtil.put(
						"element-html",
						JSONUtil.put(
							"classNameId",
							String.valueOf(
								_portal.getClassNameId(JournalArticle.class))
						).put(
							"classPK",
							String.valueOf(journalArticle.getResourcePrimKey())
						).put(
							"defaultValue", RandomTestUtil.randomString()
						).put(
							"fieldId",
							"ddmTemplate__ddmTemplate_" +
								templateEntry.getTemplateEntryId()
						))
				).toString(),
				layout,
				_segmentsExperienceLocalService.
					fetchDefaultSegmentsExperienceId(layout.getPlid()));

		_assertSuccessful(_execute(_group.getGroupId(), true));

		TemplateEntry migratedTemplateEntry =
			_templateEntryLocalService.getTemplateEntryByExternalReferenceCode(
				"CMS_MIGRATION_DDM_TEMPLATE_" + ddmTemplate.getTemplateId(),
				_group.getGroupId());

		ObjectDefinition objectDefinition =
			_objectDefinitionLocalService.
				getObjectDefinitionByExternalReferenceCode(
					"CMS_MIGRATION_DDM_STRUCTURE_" +
						journalArticle.getDDMStructureId(),
					_group.getCompanyId());

		Assert.assertEquals(
			objectDefinition.getClassName(),
			migratedTemplateEntry.getInfoItemClassName());

		DDMTemplate migratedDDMTemplate = _ddmTemplateLocalService.getTemplate(
			migratedTemplateEntry.getDDMTemplateId());

		String script = migratedDDMTemplate.getScript();

		Assert.assertTrue(script, script.contains("ObjectField_title"));
		Assert.assertTrue(
			script, script.contains(".vars[\"ObjectField_name\"]"));
		Assert.assertFalse(script, script.contains("DDMStructure_"));
		Assert.assertFalse(script, script.contains("JournalArticle_"));

		fragmentEntryLink = _fragmentEntryLinkLocalService.getFragmentEntryLink(
			fragmentEntryLink.getFragmentEntryLinkId());

		JSONObject editableValuesJSONObject = JSONFactoryUtil.createJSONObject(
			fragmentEntryLink.getEditableValues());

		JSONObject elementHTMLJSONObject =
			editableValuesJSONObject.getJSONObject(
				"com.liferay.fragment.entry.processor.editable." +
					"EditableFragmentEntryProcessor"
			).getJSONObject(
				"element-html"
			);

		Assert.assertEquals(
			"ddmTemplate__ddmTemplate_" +
				migratedTemplateEntry.getTemplateEntryId(),
			elementHTMLJSONObject.getString("fieldId"));
	}

	@Test
	public void testExecuteWithRewritePagesAndPortletPermissions()
		throws Exception {

		JournalArticle journalArticle = JournalTestUtil.addArticle(
			_group.getGroupId(),
			JournalFolderConstants.DEFAULT_PARENT_FOLDER_ID);

		Layout customPermissionsLayout = LayoutTestUtil.addTypePortletLayout(
			_group);

		String customPermissionsPortletId = _addJournalContentPortlet(
			customPermissionsLayout, journalArticle);

		Role guestRole = _roleLocalService.getRole(
			_group.getCompanyId(), RoleConstants.GUEST);
		Role siteMemberRole = _roleLocalService.getRole(
			_group.getCompanyId(), RoleConstants.SITE_MEMBER);

		_resourcePermissionLocalService.setResourcePermissions(
			_group.getCompanyId(), JournalContentPortletKeys.JOURNAL_CONTENT,
			ResourceConstants.SCOPE_INDIVIDUAL,
			PortletPermissionUtil.getPrimaryKey(
				customPermissionsLayout.getPlid(), customPermissionsPortletId),
			HashMapBuilder.put(
				guestRole.getRoleId(), new String[0]
			).put(
				siteMemberRole.getRoleId(), new String[] {ActionKeys.VIEW}
			).build());

		Layout defaultPermissionsLayout = LayoutTestUtil.addTypePortletLayout(
			_group);

		String defaultPermissionsPortletId = _addJournalContentPortlet(
			defaultPermissionsLayout, journalArticle);

		_assertSuccessful(_execute(_group.getGroupId(), true));

		_assertPortletPermissions(
			guestRole, false, customPermissionsLayout,
			customPermissionsPortletId, siteMemberRole, true);
		_assertPortletPermissions(
			guestRole, true, defaultPermissionsLayout,
			defaultPermissionsPortletId, siteMemberRole, true);
	}

	@Test
	public void testExecuteWithUnavailableDefaultLanguage() throws Exception {
		Map<Locale, String> contentMap = HashMapBuilder.put(
			LocaleUtil.US, RandomTestUtil.randomString()
		).build();
		Map<Locale, String> descriptionMap = HashMapBuilder.put(
			LocaleUtil.US, RandomTestUtil.randomString()
		).build();
		Map<Locale, String> titleMap = HashMapBuilder.put(
			LocaleUtil.US, RandomTestUtil.randomString()
		).build();

		ObjectEntry objectEntry = _migrateWithUnavailableDefaultLanguage(
			contentMap, descriptionMap, titleMap);

		Assert.assertEquals("es_ES", objectEntry.getDefaultLanguageId());

		Map<String, Serializable> values = objectEntry.getValues();

		Assert.assertEquals(
			HashMapBuilder.put(
				"es_ES", contentMap.get(LocaleUtil.US)
			).build(),
			values.get("content_i18n"));
		Assert.assertEquals(
			HashMapBuilder.put(
				"es_ES", descriptionMap.get(LocaleUtil.US)
			).build(),
			values.get("description_i18n"));

		Assert.assertEquals(
			titleMap.get(LocaleUtil.US), objectEntry.getTitleValue("es_ES"));
	}

	@Test
	public void testExecuteWithUnavailableDefaultLanguageAndTranslation()
		throws Exception {

		Map<Locale, String> contentMap = HashMapBuilder.put(
			LocaleUtil.SPAIN, RandomTestUtil.randomString()
		).put(
			LocaleUtil.US, RandomTestUtil.randomString()
		).build();
		Map<Locale, String> descriptionMap = HashMapBuilder.put(
			LocaleUtil.SPAIN, RandomTestUtil.randomString()
		).put(
			LocaleUtil.US, RandomTestUtil.randomString()
		).build();
		Map<Locale, String> titleMap = HashMapBuilder.put(
			LocaleUtil.SPAIN, RandomTestUtil.randomString()
		).put(
			LocaleUtil.US, RandomTestUtil.randomString()
		).build();

		ObjectEntry objectEntry = _migrateWithUnavailableDefaultLanguage(
			contentMap, descriptionMap, titleMap);

		Assert.assertEquals("es_ES", objectEntry.getDefaultLanguageId());

		Map<String, Serializable> values = objectEntry.getValues();

		Assert.assertEquals(
			HashMapBuilder.put(
				"es_ES", contentMap.get(LocaleUtil.SPAIN)
			).build(),
			values.get("content_i18n"));
		Assert.assertEquals(
			HashMapBuilder.put(
				"es_ES", descriptionMap.get(LocaleUtil.SPAIN)
			).build(),
			values.get("description_i18n"));

		Assert.assertEquals(
			titleMap.get(LocaleUtil.SPAIN), objectEntry.getTitleValue("es_ES"));
	}

	@Test
	public void testExecuteWithUntranslatedDDMStructureName() throws Exception {
		DDMStructure ddmStructure = DDMStructureTestUtil.addStructure(
			_group.getGroupId(), JournalArticle.class.getName());

		JournalTestUtil.addArticleWithXMLContent(
			_group.getGroupId(),
			DDMStructureTestUtil.getSampleStructuredContent(),
			ddmStructure.getStructureKey(), null);

		_executeWithSiteDefaultLocale(LocaleUtil.SPAIN);

		ObjectDefinition objectDefinition =
			_objectDefinitionLocalService.
				getObjectDefinitionByExternalReferenceCode(
					"CMS_MIGRATION_DDM_STRUCTURE_" +
						ddmStructure.getStructureId(),
					_group.getCompanyId());

		Assert.assertEquals(
			ddmStructure.getName(LocaleUtil.US),
			objectDefinition.getLabel(LocaleUtil.SPAIN));
	}

	@Test
	public void testExecuteWithUntranslatedDDMStructureNameInDefaultLanguage()
		throws Exception {

		DDMStructure ddmStructure = DDMStructureTestUtil.addStructure(
			_group.getGroupId(), JournalArticle.class.getName());

		ddmStructure.setNameMap(ddmStructure.getNameMap(), LocaleUtil.SPAIN);

		ddmStructure = _ddmStructureLocalService.updateDDMStructure(
			ddmStructure);

		JournalTestUtil.addArticleWithXMLContent(
			_group.getGroupId(),
			DDMStructureTestUtil.getSampleStructuredContent(),
			ddmStructure.getStructureKey(), null);

		_executeWithSiteDefaultLocale(LocaleUtil.SPAIN);

		ObjectDefinition objectDefinition =
			_objectDefinitionLocalService.
				getObjectDefinitionByExternalReferenceCode(
					"CMS_MIGRATION_DDM_STRUCTURE_" +
						ddmStructure.getStructureId(),
					_group.getCompanyId());

		Assert.assertEquals(
			ddmStructure.getName(LocaleUtil.US),
			objectDefinition.getLabel(LocaleUtil.SPAIN));
	}

	@Test
	public void testExecuteWithUntranslatedDDMTemplateNameAndDifferentDefaultLanguage()
		throws Exception {

		DDMStructure ddmStructure = DDMStructureTestUtil.addStructure(
			_group.getGroupId(), JournalArticle.class.getName());

		DDMTemplate ddmTemplate = DDMTemplateTestUtil.addTemplate(
			_group.getGroupId(), ddmStructure.getStructureId(),
			_portal.getClassNameId(JournalArticle.class),
			TemplateConstants.LANG_TYPE_FTL, RandomTestUtil.randomString(),
			LocaleUtil.US);

		JournalTestUtil.addArticleWithXMLContent(
			_group.getGroupId(),
			DDMStructureTestUtil.getSampleStructuredContent(),
			ddmStructure.getStructureKey(), ddmTemplate.getTemplateKey());

		_executeWithSiteDefaultLocale(LocaleUtil.SPAIN);

		DDMTemplate migratedDDMTemplate =
			_ddmTemplateLocalService.getDDMTemplateByExternalReferenceCode(
				"CMS_MIGRATION_DDM_TEMPLATE_" + ddmTemplate.getTemplateId(),
				_group.getGroupId());

		Assert.assertEquals(
			ddmTemplate.getName(LocaleUtil.US),
			migratedDDMTemplate.getName(LocaleUtil.SPAIN, false));
	}

	@Test
	public void testExecuteWithUntranslatedDDMTemplateNameInDefaultLanguage()
		throws Exception {

		DDMStructure ddmStructure = DDMStructureTestUtil.addStructure(
			_group.getGroupId(), JournalArticle.class.getName());

		DDMTemplate ddmTemplate = DDMTemplateTestUtil.addTemplate(
			_group.getGroupId(), ddmStructure.getStructureId(),
			_portal.getClassNameId(JournalArticle.class),
			TemplateConstants.LANG_TYPE_FTL, RandomTestUtil.randomString(),
			LocaleUtil.US);

		ddmTemplate.setNameMap(ddmTemplate.getNameMap(), LocaleUtil.SPAIN);

		ddmTemplate = _ddmTemplateLocalService.updateDDMTemplate(ddmTemplate);

		JournalTestUtil.addArticleWithXMLContent(
			_group.getGroupId(),
			DDMStructureTestUtil.getSampleStructuredContent(),
			ddmStructure.getStructureKey(), ddmTemplate.getTemplateKey());

		_executeWithSiteDefaultLocale(LocaleUtil.SPAIN);

		DDMTemplate migratedDDMTemplate =
			_ddmTemplateLocalService.getDDMTemplateByExternalReferenceCode(
				"CMS_MIGRATION_DDM_TEMPLATE_" + ddmTemplate.getTemplateId(),
				_group.getGroupId());

		Assert.assertEquals(
			ddmTemplate.getName(LocaleUtil.US),
			migratedDDMTemplate.getName(LocaleUtil.SPAIN, false));
	}

	@Test
	public void testExecuteWithUntranslatedDLFileEntryTypeName()
		throws Exception {

		FileEntry fileEntry = _addFileEntry(StringPool.BLANK);

		DLFileEntryType dlFileEntryType =
			_dlFileEntryTypeLocalService.getFileEntryType(
				DLFileEntryTypeConstants.FILE_ENTRY_TYPE_ID_BASIC_DOCUMENT);

		String originalName = dlFileEntryType.getName();

		dlFileEntryType.setNameMap(
			HashMapBuilder.put(
				LocaleUtil.US, DLFileEntryTypeConstants.NAME_BASIC_DOCUMENT
			).build(),
			LocaleUtil.US);

		dlFileEntryType = _dlFileEntryTypeLocalService.updateDLFileEntryType(
			dlFileEntryType);

		try {
			_executeWithSiteDefaultLocale(LocaleUtil.SPAIN);
		}
		finally {
			dlFileEntryType.setName(originalName);

			_dlFileEntryTypeLocalService.updateDLFileEntryType(dlFileEntryType);
		}

		ObjectEntry objectEntry = _getObjectEntry(fileEntry);

		Map<String, Serializable> values = objectEntry.getValues();

		Assert.assertTrue(GetterUtil.getLong(values.get("file")) > 0);
	}

	private DepotEntry _addDepotEntry() throws Exception {
		return _depotEntryLocalService.addDepotEntry(
			HashMapBuilder.put(
				LocaleUtil.getDefault(), RandomTestUtil.randomString()
			).build(),
			HashMapBuilder.put(
				LocaleUtil.getDefault(), RandomTestUtil.randomString()
			).build(),
			DepotConstants.TYPE_SPACE,
			ServiceContextTestUtil.getServiceContext(_group.getGroupId()));
	}

	private void _addDynamicElement(
		Element rootElement, String data, String languageId, String name,
		String type) {

		Element dynamicElementElement = rootElement.addElement(
			"dynamic-element");

		dynamicElementElement.addAttribute("index-type", "text");
		dynamicElementElement.addAttribute("name", name);
		dynamicElementElement.addAttribute("type", type);

		Element dynamicContentElement = dynamicElementElement.addElement(
			"dynamic-content");

		dynamicContentElement.addAttribute("language-id", languageId);
		dynamicContentElement.addCDATA(data);
	}

	private FileEntry _addFileEntry(String description) throws Exception {
		return _dlAppLocalService.addFileEntry(
			null, TestPropsValues.getUserId(), _group.getGroupId(),
			DLFolderConstants.DEFAULT_PARENT_FOLDER_ID,
			RandomTestUtil.randomString() + ".txt", ContentTypes.TEXT_PLAIN,
			RandomTestUtil.randomString(), null, description, null,
			RandomTestUtil.randomBytes(), null, null, null,
			ServiceContextTestUtil.getServiceContext(_group.getGroupId()));
	}

	private JournalArticle _addJournalArticleWithImage(
			String alternativeText, FileEntry fileEntry, Locale locale)
		throws Exception {

		DDMForm ddmForm = DDMFormTestUtil.createDDMForm("name");

		ddmForm.addDDMFormField(
			DDMFormTestUtil.createDDMFormField(
				"image", "image", DDMFormFieldTypeConstants.IMAGE, "image",
				true, false, false));

		DDMStructure ddmStructure = DDMStructureTestUtil.addStructure(
			_group.getGroupId(), JournalArticle.class.getName(), ddmForm);

		Document document = SAXReaderUtil.createDocument();

		Element rootElement = document.addElement("root");

		String languageId = LocaleUtil.toLanguageId(locale);

		rootElement.addAttribute("available-locales", languageId);
		rootElement.addAttribute("default-locale", languageId);

		_addDynamicElement(
			rootElement, RandomTestUtil.randomString(), languageId, "name",
			"text");
		_addDynamicElement(
			rootElement,
			JSONUtil.put(
				"alt", alternativeText
			).put(
				"fileEntryId", fileEntry.getFileEntryId()
			).put(
				"groupId", fileEntry.getGroupId()
			).put(
				"name", fileEntry.getFileName()
			).put(
				"title", fileEntry.getTitle()
			).put(
				"type", "document"
			).put(
				"uuid", fileEntry.getUuid()
			).toString(),
			languageId, "image", "image");

		return JournalTestUtil.addArticleWithXMLContent(
			_group.getGroupId(),
			JournalFolderConstants.DEFAULT_PARENT_FOLDER_ID,
			JournalArticleConstants.CLASS_NAME_ID_DEFAULT, document.asXML(),
			ddmStructure.getStructureKey(), null, locale);
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

	private DDMStructure _addSelectDDMStructure(String... optionValues)
		throws Exception {

		DDMForm ddmForm = DDMFormTestUtil.createDDMForm("name");

		DDMFormField ddmFormField = DDMFormTestUtil.createDDMFormField(
			"choice", "choice", DDMFormFieldTypeConstants.SELECT, "string",
			false, false, false);

		DDMFormFieldOptions ddmFormFieldOptions =
			ddmFormField.getDDMFormFieldOptions();

		for (String optionValue : optionValues) {
			ddmFormFieldOptions.addOptionLabel(
				optionValue, LocaleUtil.US, optionValue);
		}

		ddmForm.addDDMFormField(ddmFormField);

		return DDMStructureTestUtil.addStructure(
			_group.getGroupId(), JournalArticle.class.getName(), ddmForm);
	}

	private void _assertPortletPermissions(
			Role guestRole, boolean guestRoleView, Layout layout,
			String oldPortletId, Role siteMemberRole,
			boolean siteMemberRoleView)
		throws Exception {

		Assert.assertEquals(
			0,
			_resourcePermissionLocalService.getResourcePermissionsCount(
				layout.getCompanyId(),
				JournalContentPortletKeys.JOURNAL_CONTENT,
				ResourceConstants.SCOPE_INDIVIDUAL,
				PortletPermissionUtil.getPrimaryKey(
					layout.getPlid(), oldPortletId)));

		String newPrimKey = PortletPermissionUtil.getPrimaryKey(
			layout.getPlid(),
			PortletIdCodec.encode(
				_CMS_CONTENT_DISPLAY_PORTLET_NAME,
				PortletIdCodec.decodeInstanceId(oldPortletId)));

		Assert.assertEquals(
			guestRoleView,
			_resourcePermissionLocalService.hasResourcePermission(
				layout.getCompanyId(), _CMS_CONTENT_DISPLAY_PORTLET_NAME,
				ResourceConstants.SCOPE_INDIVIDUAL, newPrimKey,
				guestRole.getRoleId(), ActionKeys.VIEW));
		Assert.assertEquals(
			siteMemberRoleView,
			_resourcePermissionLocalService.hasResourcePermission(
				layout.getCompanyId(), _CMS_CONTENT_DISPLAY_PORTLET_NAME,
				ResourceConstants.SCOPE_INDIVIDUAL, newPrimKey,
				siteMemberRole.getRoleId(), ActionKeys.VIEW));
	}

	private void _assertSuccessful(BackgroundTaskResult backgroundTaskResult) {
		Assert.assertEquals(
			backgroundTaskResult.getStatusMessage(),
			BackgroundTaskConstants.STATUS_SUCCESSFUL,
			backgroundTaskResult.getStatus());
	}

	private void _deleteDepotEntry(DepotEntry depotEntry) throws Exception {
		for (DepotEntryGroupRel depotEntryGroupRel :
				_depotEntryGroupRelLocalService.getDepotEntryGroupRels(
					depotEntry)) {

			_depotEntryGroupRelLocalService.deleteDepotEntryGroupRel(
				depotEntryGroupRel);
		}

		_depotEntryLocalService.deleteDepotEntry(depotEntry);
	}

	private void _deleteObjectDefinition(ObjectDefinition objectDefinition)
		throws Exception {

		List<ObjectRelationship> objectRelationships =
			_objectRelationshipLocalService.getObjectRelationships(
				objectDefinition.getObjectDefinitionId());

		for (ObjectRelationship objectRelationship : objectRelationships) {
			if (!objectRelationship.isEdge()) {
				continue;
			}

			_objectRelationshipLocalService.updateObjectRelationship(
				objectRelationship.getExternalReferenceCode(),
				objectRelationship.getObjectRelationshipId(),
				objectRelationship.getParameterObjectFieldId(),
				objectRelationship.getDeletionType(),
				objectRelationship.getDescriptionMap(), false,
				objectRelationship.getLabelMap(), null);
		}

		_objectDefinitionLocalService.deleteObjectDefinition(
			objectDefinition.getObjectDefinitionId());

		for (ObjectRelationship objectRelationship : objectRelationships) {
			ObjectDefinition repeatableGroupObjectDefinition =
				_objectDefinitionLocalService.fetchObjectDefinition(
					objectRelationship.getObjectDefinitionId2());

			if (repeatableGroupObjectDefinition != null) {
				_deleteObjectDefinition(repeatableGroupObjectDefinition);
			}
		}
	}

	private void _deleteResourcePermissions(
			String className, long classPK, Role role)
		throws Exception {

		_resourcePermissionLocalService.setResourcePermissions(
			_group.getCompanyId(), className,
			ResourceConstants.SCOPE_INDIVIDUAL, String.valueOf(classPK),
			role.getRoleId(), new String[0]);
	}

	private BackgroundTaskResult _execute(
			long sourceGroupId, boolean rewritePages)
		throws Exception {

		return _execute(sourceGroupId, false, rewritePages);
	}

	private BackgroundTaskResult _execute(
			long sourceGroupId, boolean migrateRepeatableFields,
			boolean rewritePages)
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
				"migrateRepeatableFields", migrateRepeatableFields
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

	private void _executeWithSiteDefaultLocale(Locale locale) throws Exception {
		Locale originalDefaultLocale = LocaleThreadLocal.getDefaultLocale();
		Locale originalSiteDefaultLocale =
			LocaleThreadLocal.getSiteDefaultLocale();

		try {
			LocaleThreadLocal.setDefaultLocale(locale);
			LocaleThreadLocal.setSiteDefaultLocale(locale);

			_assertSuccessful(_execute(_group.getGroupId(), false));
		}
		finally {
			LocaleThreadLocal.setDefaultLocale(originalDefaultLocale);
			LocaleThreadLocal.setSiteDefaultLocale(originalSiteDefaultLocale);
		}
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

	private FileEntry _getAttachmentFileEntry(FileEntry fileEntry)
		throws Exception {

		ObjectEntry objectEntry = _getObjectEntry(fileEntry);

		Map<String, Serializable> values = objectEntry.getValues();

		return _dlAppLocalService.getFileEntry(
			GetterUtil.getLong(values.get("file")));
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

	private String _getDynamicElement(
		String data, String name, String type,
		String... nestedDynamicElements) {

		StringBundler sb = new StringBundler();

		sb.append("<dynamic-element field-reference=\"");
		sb.append(name);
		sb.append("\" index-type=\"text\" instance-id=\"");
		sb.append(RandomTestUtil.randomString(8));
		sb.append("\" name=\"");
		sb.append(name);
		sb.append("\" type=\"");
		sb.append(type);
		sb.append("\">");

		if (data != null) {
			sb.append("<dynamic-content language-id=\"en_US\"><![CDATA[");
			sb.append(data);
			sb.append("]]></dynamic-content>");
		}

		for (String nestedDynamicElement : nestedDynamicElements) {
			sb.append(nestedDynamicElement);
		}

		sb.append("</dynamic-element>");

		return sb.toString();
	}

	private String _getFileEntryData(FileEntry fileEntry) {
		return JSONUtil.put(
			"alt", StringPool.BLANK
		).put(
			"fileEntryId", fileEntry.getFileEntryId()
		).put(
			"groupId", fileEntry.getGroupId()
		).put(
			"name", fileEntry.getFileName()
		).put(
			"title", fileEntry.getTitle()
		).put(
			"type", "document"
		).put(
			"uuid", fileEntry.getUuid()
		).toString();
	}

	private List<ObjectEntry> _getItemObjectEntries(ObjectEntry objectEntry)
		throws Exception {

		List<ObjectEntry> itemObjectEntries = new ArrayList<>();

		for (ObjectRelationship objectRelationship :
				_objectRelationshipLocalService.getObjectRelationships(
					objectEntry.getObjectDefinitionId())) {

			itemObjectEntries.addAll(
				_objectEntryLocalService.getOneToManyObjectEntries(
					objectEntry.getGroupId(),
					objectRelationship.getObjectRelationshipId(), null, true,
					objectEntry.getObjectEntryId(), true, null,
					QueryUtil.ALL_POS, QueryUtil.ALL_POS, null));
		}

		itemObjectEntries.sort(
			Comparator.comparingInt(
				itemObjectEntry -> GetterUtil.getInteger(
					StringUtil.extractLast(
						itemObjectEntry.getExternalReferenceCode(),
						StringPool.UNDERLINE))));

		return itemObjectEntries;
	}

	private MockHttpServletRequest _getMockHttpServletRequest(Layout layout)
		throws Exception {

		MockHttpServletRequest mockHttpServletRequest =
			new MockHttpServletRequest();

		ThemeDisplay themeDisplay = ContentLayoutTestUtil.getThemeDisplay(
			_companyLocalService.getCompany(_group.getCompanyId()), _group,
			layout);

		themeDisplay.setRequest(mockHttpServletRequest);

		mockHttpServletRequest.setAttribute(
			WebKeys.THEME_DISPLAY, themeDisplay);

		return mockHttpServletRequest;
	}

	private Set<Long> _getObjectDefinitionIds() {
		DynamicQuery dynamicQuery =
			_objectDefinitionLocalService.dynamicQuery();

		dynamicQuery.add(
			RestrictionsFactoryUtil.eq("companyId", _group.getCompanyId()));
		dynamicQuery.add(
			RestrictionsFactoryUtil.like(
				"externalReferenceCode", "CMS_MIGRATION_%"));
		dynamicQuery.setProjection(
			ProjectionFactoryUtil.property("objectDefinitionId"));

		return new TreeSet<>(
			_objectDefinitionLocalService.dynamicQuery(dynamicQuery));
	}

	private ObjectEntry _getObjectEntry(FileEntry fileEntry) throws Exception {
		return _getObjectEntry(
			"CMS_MIGRATION_DL_FILE_ENTRY_TYPE_0",
			"CMS_MIGRATION_FILE_ENTRY_" + fileEntry.getFileEntryId());
	}

	private ObjectEntry _getObjectEntry(JournalArticle journalArticle)
		throws Exception {

		return _getObjectEntry(
			"CMS_MIGRATION_DDM_STRUCTURE_" + journalArticle.getDDMStructureId(),
			"CMS_MIGRATION_JOURNAL_ARTICLE_" +
				journalArticle.getResourcePrimKey());
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

	private String _getTextsContent(String... texts) {
		StringBundler sb = new StringBundler();

		sb.append("<root available-locales=\"en_US\" ");
		sb.append("default-locale=\"en_US\">");

		for (String text : texts) {
			sb.append(
				_getDynamicElement(
					text, "text", DDMFormFieldTypeConstants.TEXT));
		}

		sb.append("</root>");

		return sb.toString();
	}

	private boolean _hasViewResourcePermission(
			ObjectEntry objectEntry, Role role)
		throws Exception {

		return _resourcePermissionLocalService.hasResourcePermission(
			objectEntry.getCompanyId(), objectEntry.getModelClassName(),
			ResourceConstants.SCOPE_INDIVIDUAL,
			String.valueOf(objectEntry.getObjectEntryId()), role.getRoleId(),
			ActionKeys.VIEW);
	}

	private ObjectEntry _migrateWithUnavailableDefaultLanguage(
			Map<Locale, String> contentMap, Map<Locale, String> descriptionMap,
			Map<Locale, String> titleMap)
		throws Exception {

		DDMForm ddmForm = DDMFormTestUtil.createDDMForm(
			DDMFormTestUtil.createAvailableLocales(
				LocaleUtil.SPAIN, LocaleUtil.US),
			LocaleUtil.US);

		ddmForm.addDDMFormField(
			DDMFormTestUtil.createDDMFormField(
				"content", "content", DDMFormFieldTypeConstants.TEXT, "string",
				true, false, false, LocaleUtil.SPAIN, LocaleUtil.US));

		DDMStructure ddmStructure = DDMStructureTestUtil.addStructure(
			_group.getGroupId(), JournalArticle.class.getName(), ddmForm,
			LocaleUtil.US);

		JournalArticle journalArticle = _journalArticleLocalService.addArticle(
			null, TestPropsValues.getUserId(), _group.getGroupId(),
			JournalFolderConstants.DEFAULT_PARENT_FOLDER_ID, titleMap,
			descriptionMap,
			DDMStructureTestUtil.getSampleStructuredContent(
				"content", Collections.singletonList(contentMap),
				LocaleUtil.toLanguageId(LocaleUtil.US)),
			ddmStructure.getStructureId(), null,
			ServiceContextTestUtil.getServiceContext(_group.getGroupId()));

		Set<Locale> availableLocales = LanguageUtil.getCompanyAvailableLocales(
			_group.getCompanyId());
		Locale defaultLocale = LocaleUtil.getDefault();
		Locale originalDefaultLocale = LocaleThreadLocal.getDefaultLocale();
		Locale originalSiteDefaultLocale =
			LocaleThreadLocal.getSiteDefaultLocale();

		try {
			CompanyTestUtil.resetCompanyLocales(
				_group.getCompanyId(),
				Collections.singletonList(LocaleUtil.SPAIN), LocaleUtil.SPAIN);

			LocaleThreadLocal.setDefaultLocale(LocaleUtil.SPAIN);
			LocaleThreadLocal.setSiteDefaultLocale(LocaleUtil.SPAIN);

			_assertSuccessful(_execute(_group.getGroupId(), false));
		}
		finally {
			LocaleThreadLocal.setDefaultLocale(originalDefaultLocale);
			LocaleThreadLocal.setSiteDefaultLocale(originalSiteDefaultLocale);

			CompanyTestUtil.resetCompanyLocales(
				_group.getCompanyId(), availableLocales, defaultLocale);
		}

		return _getObjectEntry(journalArticle);
	}

	private String _render(DDMTemplate ddmTemplate, ObjectEntry objectEntry)
		throws Exception {

		MockHttpServletRequest mockHttpServletRequest =
			_getMockHttpServletRequest(
				LayoutTestUtil.addTypeContentLayout(_group));

		DDMTemplate migratedDDMTemplate =
			_ddmTemplateLocalService.getDDMTemplateByExternalReferenceCode(
				"CMS_MIGRATION_DDM_TEMPLATE_" + ddmTemplate.getTemplateId(),
				_group.getGroupId());

		Template template = TemplateManagerUtil.getTemplate(
			TemplateConstants.LANG_TYPE_FTL,
			new StringTemplateResource(
				migratedDDMTemplate.getTemplateKey(),
				migratedDDMTemplate.getScript()),
			false);

		template.prepare(mockHttpServletRequest);

		ThemeDisplay themeDisplay =
			(ThemeDisplay)mockHttpServletRequest.getAttribute(
				WebKeys.THEME_DISPLAY);

		template.put(
			"objectEntryId",
			new TemplateNode(
				themeDisplay, "objectEntryId",
				String.valueOf(objectEntry.getObjectEntryId()),
				StringPool.BLANK, new HashMap<>()));

		UnsyncStringWriter unsyncStringWriter = new UnsyncStringWriter();

		template.processTemplate(unsyncStringWriter);

		return unsyncStringWriter.toString();
	}

	private String _renderAssetPublisherWidgetTemplate(
			BlogsEntry blogsEntry, Layout layout)
		throws Exception {

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
			_group.getGroupId(), _getMockHttpServletRequest(layout),
			new MockHttpServletResponse(), false);
	}

	private void _setResourcePermissions(
			String className, long classPK, Role role, String... actionIds)
		throws Exception {

		_resourcePermissionLocalService.setResourcePermissions(
			_group.getCompanyId(), className,
			ResourceConstants.SCOPE_INDIVIDUAL, String.valueOf(classPK),
			role.getRoleId(), actionIds);
	}

	private void _withNewDepotEntry(UnsafeRunnable<Exception> unsafeRunnable)
		throws Exception {

		DepotEntry depotEntry = _depotEntry;

		_depotEntry = _addDepotEntry();

		try {
			unsafeRunnable.run();
		}
		finally {
			_deleteDepotEntry(_depotEntry);

			_depotEntry = depotEntry;
		}
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

	@Inject
	private DDMStructureLocalService _ddmStructureLocalService;

	@Inject
	private DDMTemplateLocalService _ddmTemplateLocalService;

	private DepotEntry _depotEntry;

	@Inject
	private DepotEntryGroupRelLocalService _depotEntryGroupRelLocalService;

	@Inject
	private DepotEntryLocalService _depotEntryLocalService;

	@Inject
	private DLAppLocalService _dlAppLocalService;

	@Inject
	private DLFileEntryTypeLocalService _dlFileEntryTypeLocalService;

	@Inject
	private FragmentEntryLinkLocalService _fragmentEntryLinkLocalService;

	@DeleteAfterTestRun
	private Group _group;

	@Inject
	private GroupLocalService _groupLocalService;

	@Inject
	private JournalArticleLocalService _journalArticleLocalService;

	@Inject
	private LayoutLocalService _layoutLocalService;

	@Inject
	private LayoutPageTemplateEntryLocalService
		_layoutPageTemplateEntryLocalService;

	@Inject
	private ObjectDefinitionLocalService _objectDefinitionLocalService;

	@Inject
	private ObjectEntryFolderLocalService _objectEntryFolderLocalService;

	@Inject
	private ObjectEntryLocalService _objectEntryLocalService;

	@Inject
	private ObjectEntryService _objectEntryService;

	@Inject
	private ObjectFieldLocalService _objectFieldLocalService;

	@Inject
	private ObjectFolderLocalService _objectFolderLocalService;

	@Inject
	private ObjectRelationshipLocalService _objectRelationshipLocalService;

	private Set<Long> _originalObjectDefinitionIds;

	@Inject
	private Portal _portal;

	@Inject
	private PortletDisplayTemplateManager _portletDisplayTemplateManager;

	@Inject
	private PortletPreferencesLocalService _portletPreferencesLocalService;

	@Inject
	private ReadingTimeEntryLocalService _readingTimeEntryLocalService;

	@Inject
	private ResourcePermissionLocalService _resourcePermissionLocalService;

	@DeleteAfterTestRun
	private Role _role;

	@Inject
	private RoleLocalService _roleLocalService;

	@Inject
	private SegmentsExperienceLocalService _segmentsExperienceLocalService;

	@Inject
	private TemplateEntryLocalService _templateEntryLocalService;

	@Inject
	private UserLocalService _userLocalService;

}