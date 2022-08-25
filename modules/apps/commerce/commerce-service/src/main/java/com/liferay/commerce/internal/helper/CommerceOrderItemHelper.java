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
import com.liferay.commerce.currency.model.CommerceCurrency;
import com.liferay.commerce.currency.model.CommerceMoney;
import com.liferay.commerce.currency.model.CommerceMoneyFactory;
import com.liferay.commerce.discount.CommerceDiscountValue;
import com.liferay.commerce.internal.util.CommercePriceConverterUtil;
import com.liferay.commerce.model.CommerceOrder;
import com.liferay.commerce.model.CommerceOrderItem;
import com.liferay.commerce.price.CommerceProductPrice;
import com.liferay.commerce.price.CommerceProductPriceCalculation;
import com.liferay.commerce.price.CommerceProductPriceImpl;
import com.liferay.commerce.price.CommerceProductPriceRequest;
import com.liferay.commerce.product.constants.CPConstants;
import com.liferay.commerce.product.model.CPInstance;
import com.liferay.commerce.product.option.CommerceOptionValue;
import com.liferay.commerce.product.option.CommerceOptionValueHelper;
import com.liferay.commerce.service.persistence.CommerceOrderItemPersistence;
import com.liferay.commerce.tax.CommerceTaxCalculation;
import com.liferay.portal.kernel.exception.PortalException;

import java.math.BigDecimal;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Hai Yu
 */
@Component(enabled = false, service = CommerceOrderItemHelper.class)
public class CommerceOrderItemHelper {

	public CommerceProductPrice getCommerceProductPrice(
			long cpDefinitionId, long cpInstanceId, String json, int quantity,
			CommerceContext commerceContext)
		throws PortalException {

		CommerceProductPriceRequest commerceProductPriceRequest =
			new CommerceProductPriceRequest();

		commerceProductPriceRequest.setCpInstanceId(cpInstanceId);
		commerceProductPriceRequest.setQuantity(quantity);
		commerceProductPriceRequest.setSecure(false);
		commerceProductPriceRequest.setCommerceContext(commerceContext);
		commerceProductPriceRequest.setCommerceOptionValues(
			_getStaticOptionValuesNotLinkedToSku(cpDefinitionId, json));
		commerceProductPriceRequest.setCalculateTax(true);

		return _commerceProductPriceCalculation.getCommerceProductPrice(
			commerceProductPriceRequest);
	}

	public BigDecimal getConvertedPrice(
			long cpInstanceId, BigDecimal price, CommerceOrder commerceOrder)
		throws PortalException {

		return CommercePriceConverterUtil.getConvertedPrice(
			commerceOrder.getGroupId(), cpInstanceId,
			commerceOrder.getBillingAddressId(),
			commerceOrder.getShippingAddressId(), price, false,
			_commerceTaxCalculation);
	}

	public CommerceProductPrice getStaticCommerceProductPrice(
			long cpInstanceId, int quantity, BigDecimal optionValuePrice,
			CommerceOrder commerceOrder, CommerceCurrency commerceCurrency)
		throws PortalException {

		CommerceProductPriceImpl commerceProductPriceImpl =
			new CommerceProductPriceImpl();

		if (optionValuePrice == null) {
			optionValuePrice = BigDecimal.ZERO;
		}

		commerceProductPriceImpl.setUnitPrice(
			_commerceMoneyFactory.create(commerceCurrency, optionValuePrice));
		commerceProductPriceImpl.setUnitPromoPrice(
			_commerceMoneyFactory.create(commerceCurrency, BigDecimal.ZERO));
		commerceProductPriceImpl.setUnitPromoPriceWithTaxAmount(
			_commerceMoneyFactory.create(commerceCurrency, BigDecimal.ZERO));

		BigDecimal unitPriceWithTaxAmount = optionValuePrice;

		BigDecimal finalPriceWithTaxAmount = optionValuePrice;

		if (cpInstanceId > 0) {
			unitPriceWithTaxAmount = getConvertedPrice(
				cpInstanceId, optionValuePrice, commerceOrder);

			optionValuePrice = optionValuePrice.multiply(
				BigDecimal.valueOf(quantity));

			finalPriceWithTaxAmount = getConvertedPrice(
				cpInstanceId, optionValuePrice, commerceOrder);
		}

		commerceProductPriceImpl.setUnitPriceWithTaxAmount(
			_commerceMoneyFactory.create(
				commerceCurrency, unitPriceWithTaxAmount));
		commerceProductPriceImpl.setFinalPrice(
			_commerceMoneyFactory.create(commerceCurrency, optionValuePrice));
		commerceProductPriceImpl.setFinalPriceWithTaxAmount(
			_commerceMoneyFactory.create(
				commerceCurrency, finalPriceWithTaxAmount));
		commerceProductPriceImpl.setCommerceDiscountValue(null);
		commerceProductPriceImpl.setQuantity(quantity);

		return commerceProductPriceImpl;
	}

