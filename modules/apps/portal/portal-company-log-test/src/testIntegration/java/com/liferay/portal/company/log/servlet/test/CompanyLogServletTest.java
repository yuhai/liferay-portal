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

package com.liferay.portal.company.log.servlet.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.petra.log4j.Log4JUtil;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.security.auth.PrincipalException;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.servlet.HttpHeaders;
import com.liferay.portal.kernel.test.util.CompanyTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.test.util.UserTestUtil;
import com.liferay.portal.kernel.util.MimeTypes;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.kernel.webdav.methods.Method;
import com.liferay.portal.test.log.LogCapture;
import com.liferay.portal.test.log.LogEntry;
import com.liferay.portal.test.log.LoggerTestUtil;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;

import java.io.File;

import java.nio.file.Files;

import java.util.List;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServletResponse;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * @author Hai Yu
 */
@RunWith(Arquillian.class)
public class CompanyLogServletTest {

	@ClassRule
	@Rule
	public static final LiferayIntegrationTestRule liferayIntegrationTestRule =
		new LiferayIntegrationTestRule();

	@BeforeClass
	public static void setUpClass() throws Exception {
		_newCompany = CompanyTestUtil.addCompany();

		_adminUser = UserTestUtil.addCompanyAdminUser(_newCompany);

		_defaultCompany = _companyLocalService.getCompany(
			TestPropsValues.getCompanyId());
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		String logFilesDirPath = Log4JUtil.getCompanyLogDirectory(
			_newCompany.getCompanyId());

		File logFilesDir = new File(logFilesDirPath);

		for (File file : logFilesDir.listFiles()) {
			file.delete();
		}

		logFilesDir.delete();

		_companyLocalService.deleteCompany(_newCompany);
	}

	@Test
	public void testListCompaniesLogFilesWithCompanyAdminUser()
		throws Exception {

		_servlet.service(
			_createMockHttpServletRequest("/", _adminUser),
			_mockHttpServletResponse);

		String responseContent = _mockHttpServletResponse.getContentAsString();

		Assert.assertFalse(
			"Response content should not include webId " +
				_defaultCompany.getWebId(),
			responseContent.contains(_defaultCompany.getWebId()));
		Assert.assertTrue(
			"Response content should include webId " + _newCompany.getWebId(),
			responseContent.contains(_newCompany.getWebId()));

		_assertCompanyLogFilesDisplay(_newCompany, responseContent);
	}

	@Test
	public void testListCompaniesLogFilesWithCompanyUser() throws Exception {
		User user = UserTestUtil.addUser(_newCompany);

		try (LogCapture logCapture = LoggerTestUtil.configureLog4JLogger(
				"com.liferay.portal.company.log.internal.servlet." +
					"CompanyLogServlet",
				LoggerTestUtil.WARN)) {

			_servlet.service(
				_createMockHttpServletRequest("/", user),
				_mockHttpServletResponse);

			Assert.assertEquals(
				HttpServletResponse.SC_FORBIDDEN,
				_mockHttpServletResponse.getStatus());

			List<LogEntry> logEntries = logCapture.getLogEntries();

			Assert.assertEquals(logEntries.toString(), 1, logEntries.size());

			LogEntry logEntry = logEntries.get(0);

			Throwable throwable = logEntry.getThrowable();

			Assert.assertEquals(
				PrincipalException.MustBeCompanyAdmin.class,
				throwable.getClass());
		}
	}

	@Test
	public void testListCompaniesLogFilesWithOmniAdminUser() throws Exception {
		User omniAdminUser = null;

		try {
			omniAdminUser = UserTestUtil.addOmniAdminUser();

			_servlet.service(
				_createMockHttpServletRequest("/", omniAdminUser),
				_mockHttpServletResponse);

			String responseContent =
				_mockHttpServletResponse.getContentAsString();

			Assert.assertTrue(
				"Response content should include webId " +
					_defaultCompany.getWebId(),
				responseContent.contains(_defaultCompany.getWebId()));
			Assert.assertTrue(
				"Response content should include webId " +
					_newCompany.getWebId(),
				responseContent.contains(_newCompany.getWebId()));

			_assertCompanyLogFilesDisplay(_defaultCompany, responseContent);
			_assertCompanyLogFilesDisplay(_newCompany, responseContent);
		}
		finally {
			if (omniAdminUser != null) {
				_userLocalService.deleteUser(omniAdminUser);
			}
		}
	}

