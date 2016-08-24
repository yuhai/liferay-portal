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

package com.liferay.portal.verify;

import com.liferay.portal.kernel.dao.jdbc.AutoBatchPreparedStatementUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.ResourceBlockPermissionsContainer;
import com.liferay.portal.kernel.service.ResourceTypePermissionLocalServiceUtil;
import com.liferay.portal.kernel.util.LoggingTimer;
import com.liferay.portal.kernel.util.StringBundler;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.List;

/**
 * @author Preston Crary
 */
public abstract class BaseVerifyResourceBlock extends VerifyProcess {

	@Override
	protected void doVerify() {
		List<String> modelNames = getModelNames();
		List<String> tableNames = getTableNames();

		for (int i = 0; i < tableNames.size(); i++) {
			String modelName = modelNames.get(i);
			String tableName = tableNames.get(i);

			try (LoggingTimer loggingTimer = new LoggingTimer(tableName)) {
				_addMissingResourceBlocks(tableName, modelName);

				_correctResourceBlockReferenceCounts(tableName);
			}
			catch (Exception e) {
				_log.error(
					"Unable to verify ResourceBlocks for " + modelName, e);
			}
		}
	}

	protected abstract List<String> getModelNames();

	protected abstract List<String> getTableNames();

	private void _addMissingResourceBlocks(String tableName, String modelName)
		throws SQLException {

		StringBundler sb = new StringBundler(13);

		sb.append("select t.companyId, t.groupId, t.resourceBlockId, ");
		sb.append("t.referenceCount, ResourceBlockPermission.roleId, ");
		sb.append("ResourceBlockPermission.actionIds from (select companyId, ");
		sb.append("groupId, resourceBlockId, count(resourceBlockId) as ");
		sb.append("referenceCount from ");
		sb.append(tableName);
		sb.append(" group by companyId, groupId, resourceBlockId) t left ");
		sb.append("join ResourceBlock on (ResourceBlock.resourceBlockId = ");
		sb.append("t.resourceBlockId) and (ResourceBlock.referenceCount != ");
		sb.append("t.referenceCount) inner join ResourceBlockPermission on (");
		sb.append("ResourceBlockPermission.resourceBlockId = ");
		sb.append("t.resourceBlockId) where ResourceBlock.resourceBlockId is ");
		sb.append("null");

		try (PreparedStatement ps = connection.prepareStatement(sb.toString());
			ResultSet rs = ps.executeQuery();
			PreparedStatement insertPS =
				AutoBatchPreparedStatementUtil.concurrentAutoBatch(
					connection,
					"insert into ResourceBlock (resourceBlockId, companyId, " +
						"groupId, name, permissionsHash, referenceCount) " +
							"values (?, ?, ?, ?, ?, ?)")) {

			while (rs.next()) {
				long companyId = rs.getLong(1);
				long groupId = rs.getLong(2);
				long resourceBlockId = rs.getLong(3);
				long referenceCount = rs.getLong(4);
				long roleId = rs.getLong(5);
				long actionIds = rs.getLong(6);

				ResourceBlockPermissionsContainer
					resourceBlockPermissionsContainer =
						ResourceTypePermissionLocalServiceUtil.
							getResourceBlockPermissionsContainer(
								companyId, groupId, modelName);

				resourceBlockPermissionsContainer.setPermissions(
					roleId, actionIds);

				String permissionsHash =
					resourceBlockPermissionsContainer.getPermissionsHash();

				insertPS.setLong(1, resourceBlockId);
				insertPS.setLong(2, companyId);
				insertPS.setLong(3, groupId);
				insertPS.setString(4, modelName);
				insertPS.setString(5, permissionsHash);
				insertPS.setLong(6, referenceCount);

				insertPS.executeUpdate();
			}
		}
	}

	private void _correctResourceBlockReferenceCounts(String tableName)
		throws SQLException {

		StringBundler sb = new StringBundler(8);

		sb.append("select t.resourceBlockId, t.referenceCount from (select ");
		sb.append("companyId, groupId, resourceBlockId, ");
		sb.append("count(resourceBlockId) as referenceCount from ");
		sb.append(tableName);
		sb.append(" group by companyId, groupId, resourceBlockId) t inner ");
		sb.append("join ResourceBlock on (ResourceBlock.resourceBlockId = ");
		sb.append("t.resourceBlockId) and (ResourceBlock.referenceCount ");
		sb.append("!= t.referenceCount)");

		try (PreparedStatement ps = connection.prepareStatement(sb.toString());
			ResultSet rs = ps.executeQuery();
			PreparedStatement updatePS =
				AutoBatchPreparedStatementUtil.concurrentAutoBatch(
					connection,
					"update ResourceBlock set referenceCount = ? where " +
						"resourceBlockId = ?")) {

			while (rs.next()) {
				long resourceBlockId = rs.getLong(1);
				long referenceCount = rs.getLong(2);

				updatePS.setLong(1, referenceCount);
				updatePS.setLong(2, resourceBlockId);

				updatePS.executeUpdate();
			}
		}
	}

	private static final Log _log = LogFactoryUtil.getLog(
		BaseVerifyResourceBlock.class);

}