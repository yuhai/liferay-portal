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

import com.liferay.commerce.constants.CommerceOrderConstants;
import com.liferay.commerce.context.CommerceContext;
import com.liferay.commerce.currency.model.CommerceMoney;
import com.liferay.commerce.discount.CommerceDiscountValue;
import com.liferay.commerce.model.CommerceOrder;
import com.liferay.commerce.model.CommerceOrderItem;
import com.liferay.commerce.price.CommerceOrderPrice;
import com.liferay.commerce.price.CommerceOrderPriceCalculation;
import com.liferay.commerce.service.persistence.CommerceOrderPersistence;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.search.Indexer;
import com.liferay.portal.kernel.search.IndexerRegistry;
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

	public CommerceOrder recalculatePrice(
			long commerceOrderId, CommerceContext commerceContext)
		throws PortalException {

		CommerceOrder commerceOrder =
			_commerceOrderPersistence.findByPrimaryKey(commerceOrderId);

		if ((commerceOrder.getOrderStatus() !=
				CommerceOrderConstants.ORDER_STATUS_OPEN) ||
			commerceOrder.isManuallyAdjusted()) {

			return commerceOrder;
		}

		Indexer<CommerceOrderItem> indexer =
			_indexerRegistry.nullSafeGetIndexer(CommerceOrderItem.class);

		for (CommerceOrderItem commerceOrderItem :
				commerceOrder.getCommerceOrderItems()) {

			commerceOrderItem =
				_commerceOrderItemHelper.updateCommerceOrderItemPrice(
					commerceOrderItem.getCommerceOrderItemId(),
					commerceContext);

			indexer.reindex(
				CommerceOrderItem.class.getName(),
				commerceOrderItem.getCommerceOrderItemId());
		}

		commerceOrder = _commerceOrderPersistence.findByPrimaryKey(
			commerceOrderId);

		CommerceOrderPrice commerceOrderPrice =
			_commerceOrderPriceCalculation.getCommerceOrderPrice(
				commerceOrder, false, commerceContext);

		CommerceMoney subtotalCommerceMoney = commerceOrderPrice.getSubtotal();
		CommerceMoney shippingValueCommerceMoney =
			commerceOrderPrice.getShippingValue();
		CommerceMoney taxValueCommerceMoney = commerceOrderPrice.getTaxValue();
		CommerceMoney totalCommerceMoney = commerceOrderPrice.getTotal();
		CommerceMoney subtotalWithTaxAmountCommerceMoney =
			commerceOrderPrice.getSubtotalWithTaxAmount();
		CommerceMoney shippingValueWithTaxAmountCommerceMoney =
			commerceOrderPrice.getShippingValueWithTaxAmount();
		CommerceMoney totalWithTaxAmountCommerceMoney =
			commerceOrderPrice.getTotalWithTaxAmount();

		commerceOrder.setShippingAmount(shippingValueCommerceMoney.getPrice());
		commerceOrder.setSubtotal(subtotalCommerceMoney.getPrice());
		commerceOrder.setTaxAmount(taxValueCommerceMoney.getPrice());
		commerceOrder.setTotal(totalCommerceMoney.getPrice());

		if (subtotalWithTaxAmountCommerceMoney != null) {
			commerceOrder.setSubtotalWithTaxAmount(
				subtotalWithTaxAmountCommerceMoney.getPrice());
		}

		if (shippingValueWithTaxAmountCommerceMoney != null) {
			commerceOrder.setShippingWithTaxAmount(
				shippingValueWithTaxAmountCommerceMoney.getPrice());
		}

		if (totalWithTaxAmountCommerceMoney != null) {
			commerceOrder.setTotalWithTaxAmount(
				totalWithTaxAmountCommerceMoney.getPrice());
		}

		_setCommerceOrderSubtotalDiscountValue(
			commerceOrder, commerceOrderPrice.getSubtotalDiscountValue(),
			false);
		_setCommerceOrderShippingDiscountValue(
			commerceOrder, commerceOrderPrice.getShippingDiscountValue(),
			false);
		_setCommerceOrderTotalDiscountValue(
			commerceOrder, commerceOrderPrice.getTotalDiscountValue(), false);
		_setCommerceOrderSubtotalDiscountValue(
			commerceOrder,
			commerceOrderPrice.getSubtotalDiscountValueWithTaxAmount(), true);
		_setCommerceOrderShippingDiscountValue(
			commerceOrder,
			commerceOrderPrice.getShippingDiscountValueWithTaxAmount(), true);
		_setCommerceOrderTotalDiscountValue(
			commerceOrder,
			commerceOrderPrice.getTotalDiscountValueWithTaxAmount(), true);

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

	private void _setCommerceOrderShippingDiscountValue(
		CommerceOrder commerceOrder,
		CommerceDiscountValue commerceDiscountValue, boolean withTaxAmount) {

		BigDecimal discountAmount = BigDecimal.ZERO;
		BigDecimal discountPercentageLevel1 = BigDecimal.ZERO;
		BigDecimal discountPercentageLevel2 = BigDecimal.ZERO;
		BigDecimal discountPercentageLevel3 = BigDecimal.ZERO;
		BigDecimal discountPercentageLevel4 = BigDecimal.ZERO;

		if (commerceDiscountValue != null) {
			CommerceMoney discountAmountCommerceMoney =
				commerceDiscountValue.getDiscountAmount();

			discountAmount = discountAmountCommerceMoney.getPrice();

			BigDecimal[] percentages = commerceDiscountValue.getPercentages();

			if (percentages.length >= 1) {
				discountPercentageLevel1 = percentages[0];
			}

			if (percentages.length >= 2) {
				discountPercentageLevel2 = percentages[1];
			}

			if (percentages.length >= 3) {
				discountPercentageLevel3 = percentages[2];
			}

			if (percentages.length >= 4) {
				discountPercentageLevel4 = percentages[3];
			}
		}

		if (withTaxAmount) {
			commerceOrder.setShippingDiscountPercentageLevel1WithTaxAmount(
				discountPercentageLevel1);
			commerceOrder.setShippingDiscountPercentageLevel2WithTaxAmount(
				discountPercentageLevel2);
			commerceOrder.setShippingDiscountPercentageLevel3WithTaxAmount(
				discountPercentageLevel3);
			commerceOrder.setShippingDiscountPercentageLevel4WithTaxAmount(
				discountPercentageLevel4);
			commerceOrder.setShippingDiscountWithTaxAmount(discountAmount);
		}
		else {
			commerceOrder.setShippingDiscountAmount(discountAmount);
			commerceOrder.setShippingDiscountPercentageLevel1(
				discountPercentageLevel1);
			commerceOrder.setShippingDiscountPercentageLevel2(
				discountPercentageLevel2);
			commerceOrder.setShippingDiscountPercentageLevel3(
				discountPercentageLevel3);
			commerceOrder.setShippingDiscountPercentageLevel4(
				discountPercentageLevel4);
		}
	}

	private void _setCommerceOrderSubtotalDiscountValue(
		CommerceOrder commerceOrder,
		CommerceDiscountValue commerceDiscountValue, boolean withTaxAmount) {

		BigDecimal discountAmount = BigDecimal.ZERO;
		BigDecimal discountPercentageLevel1 = BigDecimal.ZERO;
		BigDecimal discountPercentageLevel2 = BigDecimal.ZERO;
		BigDecimal discountPercentageLevel3 = BigDecimal.ZERO;
		BigDecimal discountPercentageLevel4 = BigDecimal.ZERO;

		if (commerceDiscountValue != null) {
			CommerceMoney discountAmountCommerceMoney =
				commerceDiscountValue.getDiscountAmount();

			discountAmount = discountAmountCommerceMoney.getPrice();

			BigDecimal[] percentages = commerceDiscountValue.getPercentages();

			if ((percentages.length >= 1) && (percentages[0] != null)) {
				discountPercentageLevel1 = percentages[0];
			}

			if ((percentages.length >= 2) && (percentages[1] != null)) {
				discountPercentageLevel2 = percentages[1];
			}

			if ((percentages.length >= 3) && (percentages[2] != null)) {
				discountPercentageLevel3 = percentages[2];
			}

			if ((percentages.length >= 4) && (percentages[3] != null)) {
				discountPercentageLevel4 = percentages[3];
			}
		}

		if (withTaxAmount) {
			commerceOrder.setSubtotalDiscountPercentageLevel1WithTaxAmount(
				discountPercentageLevel1);
			commerceOrder.setSubtotalDiscountPercentageLevel2WithTaxAmount(
				discountPercentageLevel2);
			commerceOrder.setSubtotalDiscountPercentageLevel3WithTaxAmount(
				discountPercentageLevel3);
			commerceOrder.setSubtotalDiscountPercentageLevel4WithTaxAmount(
				discountPercentageLevel4);
			commerceOrder.setSubtotalDiscountWithTaxAmount(discountAmount);
		}
		else {
			commerceOrder.setSubtotalDiscountAmount(discountAmount);
			commerceOrder.setSubtotalDiscountPercentageLevel1(
				discountPercentageLevel1);
			commerceOrder.setSubtotalDiscountPercentageLevel2(
				discountPercentageLevel2);
			commerceOrder.setSubtotalDiscountPercentageLevel3(
				discountPercentageLevel3);
			commerceOrder.setSubtotalDiscountPercentageLevel4(
				discountPercentageLevel4);
		}
	}

	private void _setCommerceOrderTotalDiscountValue(
		CommerceOrder commerceOrder,
		CommerceDiscountValue commerceDiscountValue, boolean withTaxAmount) {

		BigDecimal discountAmount = BigDecimal.ZERO;
		BigDecimal discountPercentageLevel1 = BigDecimal.ZERO;
		BigDecimal discountPercentageLevel2 = BigDecimal.ZERO;
		BigDecimal discountPercentageLevel3 = BigDecimal.ZERO;
		BigDecimal discountPercentageLevel4 = BigDecimal.ZERO;

		if (commerceDiscountValue != null) {
			CommerceMoney discountAmountCommerceMoney =
				commerceDiscountValue.getDiscountAmount();

			discountAmount = discountAmountCommerceMoney.getPrice();

			BigDecimal[] percentages = commerceDiscountValue.getPercentages();

			if (percentages.length >= 1) {
				discountPercentageLevel1 = percentages[0];
			}

			if (percentages.length >= 2) {
				discountPercentageLevel2 = percentages[1];
			}

			if (percentages.length >= 3) {
				discountPercentageLevel3 = percentages[2];
			}

			if (percentages.length >= 4) {
				discountPercentageLevel4 = percentages[3];
			}
		}

		if (withTaxAmount) {
			commerceOrder.setTotalDiscountPercentageLevel1WithTaxAmount(
				discountPercentageLevel1);
			commerceOrder.setTotalDiscountPercentageLevel2WithTaxAmount(
				discountPercentageLevel2);
			commerceOrder.setTotalDiscountPercentageLevel3WithTaxAmount(
				discountPercentageLevel3);
			commerceOrder.setTotalDiscountPercentageLevel4WithTaxAmount(
				discountPercentageLevel4);
			commerceOrder.setTotalDiscountWithTaxAmount(discountAmount);
		}
		else {
			commerceOrder.setTotalDiscountAmount(discountAmount);
			commerceOrder.setTotalDiscountPercentageLevel1(
				discountPercentageLevel1);
			commerceOrder.setTotalDiscountPercentageLevel2(
				discountPercentageLevel2);
			commerceOrder.setTotalDiscountPercentageLevel3(
				discountPercentageLevel3);
			commerceOrder.setTotalDiscountPercentageLevel4(
				discountPercentageLevel4);
		}
	}

	@Reference
	private CommerceOrderItemHelper _commerceOrderItemHelper;

	@Reference
	private CommerceOrderPersistence _commerceOrderPersistence;

	@Reference
	private CommerceOrderPriceCalculation _commerceOrderPriceCalculation;

	@Reference
	private IndexerRegistry _indexerRegistry;

	@Reference
	private UserLocalService _userLocalService;

}