/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.bookmarks.verify;

import com.liferay.bookmarks.model.BookmarksEntry;
import com.liferay.bookmarks.model.BookmarksFolder;
import com.liferay.bookmarks.service.BookmarksEntryLocalService;
import com.liferay.bookmarks.service.BookmarksFolderLocalService;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.ResourceBlock;
import com.liferay.portal.kernel.service.ResourceBlockLocalService;
import com.liferay.portal.kernel.util.LoggingTimer;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.util.PortalInstances;
import com.liferay.portal.verify.VerifyProcess;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Raymond Augé
 * @author Alexander Chow
 */
@Component(
	immediate = true,
	property = {"verify.process.name=com.liferay.bookmarks.service"},
	service = VerifyProcess.class
)
public class BookmarksServiceVerifyProcess extends VerifyProcess {

	@Override
	protected void doVerify() throws Exception {
		updateEntryAssets();
		updateFolderAssets();
		verifyResourceBlock(
			BookmarksEntry.class.getName(), "entryId", "BookmarksEntry");
		verifyResourceBlock(
			BookmarksFolder.class.getName(), "folderId", "BookmarksFolder");
		verifyTree();
	}

	protected Map<Long, String[]> getRoleIdsToActionIds(
			String modelName, long resourceBlockId)
		throws Exception {

		try (PreparedStatement ps1 = connection.prepareStatement(
				"select roleId, actionIds from ResourceBlockPermission where " +
					"resourceBlockId = " + resourceBlockId);
			ResultSet rs1 = ps1.executeQuery()) {

			Map<Long, String[]> roleIdsToActionIds = new HashMap<>();

			for (int i = 0; rs1.next(); i++) {
				long roleId = rs1.getLong("roleId");
				long actionIds = rs1.getLong("actionIds");

				List<String> definedActionIds =
					_resourceBlockLocalService.getActionIds(
						modelName, actionIds);

				roleIdsToActionIds.put(
					roleId,
					definedActionIds.toArray(
						new String[definedActionIds.size()]));
			}

			return roleIdsToActionIds;
		}
	}

	@Reference(unbind = "-")
	protected void setBookmarksEntryLocalService(
		BookmarksEntryLocalService bookmarksEntryLocalService) {

		_bookmarksEntryLocalService = bookmarksEntryLocalService;
	}

	@Reference(unbind = "-")
	protected void setBookmarksFolderLocalService(
		BookmarksFolderLocalService bookmarksFolderLocalService) {

		_bookmarksFolderLocalService = bookmarksFolderLocalService;
	}

	@Reference(unbind = "-")
	protected void setResourceBlockLocalService(
		ResourceBlockLocalService resourceBlockLocalService) {

		_resourceBlockLocalService = resourceBlockLocalService;
	}

	protected void updateEntryAssets() throws Exception {
		try (LoggingTimer loggingTimer = new LoggingTimer()) {
			List<BookmarksEntry> entries =
				_bookmarksEntryLocalService.getNoAssetEntries();

			if (_log.isDebugEnabled()) {
				_log.debug(
					"Processing " + entries.size() + " entries with no asset");
			}

			for (BookmarksEntry entry : entries) {
				try {
					_bookmarksEntryLocalService.updateAsset(
						entry.getUserId(), entry, null, null, null, null);
				}
				catch (Exception e) {
					if (_log.isWarnEnabled()) {
						_log.warn(
							"Unable to update asset for entry " +
								entry.getEntryId() + ": " + e.getMessage());
					}
				}
			}

			if (_log.isDebugEnabled()) {
				_log.debug("Assets verified for entries");
			}
		}
	}

	protected void updateFolderAssets() throws Exception {
		try (LoggingTimer loggingTimer = new LoggingTimer()) {
			List<BookmarksFolder> folders =
				_bookmarksFolderLocalService.getNoAssetFolders();

			if (_log.isDebugEnabled()) {
				_log.debug(
					"Processing " + folders.size() + " folders with no asset");
			}

			for (BookmarksFolder folder : folders) {
				try {
					_bookmarksFolderLocalService.updateAsset(
						folder.getUserId(), folder, null, null, null, null);
				}
				catch (Exception e) {
					if (_log.isWarnEnabled()) {
						_log.warn(
							"Unable to update asset for folder " +
								folder.getFolderId() + ": " + e.getMessage());
					}
				}
			}

			if (_log.isDebugEnabled()) {
				_log.debug("Assets verified for folders");
			}
		}
	}

