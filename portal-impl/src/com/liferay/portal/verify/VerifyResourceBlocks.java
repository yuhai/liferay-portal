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

import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.ClassName;
import com.liferay.portal.kernel.model.ResourceBlock;
import com.liferay.portal.kernel.model.ResourceBlockPermission;
import com.liferay.portal.kernel.service.ClassNameLocalServiceUtil;
import com.liferay.portal.kernel.service.ResourceBlockLocalServiceUtil;
import com.liferay.portal.kernel.util.CharPool;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Preston Crary
 */
public class VerifyResourceBlocks extends BaseVerifyResourceBlock {

	@Override
	protected void doVerify() {
		List<ClassName> classNames = ClassNameLocalServiceUtil.getClassNames(
			QueryUtil.ALL_POS, QueryUtil.ALL_POS);

		for (ClassName className : classNames) {
			String value = className.getValue();

			if (ResourceBlockLocalServiceUtil.isSupported(value) &&
				!value.equals(ResourceBlock.class.getName()) &&
				!value.equals(ResourceBlockPermission.class.getName())) {

				int pos = value.lastIndexOf(CharPool.PERIOD);

				String tableName = value;

				if (pos >= 0) {
					tableName = value.substring(pos + 1);
				}

				try (PreparedStatement ps = connection.prepareStatement(
						"select count(*) from " + tableName);
					ResultSet rs = ps.executeQuery()) {

					if (rs.next()) {
						_modelNames.add(value);
						_tableNames.add(tableName);
					}
				}
				catch (SQLException sqle) {
					if (_log.isDebugEnabled()) {
						_log.debug(sqle, sqle);
					}
				}
			}
		}

		super.doVerify();
	}

	@Override
	protected List<String> getModelNames() {
		return _modelNames;
	}

	@Override
	protected List<String> getTableNames() {
		return _tableNames;
	}

	private static final Log _log = LogFactoryUtil.getLog(
		VerifyResourceBlocks.class);

	private final List<String> _modelNames = new ArrayList<>();
	private final List<String> _tableNames = new ArrayList<>();

}