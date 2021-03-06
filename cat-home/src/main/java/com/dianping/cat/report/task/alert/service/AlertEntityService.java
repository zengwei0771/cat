package com.dianping.cat.report.task.alert.service;

import org.unidal.dal.jdbc.DalException;
import org.unidal.lookup.annotation.Inject;

import com.dianping.cat.Cat;
import com.dianping.cat.home.dal.report.Alert;
import com.dianping.cat.home.dal.report.AlertDao;
import com.dianping.cat.report.task.alert.sender.AlertEntity;
import com.dianping.cat.report.task.alert.sender.AlertMessageEntity;

public class AlertEntityService {

	@Inject
	private AlertDao m_alertDao;

	private Alert buildAlert(AlertEntity alertEntity, AlertMessageEntity message) {
		Alert alert = new Alert();

		alert.setDomain(alertEntity.getGroup());
		alert.setAlertTime(alertEntity.getDate());
		alert.setCategory(alertEntity.getType() + "-alert");
		alert.setType(alertEntity.getLevel());
		alert.setContent(message.getTitle() + "<br/>" + message.getContent());
		alert.setMetric(alertEntity.getMetric());

		return alert;
	}

	public void storeAlert(AlertEntity alertEntity, AlertMessageEntity message) {
		Alert alert = buildAlert(alertEntity, message);

		try {
			int count = m_alertDao.insert(alert);

			if (count != 1) {
				Cat.logError("insert alert error: " + alert.toString(), new RuntimeException());
			}
		} catch (DalException e) {
			Cat.logError(e);
		}
	}

}
