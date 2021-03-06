package com.dianping.cat.config.app;

import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.unidal.dal.jdbc.DalException;
import org.unidal.lookup.annotation.Inject;

import com.dianping.cat.Cat;
import com.dianping.cat.app.AppDataCommand;
import com.dianping.cat.app.AppDataCommandDao;
import com.dianping.cat.app.AppDataCommandEntity;
import com.dianping.cat.configuration.app.entity.Code;

public class AppDataService {

	@Inject
	private AppDataCommandDao m_dao;

	@Inject
	private AppConfigManager m_appConfigManager;

	public static final String SUCCESS = "success";

	public static final String REQUEST = "request";

	public static final String DELAY = "delay";

	public void insertSignal(AppDataCommand proto) throws DalException {
		m_dao.insert(proto);
	}

	public void insert(AppDataCommand[] proto) throws DalException {
		m_dao.insert(proto);
	}

	public Double[] queryValue(QueryEntity entity, String type) {
		int commandId = entity.getCommand();
		Date period = entity.getDate();
		int city = entity.getCity();
		int operator = entity.getOperator();
		int network = entity.getNetwork();
		int appVersion = entity.getVersion();
		int connnectType = entity.getChannel();
		int code = entity.getCode();
		int platform = entity.getPlatfrom();
		List<AppDataCommand> datas;

		try {
			if (SUCCESS.equals(type)) {
				datas = m_dao.findDataByMinuteCode(commandId, period, city, operator, network, appVersion, connnectType,
				      code, platform, AppDataCommandEntity.READSET_SUCCESS_DATA);
				AppDataCommandMap convertedData = convert2AppDataCommandMap(datas, period);

				return querySuccessRatio(commandId, convertedData);
			} else if (REQUEST.equals(type)) {
				datas = m_dao.findDataByMinute(commandId, period, city, operator, network, appVersion, connnectType, code,
				      platform, AppDataCommandEntity.READSET_COUNT_DATA);

				AppDataCommandMap convertedData = convert2AppDataCommandMap(datas, period);
				return queryRequestCount(convertedData);
			} else if (DELAY.equals(type)) {
				datas = m_dao.findDataByMinute(commandId, period, city, operator, network, appVersion, connnectType, code,
				      platform, AppDataCommandEntity.READSET_AVG_DATA);

				AppDataCommandMap dataPair = convert2AppDataCommandMap(datas, period);
				return queryDelayAvg(dataPair);
			} else {
				throw new RuntimeException("unexpected query type, type:" + type);
			}
		} catch (Exception e) {
			Cat.logError(e);
		}
		return null;
	}

	private static int queryTenMinutesBackLength(Date period, int n) {
		int size = n;
		Calendar cal = Calendar.getInstance();

		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);

		if (period.equals(cal.getTime())) {
			long start = cal.getTimeInMillis();
			long current = System.currentTimeMillis();
			int length = (int) (current - current % 300000 - start) / 300000 - 1;
			size = length < 0 ? 0 : length;
		}
		return size;
	}

	private AppDataCommandMap convert2AppDataCommandMap(List<AppDataCommand> fromDatas, Date period) {
		Map<Integer, List<AppDataCommand>> dataMap = new LinkedHashMap<Integer, List<AppDataCommand>>();
		int max = -1;

		for (AppDataCommand from : fromDatas) {
			int minute = from.getMinuteOrder();

			if (max < 0 || max < minute) {
				max = minute;
			}
			List<AppDataCommand> data = dataMap.get(minute);

			if (data == null) {
				data = new LinkedList<AppDataCommand>();

				dataMap.put(minute, data);
			}
			data.add(from);
		}
		int n = max / 5 + 1;
		int length = queryTenMinutesBackLength(period, n);

		return new AppDataCommandMap(length, dataMap);
	}

	public Double[] querySuccessRatio(int commandId, AppDataCommandMap convertedData) {
		int n = convertedData.getMaxSize();
		Double[] value = new Double[n];

		for (int i = 0; i < n; i++) {
			value[i] = 100.0;
		}

		try {
			for (Entry<Integer, List<AppDataCommand>> entry : convertedData.getAppDataCommands().entrySet()) {
				int key = entry.getKey();
				long success = 0;
				long sum = 0;

				for (AppDataCommand data : entry.getValue()) {
					long number = data.getAccessNumberSum();

					if (isSuccessStatus(commandId, data.getCode())) {
						success += number;
					}
					sum += number;
				}
				int index = key / 5;

				if (index < n) {
					value[index] = (double) success / sum * 100;
				}
			}
		} catch (Exception e) {
			Cat.logError(e);
		}

		return value;
	}

	private boolean isSuccessStatus(int commandId, int code) {
		Collection<Code> codes = m_appConfigManager.queryCodeByCommand(commandId);

		for (Code c : codes) {
			if (c.getId() == code) {
				return (c.getStatus() == 0);
			}
		}
		return false;
	}

	public Double[] queryRequestCount(AppDataCommandMap convertedData) {
		int n = convertedData.getMaxSize();
		Double[] value = new Double[n];

		for (Entry<Integer, List<AppDataCommand>> entry : convertedData.getAppDataCommands().entrySet()) {
			for (AppDataCommand data : entry.getValue()) {
				double count = data.getAccessNumberSum();
				int index = data.getMinuteOrder() / 5;

				if (index < n) {
					value[index] = count;
				}
			}
		}
		return value;
	}

	public Double[] queryDelayAvg(AppDataCommandMap convertedData) {
		int n = convertedData.getMaxSize();
		Double[] value = new Double[n];

		for (Entry<Integer, List<AppDataCommand>> entry : convertedData.getAppDataCommands().entrySet()) {
			for (AppDataCommand data : entry.getValue()) {
				long count = data.getAccessNumberSum();
				long sum = data.getResponseSumTimeSum();
				double avg = sum / count;
				int index = data.getMinuteOrder() / 5;

				if (index < n) {
					value[index] = avg;
				}
			}
		}

		return value;
	}

	public class AppDataCommandMap {
		private int m_maxSize;

		private Map<Integer, List<AppDataCommand>> m_appDataCommands;

		public int getMaxSize() {
			return m_maxSize;
		}

		public Map<Integer, List<AppDataCommand>> getAppDataCommands() {
			return m_appDataCommands;
		}

		public AppDataCommandMap(int maxSize, Map<Integer, List<AppDataCommand>> appDataCommands) {
			m_maxSize = maxSize;
			m_appDataCommands = appDataCommands;
		}
	}

}
