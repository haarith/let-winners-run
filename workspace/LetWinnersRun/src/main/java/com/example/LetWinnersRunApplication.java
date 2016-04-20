package com.example;

import java.util.Date;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

@SpringBootApplication
public class LetWinnersRunApplication implements CommandLineRunner {
	
	private static final Logger log = LoggerFactory.getLogger(LetWinnersRunApplication.class);
	
	public static final int STARTING_CAPITAL = 100000;
	public static final int PORTFOLIO_SIZE = 20;
	public static final double MIN_COMMISSION = 1.0;
	public static final double COMMISSION_PER_SHARE = 0.005;
	public static final double SLIPPAGE_PERCENT = 0;
	public static final boolean letWinnersRun = true;
	public static final String letWinnersRunNotes = "Buy/Sell Difference";
	public static final String selectCountSql = "select count(*) from trades";
	public static final String selectAllDatesSql = "select distinct(txn_date) from trades order by 1 asc";
	public static final String selectStartDateSql = "select min(txn_date) from trades";
	public static final String selectEndDateSql = "select max(txn_date) from trades";
	public static final String selectCountGreaterDate= "select count(*) from trades where txn_date > ?";
	public static final String selectSellsForWeekSql = "select txn_date, ticker, txn_type, txn_price, notes from trades where trim(txn_type) = 'SELL' and txn_date = ?"; 
	public static final String selectBuysForWeekSql = "select txn_date, ticker, txn_type, txn_price, notes from trades where trim(txn_type) = 'BUY' and txn_date = ?"; 
	public static final String selectNumOfOpensForWeekSql = "select count(*) from trades where trim(txn_type) = 'BUY' and trim(notes) = '' and txn_date = ?"; 
	public static final int LAST_LOG_OUTPUT_WEEK = 5;
	
	@Autowired
	NamedParameterJdbcTemplate namedParameterJdbcTemplate;
	
	@Autowired
	JdbcTemplate jdbcTemplate;
//    JdbcTemplate jdbcTemplate = (JdbcTemplate) namedParameterJdbcTemplate.getJdbcOperations();
	
	Map<String, Integer> portfolio = new HashMap<String, Integer>();
	double netLiquidation;
	double portfolioCash = STARTING_CAPITAL;
	double securitiesGpv = 0;
	int numPositionsRemoved = 0;
	int negativePositionSizeCounter = 0;
	int weekNum = 0;
	
