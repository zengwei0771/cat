package com.dianping.cat.report.page.statistics;

import org.unidal.web.mvc.view.BaseJspViewer;

import com.dianping.cat.report.ReportPage;

public class JspViewer extends BaseJspViewer<ReportPage, Action, Context, Model> {
	@Override
	protected String getJspFilePath(Context ctx, Model model) {
		Action action = model.getAction();

		switch (action) {
		case BUG_REPORT:
			return JspFile.HOURLY_REPORT.getPath();
		case BUG_HISTORY_REPORT:
			return JspFile.HISTORY_REPORT.getPath();
		case BUG_HTTP_JSON:
			return JspFile.HTTP_JSON.getPath();
		case SERVICE_REPORT:
			return JspFile.SERVICE_REPORT.getPath();
		case SERVICE_HISTORY_REPORT:
			return JspFile.SERVICE_HISTORY_REPORT.getPath();
		case HEAVY_HISTORY_REPORT:
			return JspFile.HEAVY_HISTORY_REPORT.getPath();
		case HEAVY_REPORT:
			return JspFile.HEAVY_REPORT.getPath();
		case UTILIZATION_HISTORY_REPORT:
			return JspFile.UTILIZATION_HISTORY_REPORT.getPath();
		case UTILIZATION_REPORT:
			return JspFile.UTILIZATION_REPORT.getPath();
		case ALERT_HISTORY_REPORT:
			return JspFile.ALERT_HISTORY_REPORT.getPath();
		case ALERT_REPORT:
			return JspFile.ALERT_REPORT.getPath();
		case ALERT_REPORT_DETAIL:
		case ALERT_HISTORY_REPORT_DETAIL:
			return JspFile.ALERT_REPORT_DETAIL.getPath();
		case ALERT_SUMMARY:
			return JspFile.ALERT_SUMMARY.getPath();
		}

		throw new RuntimeException("Unknown action: " + action);
	}
}
