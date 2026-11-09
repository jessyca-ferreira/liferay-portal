/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.model.listener.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.blogs.model.BlogsEntry;
import com.liferay.blogs.service.BlogsEntryLocalService;
import com.liferay.depot.constants.DepotConstants;
import com.liferay.depot.model.DepotEntry;
import com.liferay.depot.model.DepotEntryGroupRel;
import com.liferay.depot.service.DepotEntryGroupRelLocalService;
import com.liferay.depot.service.DepotEntryLocalService;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.backgroundtask.BackgroundTask;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskExecutor;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskResult;
import com.liferay.portal.kernel.backgroundtask.constants.BackgroundTaskConstants;
import com.liferay.portal.kernel.dao.orm.DynamicQuery;
import com.liferay.portal.kernel.dao.orm.ProjectionFactoryUtil;
import com.liferay.portal.kernel.dao.orm.RestrictionsFactoryUtil;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.GroupTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.ProxyUtil;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.test.rule.PermissionCheckerMethodTestRule;
import com.liferay.reading.time.model.ReadingTimeEntry;
import com.liferay.reading.time.service.ReadingTimeEntryLocalService;

import java.io.Serializable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Micaelle Silva
 */
@RunWith(Arquillian.class)
public class ObjectEntryReadingTimeModelListenerTest {

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

		_originalObjectDefinitionIds = _getObjectDefinitionIds();
	}

	@After
	public void tearDown() throws Exception {
		for (DepotEntryGroupRel depotEntryGroupRel :
				_depotEntryGroupRelLocalService.getDepotEntryGroupRels(
					_depotEntry)) {

			_depotEntryGroupRelLocalService.deleteDepotEntryGroupRel(
				depotEntryGroupRel);
		}

		_depotEntryLocalService.deleteDepotEntry(_depotEntry);

		for (long objectDefinitionId : _getObjectDefinitionIds()) {
			if (!_originalObjectDefinitionIds.contains(objectDefinitionId)) {
				_objectDefinitionLocalService.deleteObjectDefinition(
					objectDefinitionId);
			}
		}
	}

	@Test
	public void testOnAfterRemove() throws Exception {
		ObjectEntry objectEntry = _migrateBlogsEntry();

		Assert.assertNotNull(
			_readingTimeEntryLocalService.fetchReadingTimeEntry(objectEntry));

		_objectEntryLocalService.deleteObjectEntry(objectEntry);

		Assert.assertNull(
			_readingTimeEntryLocalService.fetchReadingTimeEntry(objectEntry));
	}

	@Test
	public void testOnAfterUpdate() throws Exception {
		ObjectEntry objectEntry = _migrateBlogsEntry();

		Map<String, Serializable> values = objectEntry.getValues();

		values.put(
			"content_i18n",
			HashMapBuilder.put(
				LocaleUtil.toLanguageId(LocaleUtil.getSiteDefault()),
				_getContent(1060)
			).build());

		_objectEntryLocalService.updateObjectEntry(
			TestPropsValues.getUserId(), objectEntry.getObjectEntryId(),
			objectEntry.getObjectEntryFolderId(), values,
			ServiceContextTestUtil.getServiceContext(_depotEntry.getGroupId()));

		ReadingTimeEntry readingTimeEntry =
			_readingTimeEntryLocalService.fetchReadingTimeEntry(objectEntry);

		Assert.assertEquals(240000, readingTimeEntry.getReadingTime());
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

		return new HashSet<>(
			_objectDefinitionLocalService.dynamicQuery(dynamicQuery));
	}

	private ObjectEntry _migrateBlogsEntry() throws Exception {
		BlogsEntry blogsEntry = _blogsEntryLocalService.addEntry(
			TestPropsValues.getUserId(), RandomTestUtil.randomString(),
			_getContent(530),
			ServiceContextTestUtil.getServiceContext(_group.getGroupId()));

		Map<String, Serializable> taskContextMap =
			HashMapBuilder.<String, Serializable>put(
				"depotEntryId", _depotEntry.getDepotEntryId()
			).put(
				"migrateBlogsEntries", true
			).put(
				"sourceGroupId", _group.getGroupId()
			).build();

		long userId = TestPropsValues.getUserId();

		BackgroundTaskResult backgroundTaskResult =
			_backgroundTaskExecutor.execute(
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
					}));

		Assert.assertEquals(
			backgroundTaskResult.getStatusMessage(),
			BackgroundTaskConstants.STATUS_SUCCESSFUL,
			backgroundTaskResult.getStatus());

		ObjectDefinition objectDefinition =
			_objectDefinitionLocalService.
				getObjectDefinitionByExternalReferenceCode(
					"CMS_MIGRATION_BLOGS_ENTRY", _group.getCompanyId());

		return _objectEntryLocalService.getObjectEntry(
			"CMS_MIGRATION_BLOGS_ENTRY_" + blogsEntry.getEntryId(),
			_depotEntry.getGroupId(), objectDefinition.getObjectDefinitionId());
	}

	@Inject(
		filter = "background.task.executor.class.name=com.liferay.cms.migration.web.internal.background.task.CMSMigrationBackgroundTaskExecutor"
	)
	private BackgroundTaskExecutor _backgroundTaskExecutor;

	@Inject
	private BlogsEntryLocalService _blogsEntryLocalService;

	private DepotEntry _depotEntry;

	@Inject
	private DepotEntryGroupRelLocalService _depotEntryGroupRelLocalService;

	@Inject
	private DepotEntryLocalService _depotEntryLocalService;

	@DeleteAfterTestRun
	private Group _group;

	@Inject
	private ObjectDefinitionLocalService _objectDefinitionLocalService;

	@Inject
	private ObjectEntryLocalService _objectEntryLocalService;

	private Set<Long> _originalObjectDefinitionIds;

	@Inject
	private ReadingTimeEntryLocalService _readingTimeEntryLocalService;

}