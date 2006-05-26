/**
 * Copyright (c) 2000-2006 Liferay, LLC. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.liferay.portlet.layoutconfiguration.util;

import com.liferay.portal.model.Portlet;
import com.liferay.portal.service.spring.PortletLocalServiceUtil;
import com.liferay.portal.theme.PortletDisplay;
import com.liferay.portal.theme.PortletDisplayFactory;
import com.liferay.portal.theme.ThemeDisplay;
import com.liferay.portal.util.PortalUtil;
import com.liferay.portal.util.WebKeys;
import com.liferay.portlet.layoutconfiguration.util.velocity.TemplateProcessor;
import com.liferay.portlet.layoutconfiguration.util.xml.RuntimeLogic;
import com.liferay.util.StringPool;
import com.liferay.util.Validator;

import javax.portlet.PortletConfig;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.PageContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;

/**
 * <a href="RuntimePortletUtil.java.html"><b><i>View Source</i></b></a>
 *
 * @author  Brian Wing Shun Chan
 * @author  Raymond Auge
 *
 */
public class RuntimePortletUtil {

	public static void processPortlet(
			StringBuffer sb, ServletContext ctx, HttpServletRequest req,
			HttpServletResponse res, RenderRequest renderRequest,
			RenderResponse renderResponse, String portletId, String instanceId)
		throws Exception {

		processPortlet(
			sb, ctx, req, res, renderRequest, renderResponse, portletId,
			instanceId, null, null, null);
	}

	public static void processPortlet(
			StringBuffer sb, ServletContext ctx, HttpServletRequest req,
			HttpServletResponse res, RenderRequest renderRequest,
			RenderResponse renderResponse, String portletId, String instanceId,
			String columnId, Integer columnPos, Integer columnCount)
		throws Exception {

		ThemeDisplay themeDisplay =
			(ThemeDisplay)req.getAttribute(WebKeys.THEME_DISPLAY);

		Portlet portlet = PortletLocalServiceUtil.getPortletById(
			themeDisplay.getCompanyId(), portletId);

		if ((portlet != null) && portlet.isInstanceable()) {
			if (Validator.isNotNull(instanceId) &&
				Validator.isPassword(instanceId) &&
				(instanceId.length() == 4)) {

				portletId +=
					Portlet.INSTANCE_SEPARATOR + instanceId;

				portlet = PortletLocalServiceUtil.getPortletById(
					themeDisplay.getCompanyId(), portletId);
			}
			else {
				_log.debug(
					"Portlet " + portlet.getPortletId() +
						" is instanceable but does not have a " +
							"valid instance id");

				portlet = null;
			}
		}

		if (portlet == null) {
			return;
		}

		// Capture the current portlet's settings to reset them once the child
		// portlet is rendered

		PortletDisplay portletDisplay = themeDisplay.getPortletDisplay();

		PortletDisplay portletDisplayClone =
			(PortletDisplay)portletDisplay.clone();

		PortletConfig portletConfig =
			(PortletConfig)req.getAttribute(WebKeys.JAVAX_PORTLET_CONFIG);

		try {
			PortalUtil.renderPortlet(
				sb, ctx, req, res, portlet, columnId, columnPos, columnCount);
		}
		finally {
			portletDisplay.copyFrom(portletDisplayClone);

			try {
				PortletDisplayFactory.recycle(portletDisplayClone);
			}
			catch (Exception e) {
				_log.error(e);
			}

			_defineObjects(
				req, portletConfig, renderRequest, renderResponse);
		}
	}

	public static void processTemplate(
			ServletContext ctx, PageContext pageContext, HttpServletRequest req,
			HttpServletResponse res, String content)
		throws Exception {

		processTemplate(ctx, pageContext, req, res, null, content);
	}

	public static void processTemplate(
			ServletContext ctx, PageContext pageContext, HttpServletRequest req,
			HttpServletResponse res, String portletId, String content)
		throws Exception {

		if (Validator.isNull(content)) {
			return;
		}

		TemplateProcessor processor =
			new TemplateProcessor(ctx, req, res, portletId);

		VelocityContext context = new VelocityContext();

		context.put("processor", processor);

		Velocity.evaluate(
			context, pageContext.getOut(), RuntimePortletUtil.class.getName(),
			content);
	}

	public static String processXML(
			String content, RuntimeLogic runtimeLogic)
		throws Exception {

		if (Validator.isNull(content)) {
			return StringPool.BLANK;
		}

		StringBuffer sb = new StringBuffer();

		int x = 0;
		int y = content.indexOf(runtimeLogic.getOpenTag());

		while (y != -1) {
			sb.append(content.substring(x, y));

			int close1 = content.indexOf(runtimeLogic.getClose1Tag(), y);
			int close2 = content.indexOf(runtimeLogic.getClose2Tag(), y);

			if ((close2 == -1) || ((close1 != -1) && (close1 < close2))) {
				x = close1 + runtimeLogic.getClose1Tag().length();
			}
			else {
				x = close2 + runtimeLogic.getClose2Tag().length();
			}

			runtimeLogic.processXML(sb, content.substring(y, x));

			y = content.indexOf(runtimeLogic.getOpenTag(), x);
		}

		if (y == -1) {
			sb.append(content.substring(x, content.length()));
		}

		return sb.toString();
	}

	private static void _defineObjects(
		HttpServletRequest req, PortletConfig portletConfig,
		RenderRequest renderRequest, RenderResponse renderResponse) {

		if (portletConfig != null) {
			req.setAttribute(WebKeys.JAVAX_PORTLET_CONFIG, portletConfig);
		}

		if (renderRequest != null) {
			req.setAttribute(WebKeys.JAVAX_PORTLET_REQUEST, renderRequest);
		}

		if (renderResponse != null) {
			req.setAttribute(WebKeys.JAVAX_PORTLET_RESPONSE, renderResponse);
		}
	}

	private static Log _log = LogFactory.getLog(RuntimePortletUtil.class);

}