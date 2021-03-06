package com.dianping.cat.report.task.metric;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import org.junit.Test;
import org.unidal.helper.Files;

import com.dianping.cat.Cat;
import com.dianping.cat.advanced.metric.config.entity.MetricItemConfig;
import com.dianping.cat.home.rule.entity.Condition;
import com.dianping.cat.home.rule.entity.Config;
import com.dianping.cat.home.rule.entity.MonitorRules;
import com.dianping.cat.home.rule.entity.Rule;
import com.dianping.cat.home.rule.entity.SubCondition;
import com.dianping.cat.home.rule.transform.DefaultSaxParser;
import com.dianping.cat.report.task.alert.AlertResultEntity;
import com.dianping.cat.report.task.alert.DataChecker;
import com.dianping.cat.report.task.alert.DefaultDataChecker;

public class AlertConfigTest {

	private DataChecker m_checker = new DefaultDataChecker();

	private Map<String, List<com.dianping.cat.home.rule.entity.Config>> buildConfigMap(MonitorRules monitorRules) {
		if (monitorRules == null || monitorRules.getRules().size() == 0) {
			return null;
		}

		Map<String, List<com.dianping.cat.home.rule.entity.Config>> map = new HashMap<String, List<com.dianping.cat.home.rule.entity.Config>>();

		for (Rule rule : monitorRules.getRules().values()) {
			map.put(rule.getId(), rule.getConfigs());
		}

		return map;
	}

	private List<Condition> buildConditions(List<Config> configs) {
		List<Condition> conditions = new ArrayList<Condition>();

		for (Config config : configs) {
			conditions.addAll(config.getConditions());
		}

		return conditions;
	}

	private MonitorRules buildMonitorRuleFromFile(String path) {
		try {
			String content = Files.forIO().readFrom(this.getClass().getResourceAsStream(path), "utf-8");
			return DefaultSaxParser.parse(content);
		} catch (Exception ex) {
			Cat.logError(ex);
			return null;
		}
	}

	private List<Config> convert(MetricItemConfig metricItemConfig) {
		List<Config> configs = new ArrayList<Config>();
		Config config = new Config();
		Condition condition = new Condition();
		SubCondition descPerSubcon = new SubCondition();
		SubCondition descValSubcon = new SubCondition();

		double decreasePercent = metricItemConfig.getDecreasePercentage();
		double decreaseValue = metricItemConfig.getDecreaseValue();

		if (decreasePercent == 0) {
			decreasePercent = 50;
		}
		if (decreaseValue == 0) {
			decreaseValue = 100;
		}

		descPerSubcon.setType("DescPer").setText(String.valueOf(decreasePercent));
		descValSubcon.setType("DescVal").setText(String.valueOf(decreaseValue));

		condition.addSubCondition(descPerSubcon).addSubCondition(descValSubcon);
		config.addCondition(condition);
		configs.add(config);
		return configs;
	}

	@Test
	public void test() {
		DataChecker alertConfig = new DefaultDataChecker();
		MetricItemConfig itemConfig = new MetricItemConfig();
		List<Condition> conditions = buildConditions(convert(itemConfig));

		double baseline[] = { 100, 100 };
		double value[] = { 200, 200 };
		AlertResultEntity result = extractError(alertConfig.checkData(value, baseline, conditions));
		Assert.assertNull(result);

		double[] baseline2 = { 100, 100 };
		double[] value2 = { 49, 49 };
		result = extractError(alertConfig.checkData(value2, baseline2, conditions));
		Assert.assertNull(result);

		double[] baseline3 = { 100, 100 };
		double[] value3 = { 51, 49 };
		result = extractError(alertConfig.checkData(value3, baseline3, conditions));
		Assert.assertNull(result);

		double[] baseline4 = { 50, 50 };
		double[] value4 = { 10, 10 };
		result = extractError(alertConfig.checkData(value4, baseline4, conditions));
		Assert.assertNull(result);

		itemConfig.setDecreaseValue(30);
		itemConfig.setDecreasePercentage(50);
		conditions = buildConditions(convert(itemConfig));
		result = extractError(alertConfig.checkData(value4, baseline4, conditions));
		Assert.assertEquals(result.isTriggered(), true);

		itemConfig.setDecreaseValue(41);
		itemConfig.setDecreasePercentage(50);
		conditions = buildConditions(convert(itemConfig));
		result = extractError(alertConfig.checkData(value4, baseline4, conditions));
		Assert.assertNull(result);

		itemConfig.setDecreaseValue(30);
		itemConfig.setDecreasePercentage(79);
		conditions = buildConditions(convert(itemConfig));
		result = extractError(alertConfig.checkData(value4, baseline4, conditions));
		Assert.assertEquals(result.isTriggered(), true);

		itemConfig.setDecreaseValue(30);
		itemConfig.setDecreasePercentage(80);
		conditions = buildConditions(convert(itemConfig));
		result = extractError(alertConfig.checkData(value4, baseline4, conditions));
		Assert.assertNull(result);

		itemConfig.setDecreaseValue(30);
		itemConfig.setDecreasePercentage(80);
		conditions = buildConditions(convert(itemConfig));
		result = extractError(alertConfig.checkData(value4, baseline4, conditions));
		Assert.assertNull(result);

		double[] baseline5 = { 117, 118 };
		double[] value5 = { 43, 48 };
		itemConfig.setDecreasePercentage(50);
		itemConfig.setDecreasePercentage(50);
		conditions = buildConditions(convert(itemConfig));
		result = extractError(alertConfig.checkData(value5, baseline5, conditions));
		Assert.assertEquals(result.isTriggered(), true);
	}