	public boolean isStaticPriceType(Object value) {
		if (Objects.equals(
				value, CPConstants.PRODUCT_OPTION_PRICE_TYPE_STATIC)) {

			return true;
		}

		return false;
	}

	public void setCommerceOrderItemDiscountValue(
		CommerceOrderItem commerceOrderItem,
		CommerceDiscountValue commerceDiscountValue, boolean includeTax) {

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

		if (includeTax) {
			commerceOrderItem.setDiscountPercentageLevel1WithTaxAmount(
				discountPercentageLevel1);
			commerceOrderItem.setDiscountPercentageLevel2WithTaxAmount(
				discountPercentageLevel2);
			commerceOrderItem.setDiscountPercentageLevel3WithTaxAmount(
				discountPercentageLevel3);
			commerceOrderItem.setDiscountPercentageLevel4WithTaxAmount(
				discountPercentageLevel4);
			commerceOrderItem.setDiscountWithTaxAmount(discountAmount);
		}
		else {
			commerceOrderItem.setDiscountAmount(discountAmount);
			commerceOrderItem.setDiscountPercentageLevel1(
				discountPercentageLevel1);
			commerceOrderItem.setDiscountPercentageLevel2(
				discountPercentageLevel2);
			commerceOrderItem.setDiscountPercentageLevel3(
				discountPercentageLevel3);
			commerceOrderItem.setDiscountPercentageLevel4(
				discountPercentageLevel4);
		}
	}

	public void setCommerceOrderItemPrice(
		CommerceOrderItem commerceOrderItem,
		CommerceProductPrice commerceProductPrice) {

		CommerceMoney unitPriceCommerceMoney =
			commerceProductPrice.getUnitPrice();

		commerceOrderItem.setUnitPrice(unitPriceCommerceMoney.getPrice());

		BigDecimal promoPrice = BigDecimal.ZERO;
		BigDecimal promoPriceWithTaxAmount = BigDecimal.ZERO;

		CommerceMoney unitPromoPriceCommerceMoney =
			commerceProductPrice.getUnitPromoPrice();

		if (!unitPromoPriceCommerceMoney.isEmpty()) {
			promoPrice = unitPromoPriceCommerceMoney.getPrice();
		}

		CommerceMoney unitPromoPriceWithTaxAmountCommerceMoney =
			commerceProductPrice.getUnitPromoPriceWithTaxAmount();

		if (!unitPromoPriceWithTaxAmountCommerceMoney.isEmpty()) {
			promoPriceWithTaxAmount =
				unitPromoPriceWithTaxAmountCommerceMoney.getPrice();
		}

		commerceOrderItem.setPromoPrice(promoPrice);
		commerceOrderItem.setPromoPriceWithTaxAmount(promoPriceWithTaxAmount);

		CommerceMoney finalPriceCommerceMoney =
			commerceProductPrice.getFinalPrice();

		commerceOrderItem.setFinalPrice(finalPriceCommerceMoney.getPrice());

		CommerceMoney unitPriceWithTaxAmountCommerceMoney =
			commerceProductPrice.getUnitPriceWithTaxAmount();

		if (unitPriceWithTaxAmountCommerceMoney != null) {
			commerceOrderItem.setUnitPriceWithTaxAmount(
				unitPriceWithTaxAmountCommerceMoney.getPrice());
		}

		CommerceMoney finalPriceWithTaxAmountCommerceMoney =
			commerceProductPrice.getFinalPriceWithTaxAmount();

		if (finalPriceWithTaxAmountCommerceMoney != null) {
			commerceOrderItem.setFinalPriceWithTaxAmount(
				finalPriceWithTaxAmountCommerceMoney.getPrice());
		}

		commerceOrderItem.setCommercePriceListId(
			commerceProductPrice.getCommercePriceListId());
	}

