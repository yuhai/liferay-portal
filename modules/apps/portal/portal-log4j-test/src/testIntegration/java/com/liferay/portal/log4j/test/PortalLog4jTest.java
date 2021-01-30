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

package com.liferay.portal.log4j.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.petra.io.unsync.UnsyncStringWriter;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.log.SanitizerLogWrapper;
import com.liferay.portal.kernel.test.ReflectionTestUtil;
import com.liferay.portal.kernel.util.StringUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Enumeration;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.OutputStreamManager;
import org.apache.logging.log4j.core.appender.rolling.RollingFileManager;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.util.CloseShieldOutputStream;
import org.apache.logging.log4j.core.config.Configuration;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Hai Yu
 */
@RunWith(Arquillian.class)
public class PortalLog4jTest {

	@BeforeClass
	public static void setUpClass() throws Exception {
		_tempLogFileDirPath = Files.createTempDirectory(
			PortalLog4jTest.class.getName());

		Logger logger = (Logger)LogManager.getLogger(PortalLog4jTest.class);

		logger.setAdditive(false);
		logger.setLevel(Level.TRACE);

		Logger rootLogger = (Logger)LogManager.getRootLogger();

		Map<String, Appender> appendersMap= rootLogger.getAppenders();

		for (Appender appender : appendersMap.values()) {
			if ((appender instanceof ConsoleAppender) &&
				Objects.equals("CONSOLE", appender.getName())) {

				ConsoleAppender consoleAppender =
					ConsoleAppender.createDefaultAppenderForLayout(
						appender.getLayout());

				OutputStreamManager outputStreamManager =
					consoleAppender.getManager();

				_testOutputStream =
					new TestOutputStream(
						(OutputStream)ReflectionTestUtil.getFieldValue(
							outputStreamManager, "outputStream"));

				ReflectionTestUtil.getAndSetFieldValue(
					outputStreamManager, "outputStream", _testOutputStream);

				consoleAppender.start();

				logger.addAppender(consoleAppender);
			}
			else if (appender instanceof RollingFileAppender) {
				if (Objects.equals("TEXT_FILE", appender.getName())) {
					_textLogFilePath = _initFileAppender(
						logger, appender, _tempLogFileDirPath.toString());
				}
				else if (Objects.equals("XML_FILE", appender.getName())) {
					_xmlLogFilePath = _initFileAppender(
						logger, appender, _tempLogFileDirPath.toString());
				}
			}
		}
	}

	@AfterClass
	public static void tearDownClass() throws IOException {
		Logger logger = (Logger)LogManager.getLogger(PortalLog4jTest.class);

		Map<String, Appender> appendersMap = logger.getAppenders();

		for (Appender appender : appendersMap.values()) {
			logger.removeAppender(appender);
		}

		Files.deleteIfExists(_textLogFilePath);
		Files.deleteIfExists(_xmlLogFilePath);

		Files.deleteIfExists(_tempLogFileDirPath);
	}

	@Test
	public void testDefaultLevel() {
		Logger logger = (Logger)LogManager.getLogger("test.logger");

		Assert.assertFalse(logger.isDebugEnabled());
		Assert.assertTrue(logger.isInfoEnabled());
	}

	@Test
	public void testLogOutput() throws Exception {
		_testLogOutput("DEBUG");
		_testLogOutput("ERROR");
		_testLogOutput("FATAL");
		_testLogOutput("INFO");
		_testLogOutput("TRACE");
		_testLogOutput("WARN");
	}

	private static Path _initFileAppender(
		Logger logger, Appender appender, String tempLogDir) {

		RollingFileAppender portalRollingFileAppender =
			(RollingFileAppender)appender;

		String testFilePattern = 
			StringBundler.concat(
				StringUtil.replace(tempLogDir, '\\', '/'), StringPool.SLASH,
					StringUtil.extractLast(
						portalRollingFileAppender.getFilePattern(),
						StringPool.SLASH));

		LoggerContext loggerContext = (LoggerContext)LogManager.getContext();

		RollingFileAppender testRollingFileAppender =
			RollingFileAppender.createAppender(
				null, testFilePattern, "true",
				portalRollingFileAppender.getName(), "true", "8192", "true",
				portalRollingFileAppender.getTriggeringPolicy(), null,
				portalRollingFileAppender.getLayout(), null, "false", null,
				null, loggerContext.getConfiguration());

		testRollingFileAppender.start();

		logger.addAppender(testRollingFileAppender);

		RollingFileManager testRollingFileManager =
			testRollingFileAppender.getManager();

		return Paths.get(testRollingFileManager.getFileName());
	}

