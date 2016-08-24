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
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.model.ResourceBlockPermissionsContainer;
import com.liferay.portal.kernel.model.Role;
import com.liferay.portal.kernel.model.RoleConstants;
import com.liferay.portal.kernel.service.ResourceTypePermissionLocalServiceUtil;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.rule.Sync;
import com.liferay.portal.kernel.test.rule.SynchronousDestinationTestRule;
import com.liferay.portal.kernel.test.util.RoleTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.verify.test.BaseVerifyProcessTestCase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * @author Preston Crary
 */
@Sync
public class VerifyResourceBlocksTest extends BaseVerifyProcessTestCase {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new AggregateTestRule(
			new LiferayIntegrationTestRule(),
			SynchronousDestinationTestRule.INSTANCE);

	@Before
	public void setUp() throws Exception {
		_runSQL(
			"create table VerifyResourceBlocksTest (id LONG not null " +
				"primary key, companyId LONG, groupId LONG, resourceBlockId " +
					"LONG)");

		_runSQL(
			"insert into VerifyResourceBlocksTest values (1, " +
				TestPropsValues.getCompanyId() + ", " +
					TestPropsValues.getGroupId() + ", -1)");
		_runSQL(
			"insert into VerifyResourceBlocksTest values (2, " +
				TestPropsValues.getCompanyId() + ", " +
					TestPropsValues.getGroupId() + ", -2)");
		_runSQL(
			"insert into VerifyResourceBlocksTest values (3, " +
				TestPropsValues.getCompanyId() + ", " +
					TestPropsValues.getGroupId() + ", -2)");

		_testRole = RoleTestUtil.addRole(RoleConstants.TYPE_REGULAR);

		_runSQL(
			"insert into ResourceBlock (resourceBlockId, companyId, groupId, " +
				"name, permissionsHash, referenceCount) values (-2, " +
					TestPropsValues.getCompanyId() + ", " +
						TestPropsValues.getGroupId() + ", '" + _modelName +
							"', '" + _getPermissionHash(_actionIds2) + "', 1)");

		_runSQL(
			"insert into ResourceBlockPermission (resourceBlockPermissionId, " +
				"companyId, resourceBlockId, roleId, actionIds) values (-1, " +
					TestPropsValues.getCompanyId() + ", -1, " +
						_testRole.getRoleId() + ", " + _actionIds1 + ")");
		_runSQL(
			"insert into ResourceBlockPermission (resourceBlockPermissionId, " +
				"companyId, resourceBlockId, roleId, actionIds) values (-2, " +
					TestPropsValues.getCompanyId() + ", -2, " +
						_testRole.getRoleId() + ", " + _actionIds2 + ")");
	}

	@After
	public void tearDown() throws Exception {
		_runSQL("drop table VerifyResourceBlocksTest");

		_runSQL("delete from ResourceBlock where resourceBlockId < 0");
		_runSQL(
			"delete from ResourceBlockPermission where resourceBlockId < 0");
	}

	@Test
	public void testAddMissingResourceBlocks() throws Exception {
		_assertResourceBlocks(-1, _actionIds1, 1);
	}

	@Test
	public void testCorrectResourceBlockReferenceCounts() throws Exception {
		_assertResourceBlocks(-2, _actionIds2, 2);
	}

	@Test
	public void testGetModelAndTableNames() throws Exception {
		VerifyResourceBlocks verifyResourceBlocks = new VerifyResourceBlocks();

		verifyResourceBlocks.verify();

		List<String> modelNames = verifyResourceBlocks.getModelNames();
		List<String> tableNames = verifyResourceBlocks.getTableNames();

		Assert.assertEquals(modelNames.size(), tableNames.size());

		Assert.assertEquals(_expectedModelNames.size(), modelNames.size());
		Assert.assertTrue(_expectedModelNames.containsAll(modelNames));

		Assert.assertEquals(_expectedTableNames.size(), tableNames.size());
		Assert.assertTrue(_expectedTableNames.containsAll(tableNames));
	}

	@Override
	protected VerifyProcess getVerifyProcess() {
		return _verifyProcess;
	}

	private void _assertResourceBlocks(
			long resourceBlockId, long actionIds, long expectedReferenceCount)
		throws Exception {

		doVerify();

		String permissionHash = _getPermissionHash(actionIds);

		try (Connection con = DataAccess.getUpgradeOptimizedConnection();
			PreparedStatement ps = con.prepareStatement(
				"select * from ResourceBlock where resourceBlockId = " +
					resourceBlockId);
			ResultSet rs = ps.executeQuery()) {

			Assert.assertTrue(rs.next());
			Assert.assertEquals(
				TestPropsValues.getCompanyId(), rs.getLong("companyId"));
			Assert.assertEquals(
				TestPropsValues.getGroupId(), rs.getLong("groupId"));
			Assert.assertEquals(
				permissionHash, rs.getString("permissionsHash"));
			Assert.assertEquals(
				expectedReferenceCount, rs.getLong("referenceCount"));
		}
	}

	private String _getPermissionHash(long actionIds) throws PortalException {
		ResourceBlockPermissionsContainer resourceBlockPermissionsContainer =
			ResourceTypePermissionLocalServiceUtil.
				getResourceBlockPermissionsContainer(
					TestPropsValues.getCompanyId(),
					TestPropsValues.getGroupId(), _modelName);

		resourceBlockPermissionsContainer.setPermissions(
			_testRole.getRoleId(), actionIds);

		return resourceBlockPermissionsContainer.getPermissionsHash();
	}

	private void _runSQL(String sql) throws Exception {
		_verifyProcess.runSQL(sql);
	}

	private static final long _actionIds1 = 2L;
	private static final long _actionIds2 = 6L;
	private static final List<String> _expectedModelNames = Arrays.asList(
		"com.liferay.calendar.model.Calendar",
		"com.liferay.calendar.model.CalendarBooking",
		"com.liferay.calendar.model.CalendarResource",
		"com.liferay.bookmarks.model.BookmarksEntry",
		"com.liferay.bookmarks.model.BookmarksFolder");
	private static final List<String> _expectedTableNames = Arrays.asList(
		"Calendar", "CalendarBooking", "CalendarResource", "BookmarksEntry",
		"BookmarksFolder");
	private static final String _modelName =
		VerifyResourceBlocksTest.class.getName();
	private static final VerifyProcess _verifyProcess =
		new TestVerifyResourceBlock();

	@DeleteAfterTestRun
	private Role _testRole;

	private static class TestVerifyResourceBlock
		extends BaseVerifyResourceBlock {

		protected List<String> getModelNames() {
			return Collections.singletonList(_modelName);
		}

		protected List<String> getTableNames() {
			return Collections.singletonList("VerifyResourceBlocksTest");
		}

	}

}