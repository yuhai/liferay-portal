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

package com.liferay.portal.log;

import com.liferay.petra.process.ProcessLog4jLog;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactory;
import com.liferay.portal.kernel.log.LogFactoryUtil;

import java.util.function.Consumer;

import org.apache.logging.log4j.Level;

/**
 * @author Hai Yu
 */
public class ProcessLog4jLogConsumer implements Consumer<ProcessLog4jLog>{

	@Override
	public void accept(ProcessLog4jLog processLog4jLog) {
		String loggerName = processLog4jLog.getName();
		String level = processLog4jLog.getLevel();
		String message = processLog4jLog.getMessage();
		Throwable throwable = processLog4jLog.getThrowable();

		LogFactory logFactory =LogFactoryUtil.getLogFactory();

		Log log = logFactory.getLog(loggerName);

		Level logLevel = Level.toLevel(level);

		if (logLevel.equals(Level.TRACE)) {
			log.trace(message, throwable);
		}
		else if (logLevel.equals(Level.DEBUG)) {
			log.debug(message, throwable);
		}
		else if (logLevel.equals(Level.INFO)) {
			log.info(message, throwable);
		}
		else if (logLevel.equals(Level.WARN)) {
			log.warn(message, throwable);
		}
		else if (logLevel.equals(Level.ERROR)) {
			log.error(message, throwable);
		}
	}
}