	public CommerceOrderItem updateCommerceOrderItemPrice(
			long commerceOrderItemId, CommerceContext commerceContext)
		throws PortalException {

		CommerceOrderItem commerceOrderItem =
			_commerceOrderItemPersistence.findByPrimaryKey(commerceOrderItemId);

		if (commerceOrderItem.isManuallyAdjusted() ||
			(commerceOrderItem.getParentCommerceOrderItemId() != 0)) {

			return commerceOrderItem;
		}

		CPInstance cpInstance = commerceOrderItem.fetchCPInstance();

		if (cpInstance == null) {
			return commerceOrderItem;
		}

		List<CommerceOrderItem> childCommerceOrderItems =
			_commerceOrderItemPersistence.findByParentCommerceOrderItemId(
				commerceOrderItemId);

		for (CommerceOrderItem childCommerceOrderItem :
				childCommerceOrderItems) {

			CommerceOptionValue commerceOptionValue =
				_commerceOptionValueHelper.toCommerceOptionValue(
					childCommerceOrderItem.getJson());

			if (!isStaticPriceType(commerceOptionValue.getPriceType())) {
				_setCommerceOrderItemPrice(
					childCommerceOrderItem, null, commerceContext);
			}
			else {
				_setCommerceOrderItemPrice(
					childCommerceOrderItem,
					getStaticCommerceProductPrice(
						commerceOptionValue.getCPInstanceId(),
						childCommerceOrderItem.getQuantity(),
						commerceOptionValue.getPrice(),
						childCommerceOrderItem.getCommerceOrder(),
						commerceContext.getCommerceCurrency()),
					commerceContext);
			}

			_commerceOrderItemPersistence.update(childCommerceOrderItem);
		}

		commerceOrderItem = _commerceOrderItemPersistence.findByPrimaryKey(
			commerceOrderItemId);

		_setCommerceOrderItemPrice(commerceOrderItem, null, commerceContext);

		return _commerceOrderItemPersistence.update(commerceOrderItem);
	}

	private List<CommerceOptionValue> _getStaticOptionValuesNotLinkedToSku(
			long cpDefinitionId, String jsonArrayString)
		throws PortalException {

		List<CommerceOptionValue> commerceOptionValues =
			_commerceOptionValueHelper.getCPDefinitionCommerceOptionValues(
				cpDefinitionId, jsonArrayString);

		Stream<CommerceOptionValue> commerceOptionValuesStream =
			commerceOptionValues.stream();

		Stream<CommerceOptionValue> commerceOptionValuesFiltered =
			commerceOptionValuesStream.filter(
				commerceOptionValue ->
					isStaticPriceType(commerceOptionValue.getPriceType()) &&
					(commerceOptionValue.getCPInstanceId() == 0));

		return commerceOptionValuesFiltered.collect(Collectors.toList());
	}

	private void _setCommerceOrderItemPrice(
			CommerceOrderItem commerceOrderItem,
			CommerceProductPrice commerceProductPrice,
			CommerceContext commerceContext)
		throws PortalException {

		CPInstance cpInstance = commerceOrderItem.fetchCPInstance();

		if ((cpInstance == null) || commerceOrderItem.isManuallyAdjusted()) {
			return;
		}

		if (commerceProductPrice == null) {
			commerceProductPrice = getCommerceProductPrice(
				commerceOrderItem.getCPDefinitionId(),
				commerceOrderItem.getCPInstanceId(),
				commerceOrderItem.getJson(), commerceOrderItem.getQuantity(),
				commerceContext);
		}

		setCommerceOrderItemPrice(commerceOrderItem, commerceProductPrice);

		setCommerceOrderItemDiscountValue(
			commerceOrderItem, commerceProductPrice.getDiscountValue(), false);

		setCommerceOrderItemDiscountValue(
			commerceOrderItem,
			commerceProductPrice.getDiscountValueWithTaxAmount(), true);
	}

	@Reference
	private CommerceMoneyFactory _commerceMoneyFactory;

	@Reference
	private CommerceOptionValueHelper _commerceOptionValueHelper;

	@Reference
	private CommerceOrderItemPersistence _commerceOrderItemPersistence;

	@Reference
	private CommerceProductPriceCalculation _commerceProductPriceCalculation;

	@Reference
	private CommerceTaxCalculation _commerceTaxCalculation;

}