	private void _assertTextLog(
		String expectedLevel, String expectedMessage,
		Throwable expectedThrowable, String actualOutput) {

		String[] outputLines = StringUtil.splitLines(actualOutput);

		Assert.assertTrue(
			"The log output should have at least 1 line",
			outputLines.length > 0);

		String messageLine = outputLines[0];

		// Date format

		Matcher dateMatcher = _datePattern.matcher(
			messageLine.substring(0, _DATE_FORMAT.length()));

		Assert.assertTrue(
			"Output date format should be yyyy-MM-dd HH:mm:ss.SSS",
			dateMatcher.matches());

		// Level

		messageLine = messageLine.substring(_DATE_FORMAT.length());

		Assert.assertEquals(
			StringBundler.concat(
				StringPool.SPACE, expectedLevel, StringPool.SPACE),
			messageLine.substring(0, expectedLevel.length() + 2));

		// Thread name

		messageLine = messageLine.substring(
			messageLine.indexOf(StringPool.OPEN_BRACKET));

		Thread currentThread = Thread.currentThread();

		String expectedThreadName = StringBundler.concat(
			StringPool.OPEN_BRACKET, currentThread.getName(),
			StringPool.CLOSE_BRACKET);

		Assert.assertEquals(
			expectedThreadName,
			messageLine.substring(0, expectedThreadName.length()));

		// Class name

		messageLine = messageLine.substring(expectedThreadName.length());

		String expectedClassName = StringBundler.concat(
			StringPool.OPEN_BRACKET, PortalLog4jTest.class.getSimpleName(),
			StringPool.COLON);

		Assert.assertEquals(
			expectedClassName,
			messageLine.substring(0, expectedClassName.length()));

		// Line number

		messageLine = messageLine.substring(expectedClassName.length());

		int classNameEndIndex = messageLine.indexOf(StringPool.CLOSE_BRACKET);

		Integer.valueOf(messageLine.substring(0, classNameEndIndex - 1));

		// Message

		messageLine = messageLine.substring(classNameEndIndex + 1);

		Assert.assertEquals(
			String.valueOf(expectedMessage), messageLine.trim());

		// Throwable

		if (expectedThrowable != null) {
			Class<?> expectedThrowableClass = expectedThrowable.getClass();

			Assert.assertEquals(
				expectedThrowableClass.getName(), outputLines[1]);

			String actualFirstPrefixStackTraceElement = outputLines[2].trim();

			Assert.assertTrue(
				"A throwable should be logged and the first stack should be " +
					PortalLog4jTest.class.getName(),
				actualFirstPrefixStackTraceElement.startsWith(
					"at " + PortalLog4jTest.class.getName()));
		}
	}

