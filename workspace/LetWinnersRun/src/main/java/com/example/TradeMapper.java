package com.example;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.jdbc.core.RowMapper;

public class TradeMapper implements RowMapper<Trade> {

	@Override
	public Trade mapRow(ResultSet rs, int rowNum) throws SQLException {
		Trade trade = new Trade();
		trade.setNotes(rs.getString("notes"));
		trade.setTicker(rs.getString("ticker"));
		trade.setTxnDate(rs.getDate("txn_date"));
		trade.setTxnPrice(rs.getDouble("txn_price"));
		trade.setTxnType(rs.getString("txn_type"));
		return trade;
	}

}
