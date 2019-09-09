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

import React, {useState, useEffect} from 'react';
import Button from '../../components/button/Button.es';
import ControlMenu from '../../components/control-menu/ControlMenu.es';
import {UpperToolbarInput} from '../../components/upper-toolbar/UpperToolbar.es';
import {getItem, addItem, updateItem} from '../../utils/client.es';

export default ({
	history,
	match: {
		params: {dataDefinitionId, appId}
	}
}) => {
	const [state, setState] = useState({
		app: {
			name: {
				en_US: ''
			}
		},
		dataDefinition: {}
	});

	let title = Liferay.Language.get('new-app');

	if (appId) {
		title = Liferay.Language.get('edit-app');
	}

	useEffect(() => {
		const getDataDefinition = getItem(
			`/o/data-engine/v1.0/data-definitions/${dataDefinitionId}`
		);

		if (appId) {
			const getApp = getItem(`/apps/${appId}`);

			Promise.all([getApp, getDataDefinition]).then(
				([app, dataDefinition]) => {
					setState({
						app,
						dataDefinition
					});
				}
			);
		} else {
			getDataDefinition.then(dataDefinition => {
				setState(prevState => ({
					...prevState,
					dataDefinition
				}));
			});
		}
	}, [appId, dataDefinitionId]);

	const handleBack = () => {
		history.push(`/custom-object/${dataDefinitionId}/apps`);
	};

	const handleSubmit = () => {
		const {app} = state;

		if (app.name.en_US === '') {
			return;
		}

		if (appId) {
			updateItem(`/o/app-builder/v1.0/apps/${appId}`, app).then(
				handleBack
			);
		} else {
			addItem(
				`/o/app-builder/v1.0/data-definitions/${dataDefinitionId}/apps`,
				app
			).then(handleBack);
		}
	};

	const handleAppNameChange = event => {
		const name = event.target.value;

		setState(prevState => ({
			...prevState,
			app: {
				...prevState.app,
				name: {
					en_US: name
				}
			}
		}));
	};

	return (
		<>
			<ControlMenu backURL="../" title={title} />

			<div className="container-fluid container-fluid-max-lg mt-4">
				<div className="card card-root">
					<div className="card-header align-items-center d-flex justify-content-between bg-transparent">
						<UpperToolbarInput
							onInput={handleAppNameChange}
							placeholder={Liferay.Language.get('untitled-app')}
							value={state.app.en_US}
						/>
					</div>

					<h4 className="card-divider"></h4>

					<div className="card-footer bg-transparent">
						<div className="autofit-row">
							<div className="col-md-4">
								<Button
									displayType="secondary"
									onClick={handleBack}
								>
									{Liferay.Language.get('cancel')}
								</Button>
							</div>
							<div className="col-md-4 offset-md-4 text-right">
								<Button
									displayType="secondary"
									onClick={handleSubmit}
								>
									{Liferay.Language.get('save')}
								</Button>
							</div>
						</div>
					</div>
				</div>
			</div>
		</>
	);
};
