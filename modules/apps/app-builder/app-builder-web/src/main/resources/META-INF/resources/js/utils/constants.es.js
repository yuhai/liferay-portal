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

import moment from 'moment';
import {deleteItem} from './client.es';

const confirmDelete = endpoint => item =>
	new Promise((resolve, reject) => {
		const confirmed = confirm(
			Liferay.Language.get('are-you-sure-you-want-to-delete-this')
		);

		if (confirmed) {
			deleteItem(endpoint + item.id)
				.then(() => resolve(true))
				.catch(error => reject(error));
		} else {
			resolve(false);
		}
	});

export const APPS = {
	ACTIONS: [
		{
			name: Liferay.Language.get('delete'),
			callback: confirmDelete('/o/app-builder/v1.0/apps/')
		}
	],
	COLUMNS: [
		{
			name: Liferay.Language.get('name')
		},
		{
			type: Liferay.Language.get('deployed-as')
		},
		{
			dateCreated: Liferay.Language.get('create-date')
		},
		{
			dateModified: Liferay.Language.get('modified-date')
		},
		{
			status: Liferay.Language.get('status')
		}
	],
	EMPTY_STATE: {
		title: Liferay.Language.get('there-are-no-deployments-yet'),
		description: Liferay.Language.get(
			'select-the-form-and-table-view-you-want-and-deploy-your-app-as-a-widget-standalone-or-place-it-in-the-product-menu'
		)
	},
	ENDPOINT: dataDefinitionId =>
		`/o/app-builder/v1.0/data-definitions/${dataDefinitionId}/apps`,
	FORMATTER: items =>
		items.map(item => ({
			dateCreated: moment(item.dateCreated).fromNow(),
			dateModified: moment(item.dateModified).fromNow(),
			id: item.id,
			name: item.name.en_US,
			status: item.settings.status,
			type: item.settings.type
		}))
};

export const CUSTOM_OBJECTS = {
	ACTIONS: [
		{
			name: Liferay.Language.get('form-views'),
			link: item => `#/custom-object/${item.id}/form-views`
		},
		{
			name: Liferay.Language.get('table-views'),
			link: item => `#/custom-object/${item.id}/table-views`
		},
		{
			name: Liferay.Language.get('deployments'),
			link: item => `#/custom-object/${item.id}/deployments`
		},
		{
			name: 'divider'
		},
		{
			name: Liferay.Language.get('delete'),
			callback: confirmDelete('/o/data-engine/v1.0/data-definitions/')
		}
	],
	COLUMNS: [
		{
			name: Liferay.Language.get('name')
		},
		{
			dateCreated: Liferay.Language.get('create-date')
		},
		{
			dateModified: Liferay.Language.get('modified-date')
		}
	],
	EMPTY_STATE: {
		title: Liferay.Language.get('there-are-no-custom-objects-yet'),
		description: Liferay.Language.get(
			'custom-objects-define-the-types-of-data-your-business-application-needs'
		)
	},
	ENDPOINT: `/o/data-engine/v1.0/sites/${Liferay.ThemeDisplay.getCompanyGroupId()}/data-definitions`,
	FORMATTER: items =>
		items.map(item => ({
			dateCreated: moment(item.dateCreated).fromNow(),
			dateModified: moment(item.dateModified).fromNow(),
			id: item.id,
			name: {
				name: item.name.en_US,
				link: item => `/custom-object/${item.id}/form-views`
			}
		}))
};

export const FORM_VIEWS = {
	ACTIONS: [
		{
			name: Liferay.Language.get('delete'),
			callback: confirmDelete('/o/data-engine/v1.0/data-layouts/')
		}
	],
	COLUMNS: [
		{
			name: Liferay.Language.get('name')
		},
		{
			dateCreated: Liferay.Language.get('create-date')
		},
		{
			dateModified: Liferay.Language.get('modified-date')
		}
	],
	EMPTY_STATE: {
		title: Liferay.Language.get('there-are-no-form-views-yet'),
		description: Liferay.Language.get(
			'create-one-or-more-forms-to-display-the-data-held-in-your-data-object'
		)
	},
	ENDPOINT: dataDefinitionId =>
		`/o/data-engine/v1.0/data-definitions/${dataDefinitionId}/data-layouts`,
	FORMATTER: items =>
		items.map(item => ({
			dateCreated: moment(item.dateCreated).fromNow(),
			dateModified: moment(item.dateModified).fromNow(),
			id: item.id,
			name: item.name.en_US
		}))
};

export const TABLE_VIEWS = {
	ACTIONS: [
		{
			name: Liferay.Language.get('delete'),
			callback: confirmDelete('/o/data-engine/v1.0/data-layouts-views/')
		}
	],
	COLUMNS: [
		{
			name: Liferay.Language.get('name')
		},
		{
			dateCreated: Liferay.Language.get('create-date')
		},
		{
			dateModified: Liferay.Language.get('modified-date')
		}
	],
	EMPTY_STATE: {
		title: Liferay.Language.get('there-are-no-table-views-yet'),
		description: Liferay.Language.get(
			'create-one-or-more-tables-to-display-the-data-held-in-your-data-object'
		)
	},
	ENDPOINT: dataDefinitionId =>
		`/o/data-engine/v1.0/data-definitions/${dataDefinitionId}/data-layouts-views`,
	FORMATTER: items =>
		items.map(item => ({
			dateCreated: moment(item.dateCreated).fromNow(),
			dateModified: moment(item.dateModified).fromNow(),
			id: item.id,
			name: item.name.en_US
		}))
};
