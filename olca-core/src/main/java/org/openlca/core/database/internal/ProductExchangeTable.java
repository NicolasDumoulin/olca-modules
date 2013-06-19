package org.openlca.core.database.internal;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openlca.core.database.IDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A look-up table for the creation of process links in product systems. The
 * look-up table is based on IDs. If these IDs are already contained in this
 * tables no queries are executed. Thus, there is no significant performance
 * lost if the load-methods in this class are called with the same IDs multiple
 * times.
 */
class ProductExchangeTable {

	private Logger log = LoggerFactory.getLogger(getClass());
	private Map<String, List<ProductExchange>> processInputs = new HashMap<>();
	private Map<String, List<ProductExchange>> outputsForFlow = new HashMap<>();
	private IDatabase database;

	private final String SELECT_EXCHANGE = "SELECT e.id, e.f_owner, e.f_flow, "
			+ "e.resultingamount_value, e.f_default_provider";

	public ProductExchangeTable(IDatabase database) {
		this.database = database;
	}

	/**
	 * Loads the product inputs of the processes with the given IDs into this
	 * table.
	 */
	public void loadProductInputs(Collection<String> processIds) {
		List<String> realIds = filterKeys(processInputs, processIds);
		if (realIds.isEmpty())
			return;
		log.trace("Load inputs for {} processes", realIds.size());
		String sql = SELECT_EXCHANGE + " FROM tbl_exchanges e "
				+ "INNER JOIN tbl_flows f on e.f_flow = f.id "
				+ "WHERE e.f_owner IN " + asSql(realIds)
				+ " AND e.input = 1 AND f.flowtype <> 0 ";
		List<ProductExchange> results = query(sql);
		for (ProductExchange result : results)
			put(result.getProcessId(), result, processInputs);
	}

	/** Loads the process outputs with the given flows into the table. */
	public void loadOutputsWithFlows(Collection<String> flowIds) {
		List<String> realIds = filterKeys(outputsForFlow, flowIds);
		if (realIds.isEmpty())
			return;
		log.trace("Load process outputs for {} flows", realIds.size());
		String sql = SELECT_EXCHANGE + " FROM tbl_exchanges e "
				+ "WHERE e.f_flow IN " + asSql(realIds) + " AND e.input = 0";
		List<ProductExchange> results = query(sql);
		for (ProductExchange result : results)
			put(result.getFlowId(), result, outputsForFlow);
	}

	private List<String> filterKeys(Map<String, ?> map,
			Collection<String> rawList) {
		List<String> filtered = new ArrayList<>();
		for (String rawKey : rawList) {
			if (map.containsKey(rawKey))
				continue;
			filtered.add(rawKey);
		}
		return filtered;
	}

	private List<ProductExchange> query(String sql) {
		try (Connection con = database.createConnection();
				ResultSet rs = con.createStatement().executeQuery(sql)) {
			List<ProductExchange> results = new ArrayList<>();
			while (rs.next()) {
				ProductExchange e = new ProductExchange();
				e.setAmount(rs.getDouble("resultingamount_value"));
				e.setDefaultProviderId(rs.getString("f_default_provider"));
				e.setFlowId(rs.getString("f_flow"));
				e.setId(rs.getString("id"));
				e.setProcessId(rs.getString("f_owner"));
				results.add(e);
			}
			return results;
		} catch (Exception e) {
			log.error("Failed to execute query", e);
			return Collections.emptyList();
		}
	}

	/** Get the product inputs of the process with the given ID. */
	public List<ProductExchange> getProductInputs(String processId) {
		return get(processInputs, processId);
	}

	/** Get the process outputs where the flow with the given ID is used. */
	public List<ProductExchange> getOutputsWithFlow(String flowId) {
		return get(outputsForFlow, flowId);
	}

	private List<ProductExchange> get(Map<String, List<ProductExchange>> map,
			String key) {
		List<ProductExchange> r = map.get(key);
		if (r == null)
			return Collections.emptyList();
		return r;
	}

	private void put(String key, ProductExchange result,
			Map<String, List<ProductExchange>> map) {
		List<ProductExchange> list = map.get(key);
		if (list == null) {
			list = new ArrayList<>();
			map.put(key, list);
		}
		if (!list.contains(result))
			list.add(result);
	}

	private String asSql(List<String> ids) {
		StringBuilder b = new StringBuilder();
		b.append('(');
		for (int i = 0; i < ids.size(); i++) {
			b.append('\'').append(ids.get(i)).append('\'');
			if (i < (ids.size() - 1))
				b.append(',');
		}
		b.append(')');
		return b.toString();
	}

}