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

import com.liferay.portal.kernel.dao.jdbc.DataAccess;
import com.liferay.portal.kernel.model.ResourceBlock;
import com.liferay.portal.kernel.service.ResourceBlockLocalServiceUtil;
import com.liferay.portal.kernel.util.LoggingTimer;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.verify.model.VerifiableResourcedModel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Hai Yu
 */
public class VerifyResourceBlock extends VerifyProcess {

	public void verify(VerifiableResourcedModel verifiableResourcedModel)
		throws Exception {

		verifyResourceBlock(
			verifiableResourcedModel.getModelName(),
			verifiableResourcedModel.getPrimaryKeyColumnName(),
			verifiableResourcedModel.getTableName());
	}

	protected void doVerify() throws Exception {
	}

	protected long[] getNewResourceBlockIdAndReferenceCount(
			String primKey, long tablePrimKey, String tableName)
		throws Exception {

		StringBundler sb = new StringBundler(8);

		sb.append("select resourceBlockId, referenceCount from ResourceBlock ");
		sb.append("where resourceBlockId in (select resourceBlockId from ");
		sb.append(tableName);
		sb.append(" where ");
		sb.append(primKey);
		sb.append(" = ");
		sb.append(tablePrimKey);
		sb.append(")");

		try (Connection con = DataAccess.getUpgradeOptimizedConnection();
			PreparedStatement ps = con.prepareStatement(sb.toString());
			ResultSet rs = ps.executeQuery()) {

			long[] newResourceBlockIdAndReferenceCount = new long[2];

			while (rs.next()) {
				newResourceBlockIdAndReferenceCount[0] = rs.getLong(
					"resourceBlockId");
				newResourceBlockIdAndReferenceCount[1] = rs.getLong(
					"referenceCount");
			}

			return newResourceBlockIdAndReferenceCount;
		}
	}

	protected Map<Long, String[]> getRoleIdsToActionIds(
			String modelName, long resourceBlockId)
		throws Exception {

		try (Connection con = DataAccess.getUpgradeOptimizedConnection();
			PreparedStatement ps = con.prepareStatement(
				"select roleId, actionIds from ResourceBlockPermission where " +
					"resourceBlockId = " + resourceBlockId);
			ResultSet rs = ps.executeQuery()) {

			Map<Long, String[]> roleIdsToActionIds = new HashMap<>();

			for (int i = 0; rs.next(); i++) {
				long roleId = rs.getLong("roleId");
				long actionIds = rs.getLong("actionIds");

				List<String> definedActionIds =
					ResourceBlockLocalServiceUtil.getActionIds(
						modelName, actionIds);

				roleIdsToActionIds.put(
					roleId,
					definedActionIds.toArray(
						new String[definedActionIds.size()]));
			}

			return roleIdsToActionIds;
		}
	}

	protected void updateResourceBlock(
			String modelName, long referenceCount, long resourceBlockId,
			String primKey, Map<Long, String[]> roleIdsToActionIds,
			String tableName)
		throws Exception {

		try (Connection con = DataAccess.getUpgradeOptimizedConnection();
			PreparedStatement ps1 = con.prepareStatement(
				"select companyId, groupId, " + primKey + " from " + tableName +
					" where " + "resourceBlockId = " + resourceBlockId +
						" order by modifiedDate desc");
			ResultSet rs1 = ps1.executeQuery()) {

			for (int i = 0; rs1.next(); i++) {
				long companyId = rs1.getLong("companyId");
				long groupId = rs1.getLong("groupId");
				long tablePrimKey = rs1.getLong(primKey);

				ResourceBlockLocalServiceUtil.setIndividualScopePermissions(
					companyId, groupId, modelName, tablePrimKey,
					roleIdsToActionIds);

				long[] newResourceBlockIdAndReferenceCount =
					getNewResourceBlockIdAndReferenceCount(
						primKey, tablePrimKey, tableName);

				PreparedStatement ps2 = con.prepareStatement(
					"update " + tableName + " set resourceBlockId = ?" +
						" where resourceBlockId = ?");

				ps2.setLong(1, newResourceBlockIdAndReferenceCount[0]);
				ps2.setLong(2, resourceBlockId);

				ps2.executeUpdate();

				ResourceBlock resourceBlock =
					ResourceBlockLocalServiceUtil.fetchResourceBlock(
						newResourceBlockIdAndReferenceCount[0]);

				referenceCount =
					newResourceBlockIdAndReferenceCount[1] + referenceCount - 1;

				resourceBlock.setReferenceCount(referenceCount);

				ResourceBlockLocalServiceUtil.updateResourceBlock(
					resourceBlock);

				break;
			}
		}
	}

	protected void verifyResourceBlock(
			String modelName, String primKey, String tableName)
		throws Exception {

		try (LoggingTimer loggingTimer = new LoggingTimer(tableName)) {
			StringBundler sb = new StringBundler(8);

			sb.append("select t.* from (select resourceBlockId, ");
			sb.append("count(resourceBlockId) as total from ");
			sb.append(tableName);
			sb.append(" group by resourceBlockId) t left join ");
			sb.append("ResourceBlock on ");
			sb.append("(ResourceBlock.resourceBlockId = t.resourceBlockId) ");
			sb.append("and (ResourceBlock.referenceCount = t.total) ");
			sb.append("where ResourceBlock.resourceBlockId is null");

			try (Connection con = DataAccess.getUpgradeOptimizedConnection();
				PreparedStatement ps = con.prepareStatement(sb.toString());
				ResultSet rs = ps.executeQuery()) {

				while (rs.next()) {
					long resourceBlockId = rs.getLong("resourceBlockId");
					long referenceCount = rs.getLong("total");

					ResourceBlock resourceBlock =
						ResourceBlockLocalServiceUtil.fetchResourceBlock(
							resourceBlockId);

					if (resourceBlock == null) {
						Map<Long, String[]> roleIdsToActionIds =
							getRoleIdsToActionIds(modelName, resourceBlockId);

						updateResourceBlock(
							modelName, referenceCount, resourceBlockId, primKey,
							roleIdsToActionIds, tableName);
					}
					else {
						resourceBlock.setReferenceCount(referenceCount);

						ResourceBlockLocalServiceUtil.updateResourceBlock(
							resourceBlock);
					}
				}
			}
		}
	}

}