	public static void main(String[] args) {
		SpringApplication.run(LetWinnersRunApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		final Date simulationStartDate = readSimulationStartDate();
		final Date simulationEndDate = readSimulationEndDate();

		numOfTradesTest();
		thereAreMoreWeeksTest(simulationStartDate, simulationEndDate);
		getNextWeekStartDateTest(simulationStartDate);
		getNextWeekStartDateTest(getAncientDate());

		Date weekStartDate = getAncientDate();
		
		do {
			netLiquidation = portfolioCash + securitiesGpv;
			weekStartDate = getNextWeekStartDate(weekStartDate);
			weekNum++;
			if (weekNum <= LAST_LOG_OUTPUT_WEEK) log.info(">>>>>>>>>>>>>>>>Starting processing for week # " + weekNum + " starting on " + weekStartDate+"<<<<<<<<<<<<<<<<<<<");
			processSellTradesForWeek(weekStartDate);
			processBuyTradesForWeek(weekStartDate);
			if (weekNum % 100 == 0 || weekNum <= 10) {
				log.info("Week # " + weekNum + " Starting: " + weekStartDate + " Net Liquidation = " + netLiquidation + " Portfolio Cash = " + portfolioCash + " Securities GPV = " + securitiesGpv);
				displayPositionsInPortfolio();
			}
		}
		while(thereAreMoreWeeks(weekStartDate));

		//		ArrayList tradesList = getAllTradesFor(startDate);
		
	}

	private void displayPositionsInPortfolio() {
		// TODO Auto-generated method stub
		int posNum = 1;
		for (Map.Entry<String, Integer> position : portfolio.entrySet()) {
			log.info("Position # " + posNum + ": Ticker = " + position.getKey().trim() + "; No Of Shares = " + position.getValue());
			posNum++;
		}
	}

	private void processBuyTradesForWeek(Date weekStartDate) {
		List<Trade> tradesList = jdbcTemplate.query(selectBuysForWeekSql, new TradeMapper(), weekStartDate);
		int numOfOpens = jdbcTemplate.queryForObject(selectNumOfOpensForWeekSql, Integer.class, weekStartDate);
		if (null != tradesList) {
			double positionSize = portfolioCash/numOfOpens;
			if (positionSize < 0) {
				negativePositionSizeCounter++;
			}
			if (negativePositionSizeCounter < 5) {
				if (weekNum % 100 == 0 || weekNum <= 10) {
					log.info("Week # " + weekNum + " Starting: " + weekStartDate + " Position size = " + positionSize + " !!!");
				}
			}
			for (Trade trade:tradesList) {
				double txnPrice = trade.getTxnPrice();
				String ticker = trade.getTicker().trim();
				String notes = trade.getNotes().trim();
				boolean isRebalanceTxn = notes.equalsIgnoreCase(letWinnersRunNotes);
				String txnType = trade.getTxnType().trim();
				Date txnDate = trade.getTxnDate();
				if (weekNum <= LAST_LOG_OUTPUT_WEEK) log.info("Ticker="+ticker+"; Notes="+notes+"; Txn Type="+txnType+"; Txn Date="+txnDate+"; Txn Price: "+txnPrice);
				if (letWinnersRun && isRebalanceTxn) {
					if (weekNum <= LAST_LOG_OUTPUT_WEEK) log.info("Ignoring rebalance " + txnType + " txn for " + ticker);
					continue;
				}
				int numShares = (int) (positionSize/txnPrice);
				log.debug("Going to add " + ticker + " to portfolio");
				portfolio.put(ticker, numShares);
				double actualPositionSize = numShares * txnPrice;
				securitiesGpv += actualPositionSize;
				portfolioCash -= actualPositionSize;
				portfolioCash -= getCommission(numShares);
				portfolioCash -= getSlippage(actualPositionSize);
				if (weekNum <= LAST_LOG_OUTPUT_WEEK) log.info("Added " + numShares + " shares of " + ticker + " to portfolio; Cash remaining = "+portfolioCash);
			}
		}
	}

	private double getSlippage(double actualPositionSize) {
		return (actualPositionSize * SLIPPAGE_PERCENT);
	}

	private double getCommission(int numShares) {
		double commission = numShares * COMMISSION_PER_SHARE;
		if (commission < MIN_COMMISSION) {
			commission = MIN_COMMISSION;
		}
		return commission;
	}

	private void processSellTradesForWeek(Date weekStartDate) {
		numPositionsRemoved = 0;
		List<Trade> tradesList = jdbcTemplate.query(selectSellsForWeekSql, new TradeMapper(), weekStartDate);
		if (null != tradesList) {
			for (Trade trade:tradesList) {
				String notes = trade.getNotes().trim();
				boolean isRebalanceTxn = notes.equalsIgnoreCase(letWinnersRunNotes); 
				double txnPrice = trade.getTxnPrice();
				String ticker = trade.getTicker().trim();
				String txnType = trade.getTxnType().trim();
				Date txnDate = trade.getTxnDate();
				if (weekNum <= LAST_LOG_OUTPUT_WEEK) log.info("Ticker="+ticker+"; Notes="+notes+"; Txn Type="+txnType+"; Txn Date="+txnDate+"; Txn Price: "+txnPrice);
				if (letWinnersRun && isRebalanceTxn) {
					if (weekNum <= LAST_LOG_OUTPUT_WEEK) log.info("Ignoring rebalance " + txnType + " txn for " + ticker);
					continue;
				}
				log.debug("Going to remove " + ticker + " from portfolio");
				int numShares = portfolio.remove(ticker);
				numPositionsRemoved++;
				double actualPositionSize = numShares * txnPrice;
				securitiesGpv -= actualPositionSize;
				portfolioCash += actualPositionSize;
				portfolioCash -= getCommission(numShares);
				portfolioCash -= getSlippage(actualPositionSize);
				if (weekNum <= LAST_LOG_OUTPUT_WEEK) log.info("Removed " + numShares + " shares of " + ticker + " from portfolio; Cash remaining = "+portfolioCash);
			}
		}
	}

	private Date getAncientDate() {
		log.debug("Inside com.example.LetWinnersRunApplication.getAncientDate()");
		Calendar calendar = Calendar.getInstance();
		calendar.setTimeInMillis(0);
		Date ancientDate = calendar.getTime();
		log.debug("Ancient Date = " + ancientDate);
		return ancientDate;
	}

	private Date readSimulationEndDate() {
		log.debug("Inside com.example.LetWinnersRunApplication.readSimulationEndDate()");
		Date endDate = jdbcTemplate.queryForObject(selectEndDateSql, Date.class);
		log.debug("Simulation end date = " + endDate);
		return endDate;
	}

	private void getNextWeekStartDateTest(Date inputDate) {
		log.debug("Inside com.example.LetWinnersRunApplication.getNextWeekStartDateTest()");
		log.debug("Next date after " + inputDate + " is : " + getNextWeekStartDate(inputDate));
	}

	private Date getNextWeekStartDate(Date weekStartDate) {
		return jdbcTemplate.queryForObject("select min(txn_date) from trades where txn_date > ?", Date.class, weekStartDate);
	}

	private void processWeek(Date weekStartDate) {
		// TODO Auto-generated method stub
	}

	private Date readSimulationStartDate() {
		log.debug("Inside com.example.LetWinnersRunApplication.readSimulationStartDate()");
		Date startDate = jdbcTemplate.queryForObject(selectStartDateSql, Date.class);
		log.debug("Simulation start date = " + startDate);
		return startDate;
	}

	private void numOfTradesTest() {
		log.debug("Inside com.example.LetWinnersRunApplication.numOfTradesTest()");
		int numOfTrades = jdbcTemplate.queryForObject(selectCountSql, Integer.class);
		log.debug("Number of trades in the input file = " + numOfTrades);
	}

	private void thereAreMoreWeeksTest(Date simulationStartDate, Date simulationEndDate) {
		log.debug("Inside com.example.LetWinnersRunApplication.thereAreMoreWeeksTest()");
		
		int numHigherWeeks = jdbcTemplate.queryForObject(selectCountGreaterDate, Integer.class, simulationStartDate);
		log.debug("There are " + numHigherWeeks + " records in the DB with txn_date > than " + simulationStartDate);
		log.debug("Are there more weeks after " + simulationStartDate + "? " + thereAreMoreWeeks(simulationStartDate));

		int shouldBeZero = jdbcTemplate.queryForObject(selectCountGreaterDate, Integer.class, simulationEndDate);
		log.debug("There are " + shouldBeZero + " records in the DB with txn_date > than " + simulationEndDate);
		log.debug("Are there more weeks after " + simulationEndDate + "? " + thereAreMoreWeeks(simulationEndDate));
	}

	/**
	 * Check whether there are any more weeks in the simulation data
	 * @param weekStartDate
	 * @return
	 */
	private boolean thereAreMoreWeeks(Date weekStartDate) {
		int numHigherWeeks = jdbcTemplate.queryForObject(selectCountGreaterDate, Integer.class, weekStartDate);
		if (numHigherWeeks > 0) {
			return true;
		}
		return false;
	}

	private ArrayList getAllTradesFor(Date startDate) {
		// TODO Auto-generated method stub
		String selectTradesForDateSql = "select ticker, txn_type, txn_price, notes from trades where txn_date = :txn_date and txn_type = :txn_type";
		MapSqlParameterSource namedParameters = new MapSqlParameterSource("txn_date", startDate);
		namedParameters.addValue("txn_type", "SELL");
		return null;
	}

	/**
	 * Read the distinct list of dates from the DB in ascending order
	 */
	public void readAllDates() {
		// TODO Auto-generated method stub
		ArrayList<Date> distinctDatesList = (ArrayList<Date>) jdbcTemplate.queryForList(selectAllDatesSql, Date.class);
	}
}
