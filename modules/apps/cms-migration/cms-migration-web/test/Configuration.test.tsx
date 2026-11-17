/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

// eslint-disable-next-line @liferay/portal/no-cross-module-deep-import
import {checkAccessibility} from '@liferay/layout-js-components-web/test/__lib__/index';
import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';

import '@testing-library/jest-dom';

import Configuration from '../src/main/resources/META-INF/resources/js/Configuration';

const PORTLET_NAMESPACE = '_portletNamespace_';

const GROUPS = [
	{label: 'Site 1', value: '1'},
	{label: 'Site 2', value: '2'},
];

const SPACES = [
	{label: 'Space 1', value: '10'},
	{label: 'Space 2', value: '20'},
];

function renderConfiguration({
	migrationIncomplete = false,
	spaces = SPACES,
} = {}) {
	return render(
		<Configuration
			groups={GROUPS}
			migrationIncomplete={migrationIncomplete}
			portletNamespace={PORTLET_NAMESPACE}
			spaces={spaces}
			startCMSMigrationURL="http://localhost/start"
			title="CMS Migration"
		/>
	);
}

async function submitConfiguration() {
	const button = screen.getByRole<HTMLButtonElement>('button', {
		name: 'cms-migration-start',
	});

	const {form} = button;

	if (!form) {
		throw new Error('The start button is not inside a form');
	}

	const onSubmit = jest.fn((event: Event) => event.preventDefault());

	form.addEventListener('submit', onSubmit);

	await userEvent.click(button);

	return {button, form, onSubmit};
}