	private void _assertXmlLog(
		String expectedLevel, String expectedMessage,
		Throwable expectedThrowable, String actualOutput) {

		String[] outputLines = StringUtil.splitLines(actualOutput);

		Assert.assertTrue(
			"The log output should have at least 1 line",
			outputLines.length > 0);

		// Event

		String eventLine = outputLines[0];

		String event = eventLine.substring(
			eventLine.indexOf(StringPool.SPACE),
			eventLine.indexOf(StringPool.GREATER_THAN));

		// Event xmlns

		String expectedEventXmlns = StringBundler.concat(
			StringPool.SPACE, "xmlns=", StringPool.QUOTE,
			"http://logging.apache.org/log4j/2.0/events", StringPool.QUOTE,
			StringPool.SPACE);

		Assert.assertEquals(
			expectedEventXmlns,
			event.substring(0, expectedEventXmlns.length()));

		// Event timeMillis

		event = event.substring(expectedEventXmlns.length());

		String actualEventtimeMillis = event.substring(
			event.indexOf(StringPool.QUOTE) + 1,
			event.indexOf(StringPool.SPACE) - 1);

		Long.valueOf(actualEventtimeMillis);

		// Event thread

		event = event.substring(
			"timeMillis=".length() + actualEventtimeMillis.length() + 2);

		Thread currentThread = Thread.currentThread();

		String expectedEventThread = StringBundler.concat(
			StringPool.SPACE, "thread=", StringPool.QUOTE,
			currentThread.getName(), StringPool.QUOTE,
			StringPool.SPACE);

		Assert.assertEquals(
			expectedEventThread,
			event.substring(0, expectedEventThread.length()));

		// Event level

		event = event.substring(expectedEventThread.length());

		String expectedEventLevel = StringBundler.concat(
			"level=", StringPool.QUOTE, expectedLevel,
			StringPool.QUOTE, StringPool.SPACE);

		Assert.assertEquals(
			expectedEventLevel,
			event.substring(0, expectedEventLevel.length()));

		// Event loggerName

		event = event.substring(expectedEventLevel.length());

		String expectedEventLoggerName = StringBundler.concat(
			"loggerName=", StringPool.QUOTE,
			PortalLog4jTest.class.getName(), StringPool.QUOTE,
			StringPool.SPACE);

		Assert.assertEquals(
			expectedEventLoggerName,
			event.substring(0, expectedEventLoggerName.length()));

		// Event endOfBatch

		event = event.substring(expectedEventLoggerName.length());

		String expectedEventEndOfBatch = StringBundler.concat(
			"endOfBatch=", StringPool.QUOTE,
			String.valueOf(false), StringPool.QUOTE, StringPool.SPACE);

		Assert.assertEquals(
			expectedEventEndOfBatch,
			event.substring(0, expectedEventEndOfBatch.length()));

		// Event loggerFqcn

		event = event.substring(expectedEventEndOfBatch.length());

		String expectedEventLoggerFqcn = StringBundler.concat(
			"loggerFqcn=", StringPool.QUOTE,
			SanitizerLogWrapper.class.getName(), StringPool.QUOTE,
			StringPool.SPACE);

		Assert.assertEquals(
			expectedEventLoggerFqcn,
			event.substring(0, expectedEventLoggerFqcn.length()));

		// Event threadId

		event = event.substring(expectedEventLoggerFqcn.length());

		String expectedEventThreadId = StringBundler.concat(
			"threadId=", StringPool.QUOTE,
			currentThread.getId(), StringPool.QUOTE,
			StringPool.SPACE);

		Assert.assertEquals(
			expectedEventThreadId,
			event.substring(0, expectedEventThreadId.length()));

		// Event threadPriority

		event = event.substring(expectedEventThreadId.length());

		String expectedEventThreadPriority = StringBundler.concat(
			"threadPriority=", StringPool.QUOTE,
			currentThread.getPriority(), StringPool.QUOTE);

		Assert.assertEquals(
			expectedEventThreadPriority,
			event.substring(0, expectedEventThreadPriority.length()));

		// Instant

		
		// log4j:message

		if (expectedThrowable != null) {
			if (expectedMessage == null) {
				expectedMessage = StringPool.BLANK;
			}

			Assert.assertEquals(
				StringBundler.concat(
					"<log4j:message>", StringPool.CDATA_OPEN, expectedMessage,
					StringPool.CDATA_CLOSE, "</log4j:message>"),
				outputLines[1]);
		}

		// log4j:throwable

		if (expectedThrowable != null) {
			Class<?> expectedThrowableClass = expectedThrowable.getClass();

			Assert.assertEquals(
				StringBundler.concat(
					"<log4j:throwable>", StringPool.CDATA_OPEN,
					expectedThrowableClass.getName()),
				outputLines[2]);

			String actualFirstPrefixStackTraceElement = outputLines[3].trim();

			Assert.assertTrue(
				"A throwable should be logged and the first stack should be " +
					PortalLog4jTest.class.getName(),
				actualFirstPrefixStackTraceElement.startsWith(
					"at " + PortalLog4jTest.class.getName()));
		}

		// log4j:locationInfo

		String log4JLocationInfoLine = outputLines[outputLines.length - 2];

		String log4JLocationInfo = log4JLocationInfoLine.substring(
			log4JLocationInfoLine.indexOf(StringPool.SPACE),
			log4JLocationInfoLine.indexOf(StringPool.FORWARD_SLASH));

		// log4j:locationInfo class name

		String expectedLog4JLocationInfoClassName = StringBundler.concat(
			StringPool.SPACE, "class=", StringPool.QUOTE,
			PortalLog4jTest.class.getName(), StringPool.QUOTE,
			StringPool.SPACE);

		Assert.assertEquals(
			expectedLog4JLocationInfoClassName,
			log4JLocationInfo.substring(
				0, expectedLog4JLocationInfoClassName.length()));

		// log4j:locationInfo file

		log4JLocationInfo = log4JLocationInfo.substring(
			expectedLog4JLocationInfoClassName.length());
		log4JLocationInfo = log4JLocationInfo.substring(
			log4JLocationInfo.indexOf("file"));

		String expectedLog4JLocationInfoFile = StringBundler.concat(
			"file=", StringPool.QUOTE, PortalLog4jTest.class.getSimpleName(),
			".java", StringPool.QUOTE);

		Assert.assertEquals(
			expectedLog4JLocationInfoFile,
			log4JLocationInfo.substring(
				0, expectedLog4JLocationInfoFile.length()));
	}

