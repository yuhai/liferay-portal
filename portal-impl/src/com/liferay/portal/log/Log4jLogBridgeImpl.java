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

import com.liferay.petra.process.local.LocalProcessLauncher;
import com.liferay.petra.process.local.Log4JProcessCallable;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogWrapper;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

/**
 * @author Hai Yu
 */
public class Log4jLogBridgeImpl implements Log {

	public Log4jLogBridgeImpl(String name) {
		_name = name;
	}

	@Override
	public void debug(Object msg){
		try {
			LocalProcessLauncher.ProcessContext.writeProcessCallable(
				new Log4JProcessCallable(_name, "debug", String.valueOf(msg), null));
		}
		catch (Exception e) {}
	}

	@Override
	public void debug(Object msg, Throwable throwable) {
		
	}

	@Override
	public void debug(Throwable throwable) {
		
	}

	@Override
	public void error(Object msg) {
		
	}

	@Override
	public void error(Object msg, Throwable throwable) {
		
	}

	@Override
	public void error(Throwable throwable) {
		
	}

	@Override
	public void fatal(Object msg) {
		
	}

	@Override
	public void fatal(Object msg, Throwable throwable) {
	
	}

	@Override
	public void fatal(Throwable throwable) {
		
	}

	@Override
	public void info(Object msg) {
		
	}

	@Override
	public void info(Object msg, Throwable throwable) {
		
	}

	@Override
	public void info(Throwable throwable) {
		
	}

	@Override
	public boolean isDebugEnabled() {
		return true;
	}

	@Override
	public boolean isErrorEnabled() {
		return true;
	}

	@Override
	public boolean isFatalEnabled() {
		return true;
	}

	@Override
	public boolean isInfoEnabled() {
		return true;
	}

	@Override
	public boolean isTraceEnabled() {
		return true;
	}

	@Override
	public boolean isWarnEnabled() {
		return true;
	}

	@Override
	public void setLogWrapperClassName(String className) {
	}

	@Override
	public void trace(Object msg) {
		
	}

	@Override
	public void trace(Object msg, Throwable throwable) {
		
	}

	@Override
	public void trace(Throwable throwable) {
		
	}

	@Override
	public void warn(Object msg) {
		
	}

	@Override
	public void warn(Object msg, Throwable throwable) {
		
	}

	@Override
	public void warn(Throwable throwable) {
	
	}

	private final String _name;
	private String _logWrapperClassName = LogWrapper.class.getName();

}