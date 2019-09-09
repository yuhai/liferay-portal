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

module.exports = {
	devtool: 'inline-source-map',
	mode: 'development',
	module: {
		rules: [
			{
				exclude: /(node_modules)/,
				test: /\.js$/,
				use: {
					loader: 'babel-loader',
					options: {
						compact: false
					}
				}
			},
			{
				exclude: /(test|node_modules)/,
				test: /\.js$/,
				use: {
					loader: 'istanbul-instrumenter-loader',
					query: {
						esModules: true
					}
				}
			}
		]
	}
};
