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

package com.liferay.portal.upgrade.v6_2_0;

import com.liferay.portal.kernel.dao.jdbc.DataAccess;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.upgrade.UpgradeProcess;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.util.PortalUtil;
import com.liferay.portlet.journal.model.JournalArticle;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;

import java.util.Set;

/**
 * @author Sergio Sanchez
 * @author Zsolt Berentey
 */
public class UpgradeSocial extends UpgradeProcess {

	protected void addActivity(
			long activityId, long groupId, long companyId, long userId,
			Timestamp createDate, long mirrorActivityId, long classNameId,
			long classPK, int type, String extraData, long receiverUserId)
		throws Exception {

		Connection con = null;
		PreparedStatement ps = null;
		ResultSet rs = null;

		try {
			con = DataAccess.getUpgradeOptimizedConnection();

			StringBundler sb = new StringBundler(5);

			sb.append("insert into SocialActivity (activityId, groupId, ");
			sb.append("companyId, userId, createDate, mirrorActivityId, ");
			sb.append("classNameId, classPK, type_, extraData, ");
			sb.append("receiverUserId) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ");
			sb.append("?)");

			ps = con.prepareStatement(sb.toString());

			ps.setLong(1, activityId);
			ps.setLong(2, groupId);
			ps.setLong(3, companyId);
			ps.setLong(4, userId);
			ps.setLong(5, createDate.getTime());
			ps.setLong(6, mirrorActivityId);
			ps.setLong(7, classNameId);
			ps.setLong(8, classPK);
			ps.setInt(9, type);
			ps.setString(10, extraData);
			ps.setLong(11, receiverUserId);

			ps.executeUpdate();
		}
		catch (Exception e) {
			if (_log.isWarnEnabled()) {
				_log.warn("Unable to add activity " + activityId, e);
			}
		}
		finally {
			DataAccess.cleanUp(con, ps, rs);
		}
	}

	@Override
	protected void doUpgrade() throws Exception {
		updateDLFileVersionActivities();
		updateJournalActivities();
		updateSOSocialActivities();
		updateWikiPageActivities();
	}

	protected Timestamp getUniqueModifiedDate(
		Set<String> keys, long groupId, long userId, Timestamp modifiedDate,
		long classNameId, long resourcePrimKey, double type) {

		while (true) {
			StringBundler sb = new StringBundler(11);

			sb.append(groupId);
			sb.append(StringPool.DASH);
			sb.append(userId);
			sb.append(StringPool.DASH);
			sb.append(modifiedDate);
			sb.append(StringPool.DASH);
			sb.append(classNameId);
			sb.append(StringPool.DASH);
			sb.append(resourcePrimKey);
			sb.append(StringPool.DASH);
			sb.append(type);

			String key = sb.toString();

			modifiedDate = new Timestamp(modifiedDate.getTime() + 1);

			if (!keys.contains(key)) {
				keys.add(key);

				return modifiedDate;
			}
		}
	}

	protected void updateDLFileVersionActivities() throws Exception {
		Connection con1 = null;
		Connection con2 = null;
		Connection con3 = null;
		PreparedStatement ps1 = null;
		PreparedStatement ps2 = null;
		PreparedStatement ps3 = null;
		ResultSet rs1 = null;
		ResultSet rs2 = null;
		ResultSet rs3 = null;

		try {
			con1 = DataAccess.getUpgradeOptimizedConnection();
			con2 = DataAccess.getUpgradeOptimizedConnection();
			con3 = DataAccess.getUpgradeOptimizedConnection();

			ps1 = con1.prepareStatement(
				"select DISTINCT fileEntryId from DLFileVersion");

			rs1 = ps1.executeQuery();

			while (rs1.next()) {
				long fileEntryId = rs1.getLong("fileEntryId");

				ps2 = con2.prepareStatement(
					"select title from DLFileVersion where fileEntryId " +
						"= ? and status = ? order by fileVersionId asc");

				ps2.setLong(1, fileEntryId);
				ps2.setInt(2, WorkflowConstants.STATUS_APPROVED);

				rs2 = ps2.executeQuery();

				ps3 = con3.prepareStatement(
						"select activityId from SocialActivity where " +
							"classPK = ? order by activityId asc");

				ps3.setLong(1, fileEntryId);

				rs3 = ps3.executeQuery();

				while (rs2.next() && rs3.next()) {
					String title = rs2.getString("title");
					long activityId = rs3.getInt("activityId");

					JSONObject extraDataJSONObject =
						JSONFactoryUtil.createJSONObject();

					extraDataJSONObject.put("title", title);

					StringBundler sb = new StringBundler(6);

					sb.append("update SocialActivity set extraData = ");
					sb.append("'");
					sb.append(extraDataJSONObject.toString());
					sb.append("'");
					sb.append(" where activityId = ");
					sb.append(String.valueOf(activityId));

					runSQL(sb.toString());
				}
			}
		}
		finally {
			DataAccess.cleanUp(con1, ps1, rs1);
			DataAccess.cleanUp(con2, ps2, rs2);
			DataAccess.cleanUp(con3, ps3, rs3);
		}
	}

