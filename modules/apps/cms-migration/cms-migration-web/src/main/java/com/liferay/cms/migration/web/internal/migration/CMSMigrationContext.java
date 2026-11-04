/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.migration;

import com.liferay.cms.migration.web.internal.reference.ReferenceScope;
import com.liferay.depot.model.DepotEntry;
import com.liferay.dynamic.data.mapping.model.DDMStructure;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.petra.function.UnsafeFunction;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.service.ServiceContext;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Holds the state of one migration run: the source site, the target space,
 * and every mapping from classic content to the object entries that replace
 * it. Reference rewriters read the mappings to repoint pages.
 *
 * @author Lino Alves
 */
public class CMSMigrationContext {

	public CMSMigrationContext(
		DepotEntry depotEntry, Group depotGroup, boolean migrateBlogsEntries,
		boolean migrateFileEntries, boolean migrateJournalArticles,
		CMSMigrationReport cmsMigrationReport, boolean rewritePages,
		ServiceContext serviceContext, Group sourceGroup,
		Consumer<String> statusConsumer, long userId) {

		_depotEntry = depotEntry;
		_depotGroup = depotGroup;
		_migrateBlogsEntries = migrateBlogsEntries;
		_migrateFileEntries = migrateFileEntries;
		_migrateJournalArticles = migrateJournalArticles;
		_cmsMigrationReport = cmsMigrationReport;
		_rewritePages = rewritePages;
		_serviceContext = serviceContext;
		_sourceGroup = sourceGroup;
		_statusConsumer = statusConsumer;
		_userId = userId;

		_rewriteGroup = sourceGroup;
	}

	public void addAssetListEntryReferenceScope(
		long assetListEntryId, ReferenceScope referenceScope) {

		_assetListEntryReferenceScopes.put(assetListEntryId, referenceScope);
	}

	public void addDDMStructure(DDMStructure ddmStructure) {
		_ddmStructures.putIfAbsent(ddmStructure.getStructureId(), ddmStructure);
	}

	public void addFolderId(
		String className, long folderId, long objectEntryFolderId) {

		_objectEntryFolderIds.put(
			_getKey(className, folderId), objectEntryFolderId);
	}

	public void addInformationTemplateEntryId(
		long templateEntryId, long newTemplateEntryId) {

		_informationTemplateEntryIds.put(templateEntryId, newTemplateEntryId);
	}

	public void addLayoutPageTemplateEntryId(
		long layoutPageTemplateEntryId, long newLayoutPageTemplateEntryId) {

		_layoutPageTemplateEntryIds.put(
			layoutPageTemplateEntryId, newLayoutPageTemplateEntryId);
	}

	public void addObjectDefinition(
		String className, long classTypeId, ObjectDefinition objectDefinition) {

		_objectDefinitions.put(
			_getKey(className, classTypeId), objectDefinition);
	}

	public void addObjectEntry(
		String className, long classPK, ObjectEntry objectEntry) {

		_objectEntries.put(_getKey(className, classPK), objectEntry);
	}

	public void addTemplateEntryId(
		long ddmStructureId, String ddmTemplateKey, long templateEntryId) {

		_templateEntryIds.put(
			_getKey(ddmTemplateKey, ddmStructureId), templateEntryId);
	}

	public ReferenceScope getAssetListEntryReferenceScope(
		long assetListEntryId) {

		return _assetListEntryReferenceScopes.get(assetListEntryId);
	}

	public CMSMigrationReport getCMSMigrationReport() {
		return _cmsMigrationReport;
	}

	public long getCompanyId() {
		return _sourceGroup.getCompanyId();
	}

	public Collection<DDMStructure> getDDMStructures() {
		return _ddmStructures.values();
	}

	public DepotEntry getDepotEntry() {
		return _depotEntry;
	}

	public Group getDepotGroup() {
		return _depotGroup;
	}

	public long getDepotGroupId() {
		return _depotGroup.getGroupId();
	}

	public long getInformationTemplateEntryId(long templateEntryId) {
		return _informationTemplateEntryIds.getOrDefault(templateEntryId, 0L);
	}

	public long getLayoutPageTemplateEntryId(long layoutPageTemplateEntryId) {
		return _layoutPageTemplateEntryIds.getOrDefault(
			layoutPageTemplateEntryId, 0L);
	}

