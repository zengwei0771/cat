package com.dianping.cat.broker.api.page.single;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.plexus.logging.LogEnabled;
import org.codehaus.plexus.logging.Logger;
import org.unidal.lookup.annotation.Inject;
import org.unidal.lookup.util.StringUtils;
import org.unidal.web.mvc.PageHandler;
import org.unidal.web.mvc.annotation.InboundActionMeta;
import org.unidal.web.mvc.annotation.OutboundActionMeta;
import org.unidal.web.mvc.annotation.PayloadMeta;

import com.dianping.cat.Cat;
import com.dianping.cat.broker.api.page.Constrants;
import com.dianping.cat.broker.api.page.MonitorEntity;
import com.dianping.cat.broker.api.page.MonitorManager;
import com.dianping.cat.broker.api.page.RequestUtils;
import com.dianping.cat.message.Event;

public class Handler implements PageHandler<Context>, LogEnabled {

	@Inject
	private MonitorManager m_manager;

	@Inject
	private RequestUtils m_util;

	private Logger m_logger;

	@Override
	public void enableLogging(Logger logger) {
		m_logger = logger;
	}

	@Override
	@PayloadMeta(Payload.class)
	@InboundActionMeta(name = "single")
	public void handleInbound(Context ctx) throws ServletException, IOException {
		// display only, no action here
	}

	@Override
	@OutboundActionMeta(name = "single")
	public void handleOutbound(Context ctx) throws ServletException, IOException {
		Payload payload = ctx.getPayload();
		HttpServletRequest request = ctx.getHttpServletRequest();
		HttpServletResponse response = ctx.getHttpServletResponse();

		MonitorEntity entity = new MonitorEntity();
		String userIp = m_util.getRemoteIp(request);

		if (userIp != null) {
			String errorCode = payload.getErrorCode();
			String httpStatus = payload.getHttpStatus();

			if (validate(errorCode, httpStatus)) {
				Cat.logEvent("ip", "hit", Event.SUCCESS, userIp);

				entity.setDuration(payload.getDuration());
				entity.setErrorCode(errorCode);
				entity.setHttpStatus(httpStatus);
				entity.setIp(userIp);
				entity.setTargetUrl(payload.getTargetUrl());
				entity.setTimestamp(payload.getTimestamp());
				m_manager.offer(entity);
			} else {
				Cat.logEvent("Code", "Error", Event.SUCCESS, errorCode + " " + httpStatus);
			}
		} else {
			Cat.logEvent("unknownIp", "single", Event.SUCCESS, null);
			m_logger.info("unknown http request, x-forwarded-for:" + request.getHeader("x-forwarded-for"));
		}
		response.getWriter().write("OK");
	}

	private boolean validate(String errorCode, String httpStatus) {
		try {
			if (StringUtils.isNotEmpty(errorCode) && !Constrants.NOT_SET.equals(errorCode)) {
				Double.parseDouble(errorCode);
			}
			if (StringUtils.isNotEmpty(httpStatus) && !Constrants.NOT_SET.equals(httpStatus)) {
				Double.parseDouble(httpStatus);
			}
			return true;
		} catch (Exception e) {
			return false;
		}
	}

}
