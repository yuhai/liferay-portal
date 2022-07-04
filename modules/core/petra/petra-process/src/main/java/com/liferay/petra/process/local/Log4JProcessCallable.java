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

import com.liferay.petra.process.ProcessCallable;
import com.liferay.petra.process.ProcessException;

import java.io.Serializable;
import java.util.HashMap;

/**
 * @author Hai Yu
 */
public class Log4JProcessCallable<T extends Serializable>
	implements ProcessCallable<HashMap<String, Object>> {

	public Log4JProcessCallable(String loggerName, String level, String msg,
		Throwable throwable) {

		_log4jInfo = new HashMap<>();

		_log4jInfo.put("loggerName", loggerName);
		_log4jInfo.put("level", level);
		_log4jInfo.put("msg", msg);
		_log4jInfo.put("throwable", throwable);
	}

	@Override
	public HashMap<String, Object> call() throws ProcessException {
		return _log4jInfo;
	}

	private static final long serialVersionUID = 1L;

	private HashMap<String, Object> _log4jInfo;

}