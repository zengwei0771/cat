package com.dianping.cat.report.page.cross.display;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import com.dianping.cat.consumer.cross.model.entity.CrossReport;
import com.dianping.cat.consumer.cross.model.entity.Local;
import com.dianping.cat.consumer.cross.model.entity.Remote;
import com.dianping.cat.consumer.cross.model.entity.Type;
import com.dianping.cat.consumer.cross.model.transform.BaseVisitor;
import com.dianping.cat.service.HostinfoService;

public class ProjectInfo extends BaseVisitor {

	public static final String ALL_SERVER = "AllServers";

	public static final String ALL_CLIENT = "AllClients";

	private static final String UNKNOWN_PROJECT = "UnknownProject";

	private Map<String, TypeDetailInfo> m_callProjectsInfo = new LinkedHashMap<String, TypeDetailInfo>();

	private Map<String, TypeDetailInfo> m_serviceProjectsInfo = new LinkedHashMap<String, TypeDetailInfo>();

	private Map<String, TypeDetailInfo> m_callServiceProjectsInfo = new LinkedHashMap<String, TypeDetailInfo>();

	private String m_clientIp;

	private long m_reportDuration;

	private String m_callSortBy = "Avg";

	private String m_serviceSortBy = "Avg";

	private HostinfoService m_hostinfoService;

	public ProjectInfo(long reportDuration) {
		m_reportDuration = reportDuration;
	}

	public void addAllCallProjectInfo(String domain, TypeDetailInfo info) {
		TypeDetailInfo all = m_callServiceProjectsInfo.get(ALL_CLIENT);

		if (all == null) {
			all = new TypeDetailInfo(m_reportDuration, ALL_CLIENT);
			all.setType(info.getType());
			m_callServiceProjectsInfo.put(ALL_CLIENT, all);
		}
		all.mergeTypeDetailInfo(info);
		m_callServiceProjectsInfo.put(domain, info);
	}

	private void addCallProject(String ip, Type type) {
		String projectName = getProjectName(ip);

		TypeDetailInfo all = m_callProjectsInfo.get(ALL_SERVER);
		if (all == null) {
			all = new TypeDetailInfo(m_reportDuration, ALL_SERVER);
			m_callProjectsInfo.put(ALL_SERVER, all);
		}
		TypeDetailInfo info = m_callProjectsInfo.get(projectName);
		if (info == null) {
			info = new TypeDetailInfo(m_reportDuration, projectName);
			m_callProjectsInfo.put(projectName, info);
		}
		info.mergeType(type);
		all.mergeType(type);
	}

	private void addServiceProject(String ip, Type type) {
		String projectName = getProjectName(ip);

		TypeDetailInfo all = m_serviceProjectsInfo.get(ALL_CLIENT);
		if (all == null) {
			all = new TypeDetailInfo(m_reportDuration, ALL_CLIENT);
			m_serviceProjectsInfo.put(ALL_CLIENT, all);
		}
		TypeDetailInfo info = m_serviceProjectsInfo.get(projectName);
		if (info == null) {
			info = new TypeDetailInfo(m_reportDuration, projectName);
			m_serviceProjectsInfo.put(projectName, info);
		}
		info.mergeType(type);
		all.mergeType(type);
	}

	public Map<String, TypeDetailInfo> getAllCallProjectInfo() {
		return m_callProjectsInfo;
	}

	public Collection<TypeDetailInfo> getCallProjectsInfo() {
		List<TypeDetailInfo> values = new ArrayList<TypeDetailInfo>(m_callProjectsInfo.values());
		Collections.sort(values, new TypeCompartor(m_callSortBy));
		return values;
	}

	public List<TypeDetailInfo> getCallServiceProjectsInfo() {
		List<TypeDetailInfo> values = new ArrayList<TypeDetailInfo>(m_callServiceProjectsInfo.values());
		Collections.sort(values, new TypeCompartor(m_serviceSortBy));
		return values;
	}

	public String getProjectName(String ip) {
		if (StringUtils.isEmpty(ip)) {
			return UNKNOWN_PROJECT;
		}
		if (ip.indexOf(':') > 0) {
			ip = ip.substring(0, ip.indexOf(':'));
		}
		return m_hostinfoService.queryDomainByIp(ip);
	}

	public long getReportDuration() {
		return m_reportDuration;
	}

	public List<TypeDetailInfo> getServiceProjectsInfo() {
		List<TypeDetailInfo> values = new ArrayList<TypeDetailInfo>(m_serviceProjectsInfo.values());
		Collections.sort(values, new TypeCompartor(m_serviceSortBy));
		return values;
	}

	public ProjectInfo setCallSortBy(String callSoryBy) {
		m_callSortBy = callSoryBy;
		return this;
	}

	public ProjectInfo setClientIp(String clientIp) {
		m_clientIp = clientIp;
		return this;
	}

	public void setHostinfoService(HostinfoService hostinfoService) {
		m_hostinfoService = hostinfoService;
	}

	public ProjectInfo setServiceSortBy(String serviceSortBy) {
		m_serviceSortBy = serviceSortBy;
		return this;
	}

	@Override
	public void visitCrossReport(CrossReport crossReport) {
		super.visitCrossReport(crossReport);
	}

	@Override
	public void visitLocal(Local local) {
		if (m_clientIp.equals("All") || m_clientIp.equals(local.getId())) {
			super.visitLocal(local);
		}
	}

	@Override
	public void visitRemote(Remote remote) {
		String remoteIp = remote.getId();
		String role = remote.getRole();

		if (role != null && role.endsWith("Client")) {
			addServiceProject(remoteIp, remote.getType());
		} else if (role != null && role.endsWith("Server")) {
			addCallProject(remoteIp, remote.getType());
		}
	}

}