	protected void updateJournalActivities() throws Exception {
		long classNameId = PortalUtil.getClassNameId(JournalArticle.class);

		String[] tableNames = {"SocialActivity", "SocialActivityCounter"};

		for (String tableName : tableNames) {
			StringBundler sb = new StringBundler(7);

			sb.append("update ");
			sb.append(tableName);
			sb.append(" set classPK = (select resourcePrimKey ");
			sb.append("from JournalArticle where id_ = ");
			sb.append(tableName);
			sb.append(".classPK) where classNameId = ");
			sb.append(classNameId);

			runSQL(sb.toString());
		}
	}

	protected void updateSOSocialActivities() throws Exception {
		if (!hasTable("SO_SocialActivity")) {
			return;
		}

		Connection con = null;
		PreparedStatement ps = null;
		ResultSet rs = null;

		try {
			con = DataAccess.getUpgradeOptimizedConnection();

			ps = con.prepareStatement(
				"select activityId, activitySetId from SO_SocialActivity");

			rs = ps.executeQuery();

			while (rs.next()) {
				long activityId = rs.getLong("activityId");
				long activitySetId = rs.getLong("activitySetId");

				StringBundler sb = new StringBundler(4);

				sb.append("update SocialActivity set activitySetId = ");
				sb.append(activitySetId);
				sb.append(" where activityId = ");
				sb.append(activityId);

				runSQL(sb.toString());
			}
		}
		finally {
			DataAccess.cleanUp(con, ps, rs);
		}

		runSQL("drop table SO_SocialActivity");
	}

	protected void updateWikiPageActivities() throws Exception {
		Connection con1 = null;
		Connection con2 = null;
		Connection con3 = null;
		PreparedStatement ps1 = null;
		PreparedStatement ps2 = null;
		PreparedStatement ps3 = null;
		ResultSet rs1 = null;
		ResultSet rs2 = null;
		ResultSet rs3 = null;

		try {
			con1 = DataAccess.getUpgradeOptimizedConnection();
			con2 = DataAccess.getUpgradeOptimizedConnection();
			con3 = DataAccess.getUpgradeOptimizedConnection();

			ps1 = con1.prepareStatement(
				"select DISTINCT resourcePrimKey from WikiPage");

			rs1 = ps1.executeQuery();

			while (rs1.next()) {
				long resourcePrimKey = rs1.getLong("resourcePrimKey");

				ps2 = con2.prepareStatement(
					"select title, version from WikiPage where " +
						"resourcePrimKey= ? and status = ? " +
							"order by pageId asc");

				ps2.setLong(1, resourcePrimKey);
				ps2.setInt(2, WorkflowConstants.STATUS_APPROVED);

				rs2 = ps2.executeQuery();

				ps3 = con3.prepareStatement(
						"select activityId from SocialActivity where " +
							"classPK = ? order by activityId asc");

				ps3.setLong(1, resourcePrimKey);

				rs3 = ps3.executeQuery();

				while (rs2.next() && rs3.next()) {
					long activityId = rs3.getInt("activityId");
					String title = rs2.getString("title");
					double version = rs2.getDouble("version");

					JSONObject extraDataJSONObject =
						JSONFactoryUtil.createJSONObject();

					extraDataJSONObject.put("title", title);
					extraDataJSONObject.put("version", version);

					StringBundler sb = new StringBundler(6);

					sb.append("update SocialActivity set extraData = ");
					sb.append("'");
					sb.append(extraDataJSONObject.toString());
					sb.append("'");
					sb.append(" where activityId = ");
					sb.append(String.valueOf(activityId));

					runSQL(sb.toString());
				}
			}
		}
		finally {
			DataAccess.cleanUp(con1, ps1, rs1);
			DataAccess.cleanUp(con2, ps2, rs2);
			DataAccess.cleanUp(con3, ps3, rs3);
		}
	}

	private static Log _log = LogFactoryUtil.getLog(UpgradeSocial.class);

}