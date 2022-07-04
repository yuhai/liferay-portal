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

package com.liferay.petra.process.local;

import com.liferay.petra.process.ProcessLog4jLog;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author Hai Yu
 */
class LocalProcessLog4jLog implements ProcessLog4jLog {

	LocalProcessLog4jLog(Map<String, Object> log4jInfo) {
		_log4jInfo = log4jInfo;
	}

	@Override
	public String getName() {
		return (String)_log4jInfo.get("loggerName");
	}

	@Override
	public String getLevel() {
		return (String)_log4jInfo.get("level");
	}

	@Override
	public String getMessage() {
		return (String)_log4jInfo.get("msg");
	}

	@Override
	public Throwable getThrowable() {
		return (Throwable)_log4jInfo.get("throwable");
	}

	private Map<String, Object> _log4jInfo;

}