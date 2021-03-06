package com.dianping.cat.report.task.product;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.plexus.logging.LogEnabled;
import org.codehaus.plexus.logging.Logger;
import org.unidal.dal.jdbc.DalException;
import org.unidal.helper.Files;
import org.unidal.helper.Threads.Task;
import org.unidal.helper.Urls;
import org.unidal.lookup.annotation.Inject;
import org.unidal.webres.json.JsonArray;
import org.unidal.webres.json.JsonObject;

import com.dianping.cat.Cat;
import com.dianping.cat.core.dal.Hostinfo;
import com.dianping.cat.core.dal.Project;
import com.dianping.cat.message.Transaction;
import com.dianping.cat.service.HostinfoService;
import com.dianping.cat.service.ProjectService;
import com.site.lookup.util.StringUtils;

public class ProjectUpdateTask implements Task, LogEnabled {

	@Inject
	private HostinfoService m_hostInfoService;

	@Inject
	private ProjectService m_projectService;

	private Logger m_logger;

	private Map<String, List<String>> m_domainToIpMap = new HashMap<String, List<String>>();

	private static final long DURATION = 60 * 60 * 1000L;

	private static final String CMDB_DOMAIN_URL = "http://api.cmdb.dp/api/v0.1/projects/s?private_ip=%s";

	private static final String CMDB_INFO_URL = "http://api.cmdb.dp/api/v0.1/projects/%s";

	private static final String CMDB_HOSTNAME_URL = "http://api.cmdb.dp/api/v0.1/ci/s?q=_type:(vserver;server),private_ip:%s&fl=hostname";

	private void buildDomainToIpMap() {
		try {
			List<Hostinfo> infos = m_hostInfoService.findAll();

			for (Hostinfo info : infos) {
				String domain = info.getDomain();
				String ip = info.getIp();
				List<String> ips = m_domainToIpMap.get(domain);

				if (ips == null) {
					ips = new ArrayList<String>();
					m_domainToIpMap.put(domain, ips);
				}
				ips.add(ip);
			}
		} catch (DalException e) {
			Cat.logError(e);
		}
	}

	private boolean checkIfNullOrEqual(String source, String target) {
		if (source == null || source.equals("null")) {
			return true;
		} else {
			return source.equals(target);
		}
	}

	private boolean checkIfValid(String source) {
		if (source == null || "".equals(source) || "null".equals(source)) {
			return false;
		}
		return true;
	}

	@Override
	public void enableLogging(Logger logger) {
		m_logger = logger;
	}

	private String extractStringFromJsonElement(String str) {
		if (str != null && str.startsWith("[\"")) {
			return str.replace("[\"", "").replace("\"]", "");
		} else {
			return str;
		}
	}

	@Override
	public String getName() {
		return "product_update_task";
	}

	private String mergeAndBuildUniqueString(String baseStr, String appendStr) {
		if (StringUtils.isEmpty(appendStr)) {
			return baseStr;
		}

		StringBuilder builder = new StringBuilder(256);
		builder.append(baseStr);

		for (String str : appendStr.split(",")) {
			String tmpStr = extractStringFromJsonElement(str);

			if (builder.indexOf(tmpStr) < 0) {
				builder.append(",");
				builder.append(tmpStr);
			}
		}

		return builder.toString();
	}

	public String parseDomain(String content) throws Exception {
		JsonObject object = new JsonObject(content);
		JsonArray projectArray = object.getJSONArray("projects");

		if (projectArray.length() > 0) {
			JsonObject firstProject = projectArray.getJSONObject(0);
			return firstProject.get("project_name").toString();
		}
		return null;
	}

	public String parseHostname(String content) throws Exception {
		JsonObject object = new JsonObject(content);
		JsonArray resultArray = object.getJSONArray("result");

		if (resultArray.length() > 0) {
			JsonObject firstResult = resultArray.getJSONObject(0);
			return firstResult.get("hostname").toString();
		}
		return null;
	}

	private Map<String, String> parseInfos(String content) throws Exception {
		Map<String, String> infosMap = new HashMap<String, String>();
		JsonObject object = new JsonObject(content);
		JsonObject project = object.getJSONObject("project");

		if (project == null) {
			return infosMap;
		}

		Object owner = project.get("rd_duty");
		Object email = project.get("project_email");
		Object phone = project.get("rd_mobile");

		if (email != null) {
			infosMap.put("owner", owner.toString());
		} else {
			infosMap.put("owner", null);
		}

		if (email != null) {
			infosMap.put("email", email.toString());
		} else {
			infosMap.put("email", null);
		}

		if (phone != null) {
			infosMap.put("phone", phone.toString());
		} else {
			infosMap.put("phone", null);
		}
		return infosMap;
	}

	private String queryCmdbName(List<String> ips) {
		if (ips != null) {
			for (String ip : ips) {
				String cmdbDomain = queryDomainFromCMDB(ip);

				if (checkIfValid(cmdbDomain)) {
					return cmdbDomain;
				}
			}
		}
		return null;
	}

	private String queryDomainFromCMDB(String ip) {
		Transaction t = Cat.newTransaction("CMDB", "queryDomain");

		try {
			String cmdb = String.format(CMDB_DOMAIN_URL, ip);
			InputStream in = Urls.forIO().readTimeout(1000).connectTimeout(1000).openStream(cmdb);
			String content = Files.forIO().readFrom(in, "utf-8");

			t.setStatus(Transaction.SUCCESS);
			t.addData(content);
			return parseDomain(content.trim());
		} catch (Exception e) {
			Cat.logError(e);
			t.setStatus(e);
		} finally {
			t.complete();
		}
		return null;
	}

