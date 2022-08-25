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

package com.liferay.commerce.internal.helper;

import com.liferay.commerce.context.CommerceContext;
import com.liferay.commerce.model.CommerceOrder;
import com.liferay.commerce.service.persistence.CommerceOrderPersistence;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.UserLocalService;

import java.io.Serializable;

import java.math.BigDecimal;

import java.util.Date;
import java.util.Map;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Hai Yu
 */
@Component(enabled = false, service = CommerceOrderHelper.class)
public class CommerceOrderHelper {

	public CommerceOrder updateCommerceShippingMethod(
			long commerceOrderId, long commerceShippingMethodId,
			String commerceShippingOptionName, BigDecimal shippingAmount,
			CommerceContext commerceContext)
		throws PortalException {

		CommerceOrder commerceOrder =
			_commerceOrderPersistence.findByPrimaryKey(commerceOrderId);

		commerceOrder.setCommerceShippingMethodId(commerceShippingMethodId);
		commerceOrder.setShippingAmount(shippingAmount);
		commerceOrder.setShippingOptionName(commerceShippingOptionName);

		return _commerceOrderPersistence.update(commerceOrder);
	}

	public CommerceOrder updateStatus(
			long userId, long commerceOrderId, int status,
			ServiceContext serviceContext,
			Map<String, Serializable> workflowContext)
		throws PortalException {

		if (userId == 0) {
			userId = serviceContext.getUserId();
		}

		User user = _userLocalService.getUser(userId);
		Date date = new Date();

		CommerceOrder commerceOrder =
			_commerceOrderPersistence.findByPrimaryKey(commerceOrderId);

		commerceOrder.setStatus(status);
		commerceOrder.setStatusByUserId(user.getUserId());
		commerceOrder.setStatusByUserName(user.getFullName());
		commerceOrder.setStatusDate(serviceContext.getModifiedDate(date));

		return _commerceOrderPersistence.update(commerceOrder);
	}

	@Reference
	private CommerceOrderPersistence _commerceOrderPersistence;

	@Reference
	private UserLocalService _userLocalService;

}