	protected void updateResourceBlock(
			String modelName, long referenceCount, long resourceBlockId,
			String resourcePrimKey, Map<Long, String[]> roleIdsToActionIds,
			String tableName)
		throws Exception {

		try (PreparedStatement ps1 = connection.prepareStatement(
				"select companyId, groupId, " + resourcePrimKey + " from " +
					tableName + " where " + "resourceBlockId = " +
						resourceBlockId + " order by modifiedDate desc");
			ResultSet rs1 = ps1.executeQuery()) {

			for (int i = 0; rs1.next(); i++) {
				long companyId = rs1.getLong("companyId");
				long groupId = rs1.getLong("groupId");
				long tableResourcePrimKey = rs1.getLong(resourcePrimKey);

				_resourceBlockLocalService.setIndividualScopePermissions(
					companyId, groupId, modelName, tableResourcePrimKey,
					roleIdsToActionIds);

				long newResourceBlockId;

				if (modelName.equals(BookmarksEntry.class.getName())) {
					BookmarksEntry bookmarksEntry =
						_bookmarksEntryLocalService.fetchBookmarksEntry(
							tableResourcePrimKey);

					newResourceBlockId = bookmarksEntry.getResourceBlockId();
				}
				else {
					BookmarksFolder bookmarksFolder =
						_bookmarksFolderLocalService.fetchBookmarksFolder(
							tableResourcePrimKey);

					newResourceBlockId = bookmarksFolder.getResourceBlockId();
				}

				PreparedStatement ps2 = connection.prepareStatement(
					"update " + tableName + " set resourceBlockId = ?" +
						" where resourceBlockId = ?");

				ps2.setLong(1, newResourceBlockId);
				ps2.setLong(2, resourceBlockId);

				ps2.executeUpdate();

				ResourceBlock resourceBlock =
					_resourceBlockLocalService.fetchResourceBlock(
						newResourceBlockId);

				resourceBlock.setReferenceCount(referenceCount);

				_resourceBlockLocalService.updateResourceBlock(resourceBlock);

				break;
			}
		}
	}

	protected void verifyResourceBlock(
			String modelName, String resourcePrimKey, String tableName)
		throws Exception {

		try (LoggingTimer loggingTimer = new LoggingTimer()) {
			StringBundler sb = new StringBundler(8);

			sb.append("select t.* from (select resourceBlockId, ");
			sb.append("count(resourceBlockId) as total from ");
			sb.append(tableName);
			sb.append(" group by resourceBlockId) t left join ");
			sb.append("ResourceBlock on ");
			sb.append("(ResourceBlock.resourceBlockId = t.resourceBlockId) ");
			sb.append("and (ResourceBlock.referenceCount = t.total) ");
			sb.append("where ResourceBlock.resourceBlockId is null");

			try (PreparedStatement ps = connection.prepareStatement(
					sb.toString());
				ResultSet rs = ps.executeQuery()) {

				while (rs.next()) {
					long resourceBlockId = rs.getLong("resourceBlockId");
					long referenceCount = rs.getLong("total");

					ResourceBlock resourceBlock =
						_resourceBlockLocalService.fetchResourceBlock(
							resourceBlockId);

					if (resourceBlock == null) {
						Map<Long, String[]> roleIdsToActionIds =
							getRoleIdsToActionIds(modelName, resourceBlockId);

						updateResourceBlock(
							modelName, referenceCount, resourceBlockId,
							resourcePrimKey, roleIdsToActionIds, tableName);
					}
					else {
						resourceBlock.setReferenceCount(referenceCount);

						_resourceBlockLocalService.updateResourceBlock(
							resourceBlock);
					}
				}
			}
		}
	}

	protected void verifyTree() throws Exception {
		try (LoggingTimer loggingTimer = new LoggingTimer()) {
			long[] companyIds = PortalInstances.getCompanyIdsBySQL();

			for (long companyId : companyIds) {
				_bookmarksFolderLocalService.rebuildTree(companyId);
			}
		}
	}

	private static final Log _log = LogFactoryUtil.getLog(
		BookmarksServiceVerifyProcess.class);

	private BookmarksEntryLocalService _bookmarksEntryLocalService;
	private BookmarksFolderLocalService _bookmarksFolderLocalService;
	private ResourceBlockLocalService _resourceBlockLocalService;

}