	private String queryHostnameFromCMDB(String ip) {
		Transaction t = Cat.newTransaction("CMDB", "queryHostname");
		try {
			String cmdb = String.format(CMDB_HOSTNAME_URL, ip);
			InputStream in = Urls.forIO().readTimeout(1000).connectTimeout(1000).openStream(cmdb);
			String content = Files.forIO().readFrom(in, "utf-8");

			t.setStatus(Transaction.SUCCESS);
			t.addData(content);
			return parseHostname(content.trim());
		} catch (Exception e) {
			Cat.logError(e);
			t.setStatus(e);
		} finally {
			t.complete();
		}
		return null;
	}

	private Map<String, String> queryProjectInfoFromCMDB(String cmdbDomain) {
		Transaction t = Cat.newTransaction("CMDB", "queryProjectInfo");
		try {
			String cmdb = String.format(CMDB_INFO_URL, cmdbDomain);
			InputStream in = Urls.forIO().readTimeout(1000).connectTimeout(1000).openStream(cmdb);
			String content = Files.forIO().readFrom(in, "utf-8");

			t.setStatus(Transaction.SUCCESS);
			t.addData(content);
			return parseInfos(content.trim());
		} catch (Exception e) {
			Cat.logError(e);
			t.setStatus(e);
		} finally {
			t.complete();
		}
		return null;
	}

	@Override
	public void run() {
		boolean active = true;

		while (active) {
			long startMill = System.currentTimeMillis();
			int hour = Calendar.getInstance().get(Calendar.HOUR);
			String hourStr = String.valueOf(hour);

			if (hour < 10) {
				hourStr = "0" + hourStr;
			}

			Transaction t1 = Cat.newTransaction("UpdateHostname", "H" + hourStr);
			try {
				updateHostNameInfo();
				t1.setStatus(Transaction.SUCCESS);
			} catch (Exception e) {
				t1.setStatus(e);
			} finally {
				t1.complete();
			}

			Transaction t2 = Cat.newTransaction("UpdateProjectInfo", "H" + hourStr);
			try {
				updateProjectInfo();
				t2.setStatus(Transaction.SUCCESS);
			} catch (Exception e) {
				t1.setStatus(e);
			} finally {
				t2.complete();
			}

			try {
				long executeMills = System.currentTimeMillis() - startMill;

				if (executeMills < DURATION) {
					Thread.sleep(DURATION - executeMills);
				}
			} catch (InterruptedException e) {
				active = false;
			}
		}
	}

	@Override
	public void shutdown() {
	}

	private void updateHostNameInfo() {
		try {
			List<Hostinfo> infos = m_hostInfoService.findAll();

			for (Hostinfo info : infos) {
				try {
					String hostname = info.getHostname();
					String ip = info.getIp();
					String cmdbHostname = queryHostnameFromCMDB(ip);

					if (StringUtils.isEmpty(cmdbHostname)) {
						continue;
					}

					if (StringUtils.isEmpty(hostname) || !hostname.equals(cmdbHostname)) {
						info.setHostname(cmdbHostname);
						m_hostInfoService.updateHostinfo(info);
					} else {
						m_logger.error("can't find hostname for ip: " + ip);
					}
				} catch (Exception e) {
					Cat.logError(e);
				}
			}
		} catch (Throwable e) {
			Cat.logError(e);
		}
	}

	private boolean updateProject(Project pro) {
		Map<String, String> infosMap = queryProjectInfoFromCMDB(pro.getCmdbDomain());
		String cmdbOwner = infosMap.get("owner");
		String cmdbEmail = infosMap.get("email");
		String cmdbPhone = infosMap.get("phone");
		String dbOwner = pro.getOwner();
		String dbEmail = pro.getEmail();
		String dbPhone = pro.getPhone();
		boolean isProjChanged = false;

		if (!checkIfNullOrEqual(cmdbOwner, dbOwner)) {
			pro.setOwner(mergeAndBuildUniqueString(cmdbOwner, dbOwner));
			isProjChanged = true;
		}
		if (!checkIfNullOrEqual(cmdbEmail, dbEmail)) {
			pro.setEmail(mergeAndBuildUniqueString(cmdbEmail, dbEmail));
			isProjChanged = true;
		}
		if (!checkIfNullOrEqual(cmdbPhone, dbPhone)) {
			pro.setPhone(mergeAndBuildUniqueString(cmdbPhone, dbPhone));
			isProjChanged = true;
		}

		return isProjChanged;
	}

	private void updateProjectInfo() {
		buildDomainToIpMap();

		try {
			List<Project> projects = m_projectService.findAll();

			for (Project pro : projects) {
				try {
					List<String> ips = m_domainToIpMap.get(pro.getDomain());
					String originCmdbDomain = pro.getCmdbDomain();
					String cmdbDomain = queryCmdbName(ips);

					if (cmdbDomain != null) {
						boolean isChange = !cmdbDomain.equals(originCmdbDomain);

						if (checkIfValid(cmdbDomain)) {
							pro.setCmdbDomain(cmdbDomain);

							boolean isProjectInfoChange = updateProject(pro);

							if (isProjectInfoChange || isChange) {
								m_projectService.updateProject(pro);
							}
						}
					}
				} catch (Exception e) {
					Cat.logError(e);
				}
			}
		} catch (Throwable e) {
			Cat.logError(e);
		}
	}

}
