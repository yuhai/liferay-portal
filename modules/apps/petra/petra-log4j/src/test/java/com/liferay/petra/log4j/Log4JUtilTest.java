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

package com.liferay.petra.log4j;

import com.liferay.portal.kernel.test.util.PropsTestUtil;

import java.net.URL;

import java.util.Collections;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author Hai Yu
 */
public class Log4JUtilTest {

	@BeforeClass
	public static void setUpClass() throws Exception {
		PropsTestUtil.setProps(Collections.emptyMap());

		Thread currentThread = Thread.currentThread();

		ClassLoader classLoader = currentThread.getContextClassLoader();

		URL url = classLoader.getResource(
			"com/liferay/petra/log4j/dependencies/log4j.xml");

		Log4JUtil.configureLog4J(url);
	}

	@Test
	public void testGetOriginalLevel() {
		String level = Log4JUtil.getOriginalLevel(_NAMES[6]);

		Assert.assertTrue(
			"The original level should be debug", level.equals("DEBUG"));
	}

	@Test
	public void testLoggerEnabled() {
		Logger logger = LogManager.getLogger(_NAMES[0]);

		if (logger.isDebugEnabled() && logger.isEnabledFor(Level.ERROR) &&
			logger.isEnabledFor(Level.FATAL) && logger.isInfoEnabled() &&
			logger.isTraceEnabled() && logger.isEnabledFor(Level.WARN)) {
		}
		else {
			Assert.fail("Logger should be all enabled");
		}

		logger = LogManager.getLogger(_NAMES[1]);

		if (logger.isDebugEnabled() || logger.isEnabledFor(Level.ERROR) ||
			logger.isEnabledFor(Level.FATAL) || logger.isInfoEnabled() ||
			logger.isTraceEnabled() || logger.isEnabledFor(Level.WARN)) {

			Assert.fail("Setting logger level OFF does not take effect");
		}

		logger = LogManager.getLogger(_NAMES[2]);

		if (logger.isDebugEnabled() || logger.isEnabledFor(Level.ERROR) ||
			!logger.isEnabledFor(Level.FATAL) || logger.isInfoEnabled() ||
			logger.isTraceEnabled() || logger.isEnabledFor(Level.WARN)) {

			Assert.fail("Setting logger level FATAL does not take effect");
		}

		logger = LogManager.getLogger(_NAMES[3]);

		if (logger.isDebugEnabled() || !logger.isEnabledFor(Level.ERROR) ||
			!logger.isEnabledFor(Level.FATAL) || logger.isInfoEnabled() ||
			logger.isTraceEnabled() || logger.isEnabledFor(Level.WARN)) {

			Assert.fail("Setting logger level ERROR does not take effect");
		}

		logger = LogManager.getLogger(_NAMES[4]);

		if (logger.isDebugEnabled() || !logger.isEnabledFor(Level.ERROR) ||
			logger.isInfoEnabled() || logger.isTraceEnabled() ||
			!logger.isEnabledFor(Level.WARN) ||
			!logger.isEnabledFor(Level.FATAL)) {

			Assert.fail("Setting logger level WARN does not take effect");
		}

		logger = LogManager.getLogger(_NAMES[5]);

		if (logger.isDebugEnabled() || !logger.isEnabledFor(Level.ERROR) ||
			!logger.isInfoEnabled() || logger.isTraceEnabled() ||
			!logger.isEnabledFor(Level.WARN) ||
			!logger.isEnabledFor(Level.FATAL)) {

			Assert.fail("Setting logger level INFO does not take effect");
		}

		logger = LogManager.getLogger(_NAMES[6]);

		if (!logger.isDebugEnabled() || !logger.isEnabledFor(Level.ERROR) ||
			!logger.isInfoEnabled() || logger.isTraceEnabled() ||
			!logger.isEnabledFor(Level.WARN) ||
			!logger.isEnabledFor(Level.FATAL)) {

			Assert.fail("Setting logger level DEBUG does not take effect");
		}

		logger = LogManager.getLogger(_NAMES[7]);

		if (!logger.isDebugEnabled() || !logger.isEnabledFor(Level.ERROR) ||
			!logger.isInfoEnabled() || !logger.isTraceEnabled() ||
			!logger.isEnabledFor(Level.WARN) ||
			!logger.isEnabledFor(Level.FATAL)) {

			Assert.fail("Setting logger level TRACE does not take effect");
		}
	}

	@Test
	public void testSetLevel() {
		Logger logger = LogManager.getLogger(_NAMES[4]);

		Level level = logger.getLevel();

		Assert.assertEquals(
			"Logger level should be WARN", level.toString(), "WARN");
		Assert.assertTrue(
			"Warn level should be enabled", logger.isEnabledFor(level));

		Log4JUtil.setLevel(_NAMES[4], "DEBUG", false);

		level = logger.getLevel();

		Assert.assertEquals(
			"Logger level should be DEBUG", level.toString(), "DEBUG");
		Assert.assertTrue(
			"DEBUG level should be enabled", logger.isEnabledFor(level));

		Log4JUtil.setLevel(_NAMES[4], "WARN", false);
	}

	private static final String[] _NAMES = {
		"level", "level.off", "level.fatal", "level.error", "level.warn",
		"level.info", "level.debug", "level.trace"
	};

}