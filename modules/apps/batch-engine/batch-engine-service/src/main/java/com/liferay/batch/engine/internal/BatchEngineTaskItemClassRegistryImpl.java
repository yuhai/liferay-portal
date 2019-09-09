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

package com.liferay.batch.engine.internal;

import com.liferay.batch.engine.BatchEngineTaskItemClassRegistry;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.osgi.service.component.annotations.Component;

/**
 * @author Shuyang Zhou
 */
@Component(service = BatchEngineTaskItemClassRegistry.class)
public class BatchEngineTaskItemClassRegistryImpl
	implements BatchEngineTaskItemClassRegistry {

	@Override
	public Class<?> get(String itemClassName) {
		return _itemClasses.get(itemClassName);
	}

	@Override
	public void register(Class<?>... itemClasses) {
		for (Class<?> itemClass : itemClasses) {
			Class<?> oldItemClass = _itemClasses.put(
				itemClass.getName(), itemClass);

			if ((oldItemClass != null) && _log.isWarnEnabled()) {
				_log.warn(
					StringBundler.concat(
						oldItemClass, " from ", oldItemClass.getClassLoader(),
						" is replaced with ", itemClass, "from ",
						itemClass.getClassLoader()));
			}
		}
	}

	@Override
	public void unregister(Class<?>... itemClasses) {
		for (Class<?> itemClass : itemClasses) {
			_itemClasses.remove(itemClass.getName(), itemClass);
		}
	}

	private static final Log _log = LogFactoryUtil.getLog(
		BatchEngineTaskItemClassRegistryImpl.class);

	private final Map<String, Class<?>> _itemClasses =
		new ConcurrentHashMap<>();

}