	private void _outputLog(String level, String message, Throwable throwable) {
		if (level.equals("DEBUG")) {
			if ((message == null) && (throwable != null)) {
				_log.debug(throwable);
			}
			else if ((message != null) && (throwable == null)) {
				_log.debug(message);
			}
			else {
				_log.debug(message, throwable);
			}
		}
		else if (level.equals("ERROR")) {
			if ((message == null) && (throwable != null)) {
				_log.error(throwable);
			}
			else if ((message != null) && (throwable == null)) {
				_log.error(message);
			}
			else {
				_log.error(message, throwable);
			}
		}
		else if (level.equals("FATAL")) {
			if ((message == null) && (throwable != null)) {
				_log.fatal(throwable);
			}
			else if ((message != null) && (throwable == null)) {
				_log.fatal(message);
			}
			else {
				_log.fatal(message, throwable);
			}
		}
		else if (level.equals("INFO")) {
			if ((message == null) && (throwable != null)) {
				_log.info(throwable);
			}
			else if ((message != null) && (throwable == null)) {
				_log.info(message);
			}
			else {
				_log.info(message, throwable);
			}
		}
		else if (level.equals("TRACE")) {
			if ((message == null) && (throwable != null)) {
				_log.trace(throwable);
			}
			else if ((message != null) && (throwable == null)) {
				_log.trace(message);
			}
			else {
				_log.trace(message, throwable);
			}
		}
		else if (level.equals("WARN")) {
			if ((message == null) && (throwable != null)) {
				_log.warn(throwable);
			}
			else if ((message != null) && (throwable == null)) {
				_log.warn(message);
			}
			else {
				_log.warn(message, throwable);
			}
		}
	}

	private void _testLogOutput(String level) throws Exception {
		String testMessage = level + " message";

		_testLogOutput(level, testMessage, null);

		TestException testException = new TestException();

		_testLogOutput(level, testMessage, testException);

		_testLogOutput(level, null, testException);
	}

	private void _testLogOutput(
			String level, String message, Throwable throwable)
		throws Exception {

		_outputLog(level, message, throwable);

		try {
			_assertTextLog(
				level, message, throwable, _unsyncStringWriter.toString());

			_assertTextLog(
				level, message, throwable,
				new String(Files.readAllBytes(_textLogFilePath)));

			_assertXmlLog(
				level, message, throwable,
				new String(Files.readAllBytes(_xmlLogFilePath)));
		}
		finally {
			_unsyncStringWriter.reset();

			Files.write(
				_textLogFilePath, new byte[0],
				StandardOpenOption.TRUNCATE_EXISTING);
			Files.write(
				_xmlLogFilePath, new byte[0],
				StandardOpenOption.TRUNCATE_EXISTING);
		}
	}

	private static final String _DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";

	private static final Log _log = LogFactoryUtil.getLog(
		PortalLog4jTest.class);

	private static final Pattern _datePattern = Pattern.compile(
		"\\d\\d\\d\\d-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d.\\d\\d\\d");
	private static Path _tempLogFileDirPath;
	private static Path _textLogFilePath;
	private final static UnsyncStringWriter _unsyncStringWriter = new UnsyncStringWriter();
	private static Path _xmlLogFilePath;
	private static TestOutputStream _testOutputStream;

	private class TestException extends Exception {
	}

	private static class TestOutputStream extends CloseShieldOutputStream {
		public TestOutputStream(OutputStream originalOutputStream) {
			super(originalOutputStream);
		}

		@Override
		public void write(byte[] b) throws IOException {
			String content = new String(b);

			_unsyncStringWriter.write(content.toCharArray());
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			String content = new String(b);

			_unsyncStringWriter.write(content.toCharArray(), off, len);
		}

		@Override
		public void write(int b) throws IOException {
			_unsyncStringWriter.write(b);
		}
	}

}