describe('Configuration', () => {
	it('shows the title and the description of the migration', () => {
		renderConfiguration();

		expect(
			screen.getByRole('heading', {name: 'CMS Migration'})
		).toBeInTheDocument();
		expect(
			screen.getByText('cms-migration-description')
		).toBeInTheDocument();
	});

	it('posts the namespaced configuration to the start migration URL', async () => {
		renderConfiguration();

		const {form, onSubmit} = await submitConfiguration();

		expect(onSubmit).toHaveBeenCalledTimes(1);
		expect(form).toHaveAttribute('action', 'http://localhost/start');
		expect(form).toHaveAttribute('method', 'post');
		expect(form).toHaveFormValues({
			[`${PORTLET_NAMESPACE}depotEntryId`]: '10',
			[`${PORTLET_NAMESPACE}migrateBlogsEntries`]: true,
			[`${PORTLET_NAMESPACE}migrateFileEntries`]: true,
			[`${PORTLET_NAMESPACE}migrateJournalArticles`]: true,
			[`${PORTLET_NAMESPACE}migrateObjectEntries`]: false,
			[`${PORTLET_NAMESPACE}migrateRepeatableFields`]: false,
			[`${PORTLET_NAMESPACE}rewritePages`]: true,
			[`${PORTLET_NAMESPACE}skipUnchangedEntries`]: false,
			[`${PORTLET_NAMESPACE}sourceGroupId`]: '1',
			[`${PORTLET_NAMESPACE}titleObjectFieldNames`]: '',
		});
	});

	it('posts the selected source site', async () => {
		renderConfiguration();

		await userEvent.selectOptions(
			screen.getByLabelText(/cms-migration-source-site/),
			'2'
		);

		const {form, onSubmit} = await submitConfiguration();

		expect(onSubmit).toHaveBeenCalledTimes(1);
		expect(form).toHaveFormValues({
			[`${PORTLET_NAMESPACE}sourceGroupId`]: '2',
		});
	});

	it('lists the source sites and the target spaces', () => {
		renderConfiguration();

		expect(
			screen.getByLabelText(/cms-migration-source-site/)
		).toBeRequired();
		expect(screen.getByRole('option', {name: 'Site 2'})).toHaveValue('2');
		expect(
			screen.getByRole('option', {name: 'cms-migration-create-new-space'})
		).toHaveValue('0');
		expect(screen.getByRole('option', {name: 'Space 2'})).toHaveValue('20');
	});

	it('asks for the new space name only when creating a new space', async () => {
		renderConfiguration();

		expect(
			screen.queryByLabelText('cms-migration-new-space-name')
		).not.toBeInTheDocument();

		await userEvent.selectOptions(
			screen.getByLabelText('cms-migration-target-space'),
			'0'
		);

		expect(
			screen.getByLabelText('cms-migration-new-space-name')
		).toHaveAttribute('name', `${PORTLET_NAMESPACE}spaceName`);

		await userEvent.selectOptions(
			screen.getByLabelText('cms-migration-target-space'),
			'20'
		);

		expect(
			screen.queryByLabelText('cms-migration-new-space-name')
		).not.toBeInTheDocument();
	});

	it('posts the name of the new space', async () => {
		renderConfiguration();

		await userEvent.selectOptions(
			screen.getByLabelText('cms-migration-target-space'),
			'0'
		);
		await userEvent.type(
			screen.getByLabelText('cms-migration-new-space-name'),
			'New Space'
		);

		const {form, onSubmit} = await submitConfiguration();

		expect(onSubmit).toHaveBeenCalledTimes(1);
		expect(form).toHaveFormValues({
			[`${PORTLET_NAMESPACE}depotEntryId`]: '0',
			[`${PORTLET_NAMESPACE}spaceName`]: 'New Space',
		});
	});

	it('creates a new space by default when there are no spaces', () => {
		renderConfiguration({spaces: []});

		expect(screen.getByLabelText('cms-migration-target-space')).toHaveValue(
			'0'
		);
		expect(
			screen.getByLabelText('cms-migration-new-space-name')
		).toBeInTheDocument();
	});

	it('leaves out the content types that are unchecked', async () => {
		renderConfiguration();

		await userEvent.click(screen.getByLabelText('blogs'));

		const {form} = screen.getByRole<HTMLButtonElement>('button', {
			name: 'cms-migration-start',
		});

		expect(form).toHaveFormValues({
			[`${PORTLET_NAMESPACE}migrateBlogsEntries`]: false,
		});
	});

	it('posts the repeatable fields option once it is checked', async () => {
		renderConfiguration();

		await userEvent.click(
			screen.getByLabelText('cms-migration-repeatable-fields')
		);

		const {form} = screen.getByRole<HTMLButtonElement>('button', {
			name: 'cms-migration-start',
		});

		expect(form).toHaveFormValues({
			[`${PORTLET_NAMESPACE}migrateRepeatableFields`]: true,
		});
	});

	it('posts the skip unchanged entries option once it is checked', async () => {
		renderConfiguration();

		await userEvent.click(
			screen.getByLabelText('cms-migration-skip-unchanged-entries')
		);

		const {form} = screen.getByRole<HTMLButtonElement>('button', {
			name: 'cms-migration-start',
		});

		expect(form).toHaveFormValues({
			[`${PORTLET_NAMESPACE}skipUnchangedEntries`]: true,
		});
	});

	it('explains how the pages are updated', () => {
		renderConfiguration();

		expect(
			screen.getByTitle('cms-migration-rewrite-pages-help')
		).toBeInTheDocument();
	});

	it('explains how the repeatable fields are migrated', () => {
		renderConfiguration();

		expect(
			screen.getByTitle('cms-migration-repeatable-fields-help')
		).toBeInTheDocument();
	});

	it('disables the start button once the migration is submitted', async () => {
		renderConfiguration();

		expect(
			screen.getByRole('button', {name: 'cms-migration-start'})
		).toBeEnabled();

		const {button} = await submitConfiguration();

		expect(button).toBeDisabled();
	});

	it('warns that only one migration runs at a time and disables the start button', () => {
		renderConfiguration({migrationIncomplete: true});

		expect(screen.getByRole('alert')).toHaveTextContent(
			'cms-migration-only-one-migration-can-run-at-a-time'
		);
		expect(
			screen.getByRole('button', {name: 'cms-migration-start'})
		).toBeDisabled();
	});

	it('does not warn when no migration is incomplete', () => {
		renderConfiguration();

		expect(screen.queryByRole('alert')).not.toBeInTheDocument();
	});

	it('has no accessibility violations with the warning', async () => {
		const {container} = renderConfiguration({migrationIncomplete: true});

		await checkAccessibility({bestPractices: true, context: container});
	});

	it('has no accessibility violations without the warning', async () => {
		const {container} = renderConfiguration();

		await checkAccessibility({bestPractices: true, context: container});
	});
});