	public ObjectDefinition getObjectDefinition(
		String className, long classTypeId) {

		return _objectDefinitions.get(_getKey(className, classTypeId));
	}

	public Map<String, ObjectDefinition> getObjectDefinitions() {
		return _objectDefinitions;
	}

	public Collection<ObjectEntry> getObjectEntries() {
		return _objectEntries.values();
	}

	/**
	 * Returns the migrated object entries keyed by the class name and the
	 * primary key of their source, joined with a pound sign.
	 */
	public Map<String, ObjectEntry> getObjectEntriesMap() {
		return _objectEntries;
	}

	public ObjectEntry getObjectEntry(String className, long classPK) {
		return _objectEntries.get(_getKey(className, classPK));
	}

	public long getObjectEntryFolderId(String className, long folderId) {
		return _objectEntryFolderIds.getOrDefault(
			_getKey(className, folderId), 0L);
	}

	public ObjectEntry getOrMigrateFileEntry(FileEntry fileEntry)
		throws PortalException {

		return _fileEntryResolverUnsafeFunction.apply(fileEntry);
	}

	/**
	 * Returns the ID of the group whose references are being rewritten. It is
	 * the source group, unless the source group is the Global site, whose
	 * content other groups reference too.
	 */
	public long getRewriteGroupId() {
		return _rewriteGroup.getGroupId();
	}

	public ServiceContext getServiceContext() {
		return _serviceContext;
	}

	public Group getSourceGroup() {
		return _sourceGroup;
	}

	public long getSourceGroupId() {
		return _sourceGroup.getGroupId();
	}

	public long getTemplateEntryId(long ddmStructureId, String ddmTemplateKey) {
		return _templateEntryIds.getOrDefault(
			_getKey(ddmTemplateKey, ddmStructureId), 0L);
	}

	public long getUserId() {
		return _userId;
	}

	public boolean isMigrateBlogsEntries() {
		return _migrateBlogsEntries;
	}

	public boolean isMigrateFileEntries() {
		return _migrateFileEntries;
	}

	public boolean isMigrateJournalArticles() {
		return _migrateJournalArticles;
	}

	public boolean isRewritePages() {
		return _rewritePages;
	}

	public boolean isRewriteSourceGroup() {
		if (_rewriteGroup.getGroupId() == _sourceGroup.getGroupId()) {
			return true;
		}

		return false;
	}

	public void sendStatus(String itemTitle) {
		_statusConsumer.accept(itemTitle);
	}

	public void setFileEntryResolver(
		UnsafeFunction<FileEntry, ObjectEntry, PortalException>
			fileEntryResolverUnsafeFunction) {

		_fileEntryResolverUnsafeFunction = fileEntryResolverUnsafeFunction;
	}

	public void setRewriteGroup(Group rewriteGroup) {
		_rewriteGroup = rewriteGroup;
	}

	private String _getKey(String prefix, long id) {
		return StringBundler.concat(prefix, StringPool.POUND, id);
	}

	private final Map<Long, ReferenceScope> _assetListEntryReferenceScopes =
		new HashMap<>();
	private final CMSMigrationReport _cmsMigrationReport;
	private final Map<Long, DDMStructure> _ddmStructures =
		new LinkedHashMap<>();
	private final DepotEntry _depotEntry;
	private final Group _depotGroup;
	private UnsafeFunction<FileEntry, ObjectEntry, PortalException>
		_fileEntryResolverUnsafeFunction;
	private final Map<Long, Long> _informationTemplateEntryIds =
		new HashMap<>();
	private final Map<Long, Long> _layoutPageTemplateEntryIds = new HashMap<>();
	private final boolean _migrateBlogsEntries;
	private final boolean _migrateFileEntries;
	private final boolean _migrateJournalArticles;
	private final Map<String, ObjectDefinition> _objectDefinitions =
		new HashMap<>();
	private final Map<String, ObjectEntry> _objectEntries = new HashMap<>();
	private final Map<String, Long> _objectEntryFolderIds = new HashMap<>();
	private Group _rewriteGroup;
	private final boolean _rewritePages;
	private final ServiceContext _serviceContext;
	private final Group _sourceGroup;
	private final Consumer<String> _statusConsumer;
	private final Map<String, Long> _templateEntryIds = new HashMap<>();
	private final long _userId;

}