/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import ClayAlert from '@clayui/alert';
import ClayButton, {ClayButtonWithIcon} from '@clayui/button';
import ClayForm, {ClayCheckbox, ClayInput, ClaySelect} from '@clayui/form';
import ClayIcon from '@clayui/icon';
import ClayLayout from '@clayui/layout';
import {ClayTooltipProvider} from '@clayui/tooltip';
import React, {useState} from 'react';

interface IOption {
	label: string;
	value: string;
}

interface IProps {
	groups: IOption[];
	migrationIncomplete: boolean;
	portletNamespace: string;
	spaces: IOption[];
	startCMSMigrationURL: string;
	title: string;
}

const CONTENT_TYPES = [
	{
		label: Liferay.Language.get('web-content'),
		name: 'migrateJournalArticles',
	},
	{label: Liferay.Language.get('blogs'), name: 'migrateBlogsEntries'},
	{
		label: Liferay.Language.get('documents-and-media'),
		name: 'migrateFileEntries',
	},
	{
		defaultChecked: false,
		help: Liferay.Language.get('cms-migration-object-entries-help'),
		label: Liferay.Language.get('objects'),
		name: 'migrateObjectEntries',
	},
	{
		help: Liferay.Language.get('cms-migration-rewrite-pages-help'),
		label: Liferay.Language.get('cms-migration-rewrite-pages'),
		name: 'rewritePages',
	},
	{
		defaultChecked: false,
		help: Liferay.Language.get('cms-migration-repeatable-fields-help'),
		label: Liferay.Language.get('cms-migration-repeatable-fields'),
		name: 'migrateRepeatableFields',
	},
	{
		defaultChecked: false,
		help: Liferay.Language.get('cms-migration-skip-unchanged-entries-help'),
		label: Liferay.Language.get('cms-migration-skip-unchanged-entries'),
		name: 'skipUnchangedEntries',
	},
];

const CREATE_NEW_SPACE = '0';

const UncontrolledClayCheckbox = ClayCheckbox as unknown as React.ComponentType<
	Omit<React.ComponentProps<typeof ClayCheckbox>, 'checked'>
>;

export default function Configuration({
	groups,
	migrationIncomplete,
	portletNamespace,
	spaces,
	startCMSMigrationURL,
	title,
}: IProps) {
	const [depotEntryId, setDepotEntryId] = useState(
		spaces.length ? spaces[0].value : CREATE_NEW_SPACE
	);
	const [submitting, setSubmitting] = useState(false);

	return (
		<ClayLayout.Sheet>
			<form
				action={startCMSMigrationURL}
				method="post"
				onSubmit={() => setSubmitting(true)}
			>
				<ClayLayout.SheetHeader>
					<h2 className="sheet-title">{title}</h2>

					<div className="sheet-text">
						{Liferay.Language.get('cms-migration-description')}
					</div>
				</ClayLayout.SheetHeader>

				<ClayLayout.SheetSection>
					<ClayForm.Group>
						<label htmlFor={`${portletNamespace}sourceGroupId`}>
							{Liferay.Language.get('cms-migration-source-site')}

							<ClayIcon
								className="ml-1 reference-mark"
								symbol="asterisk"
							/>

							<span className="hide-accessible sr-only">
								{Liferay.Language.get('required')}
							</span>
						</label>

						<ClaySelect
							id={`${portletNamespace}sourceGroupId`}
							name={`${portletNamespace}sourceGroupId`}
							required
						>
							{groups.map(({label, value}) => (
								<ClaySelect.Option
									key={value}
									label={label}
									value={value}
								/>
							))}
						</ClaySelect>
					</ClayForm.Group>

					<ClayForm.Group>
						<label htmlFor={`${portletNamespace}depotEntryId`}>
							{Liferay.Language.get('cms-migration-target-space')}
						</label>

						<ClaySelect
							id={`${portletNamespace}depotEntryId`}
							name={`${portletNamespace}depotEntryId`}
							onChange={(event) =>
								setDepotEntryId(event.target.value)
							}
							value={depotEntryId}
						>
							<ClaySelect.Option
								label={Liferay.Language.get(
									'cms-migration-create-new-space'
								)}
								value={CREATE_NEW_SPACE}
							/>

							{spaces.map(({label, value}) => (
								<ClaySelect.Option
									key={value}
									label={label}
									value={value}
								/>
							))}
						</ClaySelect>
					</ClayForm.Group>

					{depotEntryId === CREATE_NEW_SPACE && (
						<ClayForm.Group>
							<label htmlFor={`${portletNamespace}spaceName`}>
								{Liferay.Language.get(
									'cms-migration-new-space-name'
								)}
							</label>

							<ClayInput
								id={`${portletNamespace}spaceName`}
								name={`${portletNamespace}spaceName`}
								type="text"
							/>
						</ClayForm.Group>
					)}
				</ClayLayout.SheetSection>

				<ClayTooltipProvider>
					<ClayLayout.SheetSection>
						<h3 className="sheet-subtitle">
							{Liferay.Language.get(
								'cms-migration-content-types'
							)}
						</h3>

						{CONTENT_TYPES.map(
							({defaultChecked, help, label, name}) => (
								<ClayForm.Group
									className="align-items-start d-flex"
									key={name}
								>
									<UncontrolledClayCheckbox
										containerProps={{
											className:
												'custom-control-primary mb-0',
										}}
										defaultChecked={defaultChecked ?? true}
										label={label}
										name={`${portletNamespace}${name}`}
										value="true"
									/>

									{help && (
										<ClayButtonWithIcon
											aria-label={help}
											className="component-action mt-n2"
											data-tooltip-align="top"
											displayType="unstyled"
											size="sm"
											style={
												{
													'--component-action-hover-background-color':
														'transparent',
												} as React.CSSProperties
											}
											symbol="question-circle-full"
											title={help}
										/>
									)}
								</ClayForm.Group>
							)
						)}

						<ClayForm.Group>
							<label
								htmlFor={`${portletNamespace}titleObjectFieldNames`}
							>
								{Liferay.Language.get(
									'cms-migration-title-object-field-names'
								)}
							</label>

							<ClayInput
								id={`${portletNamespace}titleObjectFieldNames`}
								name={`${portletNamespace}titleObjectFieldNames`}
								type="text"
							/>

							<ClayForm.FeedbackGroup>
								<ClayForm.Text>
									{Liferay.Language.get(
										'cms-migration-title-object-field-names-help'
									)}
								</ClayForm.Text>
							</ClayForm.FeedbackGroup>
						</ClayForm.Group>
					</ClayLayout.SheetSection>
				</ClayTooltipProvider>

				{migrationIncomplete && (
					<ClayAlert
						displayType="warning"
						title={Liferay.Language.get('warning')}
					>
						{Liferay.Language.get(
							'cms-migration-only-one-migration-can-run-at-a-time'
						)}
					</ClayAlert>
				)}

				<ClayLayout.SheetFooter>
					<ClayButton
						disabled={migrationIncomplete || submitting}
						type="submit"
					>
						{Liferay.Language.get('cms-migration-start')}
					</ClayButton>
				</ClayLayout.SheetFooter>
			</form>
		</ClayLayout.Sheet>
	);
}