	@Test
	public void testMinute() {
		Map<String, List<com.dianping.cat.home.rule.entity.Config>> configMap = buildConfigMap(buildMonitorRuleFromFile("/config/test-minute-monitor.xml"));

		Assert.assertNotNull(configMap);

		double baseline[] = { 50, 200, 200 };
		double value[] = { 50, 100, 100 };
		AlertResultEntity result = extractError(m_checker.checkData(value, baseline,
		      buildConditions(configMap.get("two-minute"))));
		Assert.assertEquals(result.isTriggered(), true);
	}

	@Test
	public void testRule() {
		Map<String, List<com.dianping.cat.home.rule.entity.Config>> configMap = buildConfigMap(buildMonitorRuleFromFile("/config/test-rule-monitor.xml"));

		Assert.assertNotNull(configMap);

		double baseline[] = { 200, 200 };
		double value[] = { 100, 100 };
		AlertResultEntity result = extractError(m_checker.checkData(value, baseline,
		      buildConditions(configMap.get("decreasePercentage"))));
		Assert.assertEquals(result.isTriggered(), true);

		double[] baseline2 = { 200, 300 };
		double[] value2 = { 100, 100 };
		result = extractError(m_checker.checkData(value2, baseline2, buildConditions(configMap.get("decreaseValue"))));
		Assert.assertEquals(result.isTriggered(), true);

		double[] baseline3 = { 200, 50 };
		double[] value3 = { 400, 100 };
		result = extractError(m_checker
		      .checkData(value3, baseline3, buildConditions(configMap.get("increasePercentage"))));
		Assert.assertEquals(result.isTriggered(), true);

		double[] baseline4 = { 200, 50 };
		double[] value4 = { 400, 100 };
		result = extractError(m_checker.checkData(value4, baseline4, buildConditions(configMap.get("increaseValue"))));
		Assert.assertEquals(result.isTriggered(), true);

		double[] baseline5 = { 200, 200 };
		double[] value5 = { 500, 600 };
		result = extractError(m_checker.checkData(value5, baseline5, buildConditions(configMap.get("absoluteMaxValue"))));
		Assert.assertEquals(result.isTriggered(), true);

		double[] baseline6 = { 200, 200 };
		double[] value6 = { 50, 40 };
		result = extractError(m_checker.checkData(value6, baseline6, buildConditions(configMap.get("absoluteMinValue"))));
		Assert.assertEquals(result.isTriggered(), true);

		double[] baseline7 = { 200, 200 };
		double[] value7 = { 100, 100 };
		result = extractError(m_checker.checkData(value7, baseline7,
		      buildConditions(configMap.get("conditionCombination"))));
		Assert.assertEquals(result.isTriggered(), true);

		double[] baseline8 = { 200, 200 };
		double[] value8 = { 100, 100 };
		result = extractError(m_checker.checkData(value8, baseline8,
		      buildConditions(configMap.get("subconditionCombination"))));
		Assert.assertNull(result);
	}

	private AlertResultEntity extractError(List<AlertResultEntity> alertResults) {
		int length = alertResults.size();
		if (length == 0) {
			return null;
		}

		for (AlertResultEntity alertResult : alertResults) {
			if (alertResult.getAlertLevel().equals("error")) {
				return alertResult;
			}
		}

		return alertResults.get(length - 1);
	}
}
