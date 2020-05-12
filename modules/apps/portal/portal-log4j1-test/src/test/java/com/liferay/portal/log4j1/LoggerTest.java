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

package com.liferay.portal.log4j1;

import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.io.unsync.UnsyncByteArrayOutputStream;
import com.liferay.portal.kernel.io.unsync.UnsyncStringReader;
import com.liferay.portal.kernel.util.StreamUtil;
import com.liferay.portal.kernel.util.StringUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.StringWriter;

import java.net.URL;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;
import org.apache.log4j.WriterAppender;
import org.apache.log4j.spi.LocationInfo;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.xml.DOMConfigurator;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author Hai Yu
 */
public class LoggerTest {

	@BeforeClass
	public static void setUpClass() throws Exception {
		_printStream = new PrintStream(_baos);

		System.setOut(_printStream);

		Thread currentThread = Thread.currentThread();

		ClassLoader classLoader = currentThread.getContextClassLoader();

		URL url = classLoader.getResource(
			"com/liferay/portal/log4j1/dependencies/log4j.xml");

		String urlContent = "";

		try (InputStream inputStream = url.openStream()) {
			byte[] bytes = _getBytes(inputStream);

			urlContent = new String(bytes, StringPool.UTF8);
		}
		catch (IOException ioException) {
			throw ioException;
		}

		DOMConfigurator domConfigurator = new DOMConfigurator();

		domConfigurator.doConfigure(
			new UnsyncStringReader(urlContent),
			LogManager.getLoggerRepository());
	}

	@AfterClass
	public static void tearDownClass() {
		_printStream.flush();

		_printStream.close();
	}

	@Test
	public void testConsoleAppender() {
		CustomAppender customAppender = new CustomAppender();

		Logger logger = Logger.getLogger(LoggerTest.class.getName());

		LoggerWrapper loggerWrapper = new LoggerWrapper(logger);

		logger.addAppender(customAppender);

		loggerWrapper.info("Test Message");

		String[] logMessages = StringUtil.splitLines(_baos.toString());

		String expectedOutput = logMessages[logMessages.length - 1];

		try {
			Matcher matcher = _pattern.matcher(expectedOutput.substring(0, 23));

			Assert.assertTrue(
				"Output date format should be yyyy-MM-dd HH:mm:ss.SSS",
				matcher.matches());

			LoggingEvent loggingEvent = customAppender.getLoggingEvent();

			LocationInfo locationInfo = loggingEvent.getLocationInformation();

			String debugContent = expectedOutput.substring(23);

			Assert.assertTrue(
				"output content should be " + debugContent,
				debugContent.equals(
					StringBundler.concat(
						" ", loggingEvent.getLevel(), "  [",
						loggingEvent.getThreadName(), "][LoggerTest:",
						locationInfo.getLineNumber(), "] ",
						loggingEvent.getRenderedMessage())));
		}
		finally {
			_baos.reset();

			logger.removeAppender(customAppender);
		}
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

		logger.setLevel(Level.toLevel("DEBUG"));

		level = logger.getLevel();

		Assert.assertEquals(
			"Logger level should be DEBUG", level.toString(), "DEBUG");
		Assert.assertTrue(
			"DEBUG level should be enabled", logger.isEnabledFor(level));

		logger.setLevel(Level.toLevel("WARN"));
	}

	@Test
	public void testWriteAppender() {
		StringWriter stringWrite = new StringWriter();

		WriterAppender writerAppender = new WriterAppender(
			new SimpleLayout(), stringWrite);

		Logger logger = Logger.getLogger(LoggerTest.class.getName());

		logger.addAppender(writerAppender);

		LoggerWrapper loggerWrapper = new LoggerWrapper(logger);

		loggerWrapper.info("Test message");

		String logMessage = stringWrite.toString();

		try {
			Assert.assertTrue(
				"Log message should be " + logMessage,
				logMessage.equals(
					Level.INFO + " - Test message" +
						System.getProperty("line.separator")));
		}
		finally {
			logger.removeAppender(writerAppender);
		}
	}

	private static byte[] _getBytes(InputStream inputStream)
		throws IOException {

		UnsyncByteArrayOutputStream unsyncByteArrayOutputStream =
			new UnsyncByteArrayOutputStream();

		StreamUtil.transfer(inputStream, unsyncByteArrayOutputStream, -1, true);

		return unsyncByteArrayOutputStream.toByteArray();
	}

	private static final String[] _NAMES = {
		"level", "level.off", "level.fatal", "level.error", "level.warn",
		"level.info", "level.debug", "level.trace"
	};

	private static final ByteArrayOutputStream _baos =
		new ByteArrayOutputStream();
	private static final Pattern _pattern = Pattern.compile(
		"\\d\\d\\d\\d-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d.\\d\\d\\d");
	private static PrintStream _printStream;

	private class CustomAppender extends AppenderSkeleton {

		@Override
		public void close() {
		}

		public LoggingEvent getLoggingEvent() {
			return _loggingEvent;
		}

		@Override
		public boolean requiresLayout() {
			return false;
		}

		@Override
		protected void append(LoggingEvent event) {
			_loggingEvent = event;
		}

		private LoggingEvent _loggingEvent;

	}

}