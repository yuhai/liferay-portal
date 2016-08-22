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

package com.liferay.calendar.verify;

import com.liferay.calendar.internal.verify.model.CalendarBookingVerifiableModel;
import com.liferay.calendar.internal.verify.model.CalendarResourceVerifiableModel;
import com.liferay.calendar.internal.verify.model.CalendarVerifiableModel;
import com.liferay.portal.kernel.util.LoggingTimer;
import com.liferay.portal.verify.VerifyProcess;
import com.liferay.portal.verify.VerifyResourceBlock;

import org.osgi.service.component.annotations.Component;

/**
 * @author Hai Yu
 */
@Component(
	immediate = true,
	property = {"verify.process.name=com.liferay.calendar.service"},
	service = VerifyProcess.class
)
public class CalendarServiceVerifyProcess extends VerifyProcess {

	@Override
	protected void doVerify() throws Exception {
		verifyResourcedModels();
	}

	protected void verifyResourcedModels() throws Exception {
		try (LoggingTimer loggingTimer = new LoggingTimer()) {
			_verifyResourceBlock.verify(new CalendarVerifiableModel());
			_verifyResourceBlock.verify(new CalendarBookingVerifiableModel());
			_verifyResourceBlock.verify(new CalendarResourceVerifiableModel());
		}
	}

	private final VerifyResourceBlock _verifyResourceBlock =
		new VerifyResourceBlock();

}