	@Test
	public void testSendFileWithDownloadForFalse() throws Exception {
		File logFile = _getNewCompanyLogFile();

		MockHttpServletRequest mockHttpServletRequest =
			_createMockHttpServletRequest(
				StringBundler.concat(
					"/", _newCompany.getCompanyId(), "/", logFile.getName()),
				_adminUser);

		mockHttpServletRequest.setParameter("download", "false");

		_servlet.service(mockHttpServletRequest, _mockHttpServletResponse);

		Assert.assertEquals(
			"text/html", _mockHttpServletResponse.getContentType());

		String responseContent = _mockHttpServletResponse.getContentAsString();

		String startIndexInput = "<input name = \"startIndex\"";
		String endIndexInput = "<input name = \"endIndex\"";
		String submitInput = "<input type = \"submit\"";
		String formTag = "<form action = \"";

		Assert.assertTrue(
			"Response content should include " + startIndexInput,
			responseContent.contains(startIndexInput));
		Assert.assertTrue(
			"Response content should include " + endIndexInput,
			responseContent.contains(endIndexInput));
		Assert.assertTrue(
			"Response content should include " + submitInput,
			responseContent.contains(submitInput));
		Assert.assertTrue(
			"Response content should include " + formTag,
			responseContent.contains(formTag));

		String logFileContent = new String(
			Files.readAllBytes(logFile.toPath()));

		Assert.assertTrue(
			"Response content should include " + logFileContent,
			responseContent.contains(logFileContent));
	}

	@Test
	public void testSendFileWithDownloadForTrue() throws Exception {
		File logFile = _getNewCompanyLogFile();

		MockHttpServletRequest mockHttpServletRequest =
			_createMockHttpServletRequest(
				StringBundler.concat(
					"/", _newCompany.getCompanyId(), "/", logFile.getName()),
				_adminUser);

		mockHttpServletRequest.setParameter("download", "true");

		_servlet.service(mockHttpServletRequest, _mockHttpServletResponse);

		StringBundler sb = new StringBundler(4);

		sb.append(HttpHeaders.CONTENT_DISPOSITION_ATTACHMENT);
		sb.append("; filename=\"");
		sb.append(logFile.getName());
		sb.append("\"");

		Assert.assertEquals(
			sb.toString(),
			_mockHttpServletResponse.getHeader(
				HttpHeaders.CONTENT_DISPOSITION));

		Assert.assertEquals(
			String.valueOf(logFile.length()),
			_mockHttpServletResponse.getHeader(HttpHeaders.CONTENT_LENGTH));

		Assert.assertEquals(
			_mimeTypes.getContentType(logFile),
			_mockHttpServletResponse.getContentType());
	}

	private void _assertCompanyLogFilesDisplay(
			Company company, String responseContent)
		throws Exception {

		String logFilesDirPath = Log4JUtil.getCompanyLogDirectory(
			company.getCompanyId());

		File logFilesDir = new File(logFilesDirPath);

		File[] files = logFilesDir.listFiles();

		Assert.assertTrue(
			"The directory " + logFilesDirPath + " must have files",
			files.length > 0);

		for (File file : logFilesDir.listFiles()) {
			Assert.assertTrue(
				"Response content should include fileName " + file.getName(),
				responseContent.contains(file.getName()));
		}
	}

	private MockHttpServletRequest _createMockHttpServletRequest(
			String path, User user)
		throws Exception {

		MockHttpServletRequest mockHttpServletRequest =
			new MockHttpServletRequest(Method.GET, "/company-log" + path);

		mockHttpServletRequest.setAttribute(WebKeys.USER, user);
		mockHttpServletRequest.setContextPath("/company-log");
		mockHttpServletRequest.setPathInfo(path);
		mockHttpServletRequest.setServletPath(StringPool.BLANK);

		return mockHttpServletRequest;
	}

	private File _getNewCompanyLogFile() {
		File logFilesDir = new File(
			Log4JUtil.getCompanyLogDirectory(_newCompany.getCompanyId()));

		for (File file : logFilesDir.listFiles()) {
			return file;
		}

		return null;
	}

	private static User _adminUser;

	@Inject
	private static CompanyLocalService _companyLocalService;

	private static Company _defaultCompany;
	private static Company _newCompany;

	@Inject
	private MimeTypes _mimeTypes;

	private final MockHttpServletResponse _mockHttpServletResponse =
		new MockHttpServletResponse();

	@Inject(
		filter = "osgi.http.whiteboard.servlet.name=com.liferay.portal.company.log.internal.servlet.CompanyLogServlet"
	)
	private Servlet _servlet;

	@Inject
	private UserLocalService _userLocalService;

}