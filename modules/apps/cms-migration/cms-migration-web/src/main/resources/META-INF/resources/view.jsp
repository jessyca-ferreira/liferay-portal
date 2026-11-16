<%--
/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */
--%>

<%@ include file="/init.jsp" %>

<c:choose>
	<c:when test='<%= FeatureFlagManagerUtil.isEnabled("ECU-21107") %>'>
		<react:component
			module="{CMSMigration} from cms-migration-web"
			props='<%=
				HashMapBuilder.<String, Object>put(
					"title", portletDisplay.getTitle()
				).build()
			%>'
		/>
	</c:when>
	<c:otherwise>

		<%
		CMSMigrationDisplayContext cmsMigrationDisplayContext = (CMSMigrationDisplayContext)request.getAttribute(WebKeys.PORTLET_DISPLAY_CONTEXT);

		Format dateTimeFormat = FastDateFormatFactoryUtil.getDateTime(locale, timeZone);
		boolean inProgress = false;
		%>

		<clay:container-fluid
			cssClass="container-form-lg"
		>
			<liferay-ui:success key="migrationStarted" message="cms-migration-started" />

			<liferay-ui:error exception="<%= DepotEntryNameException.class %>" message="please-enter-a-valid-name" />
			<liferay-ui:error exception="<%= Exception.class %>" message="an-unexpected-error-occurred" />

			<clay:sheet>
				<portlet:actionURL name="/cms_migration/start_cms_migration" var="startCMSMigrationURL" />

				<aui:form action="<%= startCMSMigrationURL %>" method="post" name="fm">
					<clay:sheet-header>
						<h2 class="sheet-title">
							<%= HtmlUtil.escape(portletDisplay.getTitle()) %>
						</h2>

						<div class="sheet-text">
							<liferay-ui:message key="cms-migration-description" />
						</div>
					</clay:sheet-header>

					<clay:sheet-section>
						<aui:select label="cms-migration-source-site" name="sourceGroupId" required="<%= true %>">

							<%
							for (Group group : cmsMigrationDisplayContext.getGroups()) {
							%>

								<aui:option label="<%= HtmlUtil.escape(group.getDescriptiveName(locale)) %>" value="<%= group.getGroupId() %>" />

							<%
							}
							%>

						</aui:select>

						<%
						List<DepotEntry> spaces = cmsMigrationDisplayContext.getSpaces();
						%>

						<aui:select label="cms-migration-target-space" name="depotEntryId">
							<aui:option label="cms-migration-create-new-space" selected="<%= spaces.isEmpty() %>" value="0" />

							<%
							for (int i = 0; i < spaces.size(); i++) {
								DepotEntry depotEntry = spaces.get(i);

								Group depotGroup = depotEntry.getGroup();
							%>

								<aui:option label="<%= HtmlUtil.escape(depotGroup.getDescriptiveName(locale)) %>" selected="<%= i == 0 %>" value="<%= depotEntry.getDepotEntryId() %>" />

							<%
							}
							%>

						</aui:select>

						<div class="<%= spaces.isEmpty() ? StringPool.BLANK : "d-none" %>" id="<portlet:namespace />spaceNameContainer">
							<aui:input label="cms-migration-new-space-name" name="spaceName" type="text" />
						</div>
					</clay:sheet-section>

					<clay:sheet-section>
						<h3 class="sheet-subtitle">
							<liferay-ui:message key="cms-migration-content-types" />
						</h3>

						<aui:input checked="<%= true %>" label="web-content" name="migrateJournalArticles" type="checkbox" />

						<aui:input checked="<%= true %>" label="blogs" name="migrateBlogsEntries" type="checkbox" />

						<aui:input checked="<%= true %>" label="documents-and-media" name="migrateFileEntries" type="checkbox" />

						<aui:input checked="<%= true %>" helpMessage="cms-migration-rewrite-pages-help" label="cms-migration-rewrite-pages" name="rewritePages" type="checkbox" />

						<aui:input helpMessage="cms-migration-repeatable-fields-help" label="cms-migration-repeatable-fields" name="migrateRepeatableFields" type="checkbox" />
					</clay:sheet-section>

					<clay:sheet-footer>
						<aui:button type="submit" value="cms-migration-start" />
					</clay:sheet-footer>
				</aui:form>
			</clay:sheet>

			<clay:sheet>
				<clay:sheet-header>
					<h2 class="sheet-title">
						<liferay-ui:message key="cms-migration-migrations" />
					</h2>
				</clay:sheet-header>

				<clay:sheet-section>

					<%
					List<BackgroundTask> backgroundTasks = cmsMigrationDisplayContext.getBackgroundTasks();
					%>

					<c:if test="<%= backgroundTasks.isEmpty() %>">
						<div class="text-secondary">
							<liferay-ui:message key="cms-migration-no-migrations" />
						</div>
					</c:if>

					<%
					for (BackgroundTask backgroundTask : backgroundTasks) {
						CMSMigrationReport cmsMigrationReport = cmsMigrationDisplayContext.getCMSMigrationReport(backgroundTask);

						if (backgroundTask.isInProgress()) {
							inProgress = true;
						}
					%>

						<div class="border-bottom mb-3 pb-3">
							<div class="autofit-row">
								<div class="autofit-col autofit-col-expand">
									<h4>
										<%= HtmlUtil.escape(backgroundTask.getName()) %>
									</h4>

									<div class="text-secondary">
										<%= dateTimeFormat.format(backgroundTask.getCreateDate()) %>
									</div>
								</div>

								<div class="autofit-col">
									<clay:label
										displayType="<%= BackgroundTaskConstants.getStatusCssClass(backgroundTask.getStatus()) %>"
										label="<%= LanguageUtil.get(request, backgroundTask.getStatusLabel()) %>"
									/>
								</div>

								<c:if test="<%= backgroundTask.isCompleted() %>">
									<div class="autofit-col">
										<portlet:actionURL name="/cms_migration/delete_cms_migration" var="deleteCMSMigrationURL">
											<portlet:param name="backgroundTaskId" value="<%= String.valueOf(backgroundTask.getBackgroundTaskId()) %>" />
											<portlet:param name="redirect" value="<%= themeDisplay.getURLCurrent() %>" />
										</portlet:actionURL>

										<clay:link
											displayType="secondary"
											href="<%= deleteCMSMigrationURL %>"
											label="delete"
											small="<%= true %>"
											type="button"
										/>
									</div>
								</c:if>
							</div>

							<c:if test="<%= backgroundTask.isInProgress() %>">

								<%
								JSONObject statusJSONObject = cmsMigrationDisplayContext.getStatusJSONObject(backgroundTask);
								%>

								<div class="mt-2">
									<liferay-ui:message key='<%= "cms-migration-" + statusJSONObject.getString("phase") %>' />

									<span class="text-secondary"><%= HtmlUtil.escape(statusJSONObject.getString("itemTitle")) %></span>
								</div>

								<clay:progressbar
									value="<%= cmsMigrationDisplayContext.getProgress(backgroundTask) %>"
								/>
							</c:if>

							<c:if test="<%= backgroundTask.isCompleted() %>">
								<ul class="list-inline mt-2">

									<%
									for (Map.Entry<String, Long> entry :
											cmsMigrationReport.getCounts(
											).entrySet()) {
									%>

										<li class="list-inline-item">
											<strong><%= entry.getValue() %></strong> <liferay-ui:message key='<%= "cms-migration-" + entry.getKey() %>' />
										</li>

									<%
									}
									%>

								</ul>

								<c:if test="<%= !cmsMigrationReport.getErrors().isEmpty() %>">
									<details class="mt-2">
										<summary class="text-danger">
											<liferay-ui:message arguments="<%= cmsMigrationReport.getErrors().size() %>" key="cms-migration-x-errors" />
										</summary>

										<ul>

											<%
											for (String error : cmsMigrationReport.getErrors()) {
											%>

												<li><%= HtmlUtil.escape(error) %></li>

											<%
											}
											%>

										</ul>
									</details>
								</c:if>

								<c:if test="<%= !cmsMigrationReport.getWarnings().isEmpty() %>">
									<details class="mt-2">
										<summary class="text-warning">
											<liferay-ui:message arguments="<%= cmsMigrationReport.getWarnings().size() %>" key="cms-migration-x-warnings" />
										</summary>

										<ul>

											<%
											for (String warning : cmsMigrationReport.getWarnings()) {
											%>

												<li><%= HtmlUtil.escape(warning) %></li>

											<%
											}
											%>

										</ul>
									</details>
								</c:if>
							</c:if>
						</div>

					<%
					}
					%>

				</clay:sheet-section>
			</clay:sheet>
		</clay:container-fluid>

		<aui:script>
			const depotEntryIdSelect = document.getElementById(
				'<portlet:namespace />depotEntryId'
			);
			const spaceNameContainer = document.getElementById(
				'<portlet:namespace />spaceNameContainer'
			);

			depotEntryIdSelect.addEventListener('change', () => {
				spaceNameContainer.classList.toggle(
					'd-none',
					depotEntryIdSelect.value !== '0'
				);
			});
		</aui:script>

		<c:if test="<%= inProgress %>">
			<aui:script>
				setTimeout(() => {
					window.location.reload();
				}, 5000);
			</aui:script>
		</c:if>
	</c:otherwise>
</c:choose>