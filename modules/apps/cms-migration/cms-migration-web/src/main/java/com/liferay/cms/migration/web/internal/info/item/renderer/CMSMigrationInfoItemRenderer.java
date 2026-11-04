/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.info.item.renderer;

import com.liferay.cms.migration.web.internal.constants.CMSMigrationConstants;
import com.liferay.document.library.kernel.service.DLAppLocalService;
import com.liferay.document.library.util.DLURLHelper;
import com.liferay.info.item.renderer.InfoItemRenderer;
import com.liferay.object.model.ObjectEntry;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.HtmlParserUtil;
import com.liferay.portal.kernel.util.HtmlUtil;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.util.WebKeys;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.PrintWriter;
import java.io.Serializable;

import java.util.Locale;
import java.util.Map;

/**
 * Renders a migrated object entry with the markup of the classic renderer it
 * replaces, so collections and content displays that used the abstract,
 * title, or full content renderers look the same after the migration.
 *
 * @author Lino Alves
 */
public class CMSMigrationInfoItemRenderer
	implements InfoItemRenderer<ObjectEntry> {

	public static final String STYLE_ABSTRACT = "abstract";

	public static final String STYLE_BLOGS_FULL_CONTENT = "blogs-full-content";

	public static final String STYLE_FILE_ABSTRACT = "file-abstract";

	public static final String STYLE_FILE_TITLE = "file-title";

	public static final String STYLE_TITLE = "title";

	public static String getKey(String style) {
		return CMSMigrationInfoItemRenderer.class.getName() + StringPool.POUND +
			style;
	}

	public CMSMigrationInfoItemRenderer(
		DLAppLocalService dlAppLocalService, DLURLHelper dlURLHelper,
		Language language, String style) {

		_dlAppLocalService = dlAppLocalService;
		_dlURLHelper = dlURLHelper;
		_language = language;
		_style = style;
	}

	@Override
	public String getKey() {
		return getKey(_style);
	}

	@Override
	public String getLabel(Locale locale) {
		if (_style.equals(STYLE_ABSTRACT) ||
			_style.equals(STYLE_FILE_ABSTRACT)) {

			return _language.get(locale, "abstract");
		}
		else if (_style.equals(STYLE_BLOGS_FULL_CONTENT)) {
			return _language.get(locale, "full-content");
		}

		return _language.get(locale, "title");
	}

	@Override
	public void render(
		ObjectEntry objectEntry, HttpServletRequest httpServletRequest,
		HttpServletResponse httpServletResponse) {

		try {
			ThemeDisplay themeDisplay =
				(ThemeDisplay)httpServletRequest.getAttribute(
					WebKeys.THEME_DISPLAY);

			PrintWriter printWriter = httpServletResponse.getWriter();

			printWriter.write(_getHTML(objectEntry, themeDisplay.getLocale()));
		}
		catch (Exception exception) {
			throw new RuntimeException(exception);
		}
	}

	private String _getFileEntryURL(long fileEntryId, String queryString) {
		if (fileEntryId == 0) {
			return StringPool.BLANK;
		}

		try {
			FileEntry fileEntry = _dlAppLocalService.getFileEntry(fileEntryId);

			return _dlURLHelper.getPreviewURL(
				fileEntry, fileEntry.getFileVersion(), null, queryString, false,
				false);
		}
		catch (Exception exception) {
			if (_log.isDebugEnabled()) {
				_log.debug(exception);
			}

			return StringPool.BLANK;
		}
	}

	private String _getHTML(ObjectEntry objectEntry, Locale locale)
		throws Exception {

		String languageId = LocaleUtil.toLanguageId(locale);
		Map<String, Serializable> values = objectEntry.getValues();

		String title = objectEntry.getTitleValue(languageId);

		if (_style.equals(STYLE_FILE_TITLE)) {
			return "<div>" + HtmlUtil.escape(title) + "</div>";
		}
		else if (_style.equals(STYLE_TITLE)) {
			return "<div class=\"asset-summary\">" + HtmlUtil.escape(title) +
				"</div>";
		}
		else if (_style.equals(STYLE_FILE_ABSTRACT)) {
			return _getImageDivHTML(
				"aspect-ratio aspect-ratio-8-to-3 aspect-ratio-bg-cover " +
					"cover-image mb-4",
				null,
				_getFileEntryURL(
					GetterUtil.getLong(
						values.get(
							CMSMigrationConstants.OBJECT_FIELD_NAME_FILE)),
					"&imagePreview=1"));
		}
		else if (_style.equals(STYLE_BLOGS_FULL_CONTENT)) {
			return StringBundler.concat(
				"<div class=\"widget-mode-simple\"><div class=\"",
				"widget-mode-simple-entry\"><div class=\"widget-content\" ",
				"id=\"", objectEntry.getObjectEntryId(), "\">",
				_getImageDivHTML(
					"aspect-ratio aspect-ratio-8-to-3 aspect-ratio-bg-cover " +
						"cover-image",
					_getLocalizedValue(
						languageId,
						CMSMigrationConstants.
							OBJECT_FIELD_NAME_COVER_IMAGE_CAPTION,
						values),
					_getFileEntryURL(
						GetterUtil.getLong(
							values.get(
								CMSMigrationConstants.
									OBJECT_FIELD_NAME_COVER_IMAGE)),
						StringPool.BLANK)),
				_getLocalizedValue(
					languageId, CMSMigrationConstants.OBJECT_FIELD_NAME_CONTENT,
					values),
				"</div></div></div>");
		}

		StringBundler sb = new StringBundler(4);

		sb.append("<div class=\"asset-summary\">");

		String smallImageURL = _getFileEntryURL(
			GetterUtil.getLong(
				values.get(
					CMSMigrationConstants.OBJECT_FIELD_NAME_SMALL_IMAGE)),
			StringPool.BLANK);

		if (Validator.isNull(smallImageURL)) {
			smallImageURL = GetterUtil.getString(
				values.get(
					CMSMigrationConstants.OBJECT_FIELD_NAME_SMALL_IMAGE_URL));
		}

		if (Validator.isNotNull(smallImageURL)) {
			sb.append(
				_getImageDivHTML(
					"aspect-ratio aspect-ratio-8-to-3 aspect-ratio-bg-cover " +
						"cover-image mb-4",
					null, smallImageURL));
		}

		String summary = _getLocalizedValue(
			languageId, CMSMigrationConstants.OBJECT_FIELD_NAME_DESCRIPTION,
			values);

		if (Validator.isNull(summary)) {
			summary = StringUtil.shorten(
				HtmlParserUtil.extractText(
					_getLocalizedValue(
						languageId,
						CMSMigrationConstants.OBJECT_FIELD_NAME_CONTENT,
						values)),
				_ABSTRACT_LENGTH);
		}

		sb.append(HtmlUtil.escape(summary));
		sb.append("</div>");

		return sb.toString();
	}

	private String _getImageDivHTML(
		String cssClass, String caption, String url) {

		if (Validator.isNull(url)) {
			return StringPool.BLANK;
		}

		StringBundler sb = new StringBundler(9);

		sb.append("<div ");

		if (Validator.isNotNull(caption)) {
			sb.append("aria-label=\"");
			sb.append(HtmlUtil.escapeAttribute(HtmlUtil.stripHtml(caption)));
			sb.append("\" role=\"img\" ");
		}

		sb.append("class=\"");
		sb.append(cssClass);
		sb.append("\" style=\"background-image: url('");
		sb.append(HtmlUtil.escapeAttribute(url));
		sb.append("');\"></div>");

		return sb.toString();
	}

	private String _getLocalizedValue(
		String languageId, String name, Map<String, Serializable> values) {

		Object value = values.get(name + "_i18n");

		if (value instanceof Map) {
			Map<String, Object> localizedValues = (Map<String, Object>)value;

			String localizedValue = GetterUtil.getString(
				localizedValues.get(languageId));

			if (Validator.isNotNull(localizedValue)) {
				return localizedValue;
			}
		}

		return GetterUtil.getString(values.get(name));
	}

	private static final int _ABSTRACT_LENGTH = 200;

	private static final Log _log = LogFactoryUtil.getLog(
		CMSMigrationInfoItemRenderer.class);

	private final DLAppLocalService _dlAppLocalService;
	private final DLURLHelper _dlURLHelper;
	private final Language _language;
	private final String _style;

}