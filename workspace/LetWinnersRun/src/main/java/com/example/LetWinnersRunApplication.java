package com.example;

import java.util.Date;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@SpringBootApplication
public class LetWinnersRunApplication implements CommandLineRunner {
	
	private static final Logger log = LoggerFactory.getLogger(LetWinnersRunApplication.class);
	
	@Value("${let.winners.run}")
	private boolean letWinnersRun;
	
	@Value("${starting.capital}")
	private double startingCapital;
	
	@Value("${minimum.commission.amount}")
	private double minCommissionAmt;
	
	@Value("${maximum.commission.percent}")
	private double maxCommissionPct;
	
	@Value("${commission.per.share}")
	private double commissionPerShare = 0.005;//$ per share
	
	@Value("${slippage.percent}")
	private double slippagePct;

	//The PORTFOLIO_SIZE is pretty much set in stone; not to be tampered lightly
	public static final int PORTFOLIO_SIZE = 20;
	
	public static final String letWinnersRunNotes = "Buy/Sell Difference";
	public static final String selectCountSql = "select count(*) from trades";
	public static final String selectAllDatesSql = "select distinct(txn_date) from trades order by 1 asc";
	public static final String selectStartDateSql = "select min(txn_date) from trades";
	public static final String selectEndDateSql = "select max(txn_date) from trades";
	public static final String selectCountGreaterDate= "select count(*) from trades where txn_date > ?";
	public static final String selectSellsForWeekSql = "select txn_date, ticker, txn_type, txn_price, notes from trades where trim(txn_type) = 'SELL' and txn_date = ?"; 
	public static final String selectPartialBuysForWeekSql = "select txn_date, ticker, txn_type, txn_price, notes from trades where trim(txn_type) = 'BUY' and trim(notes) = 'Buy/Sell Difference' and txn_date = ?"; 
	public static final String selectNewBuysForWeekSql = "select txn_date, ticker, txn_type, txn_price, notes from trades where trim(txn_type) = 'BUY' and trim(notes) <> 'Buy/Sell Difference' and txn_date = ?"; 
	public static final String selectTradesForWeekSql = "select txn_date, ticker, txn_type, txn_price, notes from trades where txn_date = ?"; 
	public static final String selectNumOfOpensForWeekSql = "select count(*) from trades where trim(txn_type) = 'BUY' and trim(notes) = '' and txn_date = ?"; 
	public static final int LAST_LOG_OUTPUT_WEEK = 0;
	public static final int logPrintInterval = 52;
	
	@Autowired
	NamedParameterJdbcTemplate namedParameterJdbcTemplate;
	
	@Autowired
	JdbcTemplate jdbcTemplate;
//    JdbcTemplate jdbcTemplate = (JdbcTemplate) namedParameterJdbcTemplate.getJdbcOperations();
	
	Map<String, Position> portfolio = new HashMap<String, Position>();
	double netLiquidation;
	double portfolioCash;
	double securitiesGpv = 0;
//	int numPositionsRemoved = 0;
	int negativePositionSizeCounter = 0;
	int weekNum = 0;
	double totalCommissionPaid = 0;
	double totalSlippagePaid = 0;
	double idealPositionSize;
	double letWinnersRunBuyAmount = 0;
	Date simulationStartDate = new Date();
	Date simulationEndDate = new Date();
	
	public static void main(String[] args) {
		SpringApplication.run(LetWinnersRunApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		simulationStartDate = readSimulationStartDate();
		simulationEndDate = readSimulationEndDate();
		runInitialTests();
		
		idealPositionSize = startingCapital/PORTFOLIO_SIZE;
		portfolioCash = startingCapital;
		log.info("letWinnersRun = " + letWinnersRun);
		log.info("STARTING_CAPITAL = " + startingCapital);
		log.info("idealPositionSize = " + idealPositionSize);
		log.info("portfolioCash = " + portfolioCash);
		log.info("MIN_COMMISSION_AMT = " + minCommissionAmt);
		log.info("MAX_COMMISSION_PERCENT = " + maxCommissionPct);
		log.info("COMMISSION_PER_SHARE = " + commissionPerShare);
		log.info("SLIPPAGE_PERCENT = " + slippagePct);

		Date weekStartDate = getAncientDate();
		do {
			weekStartDate = getNextWeekStartDate(weekStartDate);
			weekNum++;
			if (weekNum <= LAST_LOG_OUTPUT_WEEK) log.info(">>>>>>>>>>>>>>>>Starting processing for week # " + weekNum + " starting on " + weekStartDate+"<<<<<<<<<<<<<<<<<<<");
			if (weekNum > 1) {
				updateLastKnownPrices(weekStartDate);
				processSellTradesForWeek(weekStartDate);
			}
			processNewBuysForWeek(weekStartDate);
			if (!letWinnersRun && weekNum > 1) {
				processPartialBuysForWeek(weekStartDate);
			}
			securitiesGpv = getSecuritiesGpv();
			netLiquidation = portfolioCash + securitiesGpv;
			if (weekNum % logPrintInterval == 0 || weekNum <= 5) {
				Formatter f = new Formatter();
				f.format("Week # %d Starting: %tY-%tm-%td; Net Liquidation = %,.0f; Cash = %.0f; Securities = %,.0f", weekNum, weekStartDate, weekStartDate, weekStartDate, netLiquidation, portfolioCash, securitiesGpv);
				String logMessage = f.toString();
				f.close();
				log.info(logMessage);
//				displayPositionsInPortfolio();
			}
		}
		while(thereAreMoreWeeks(weekStartDate));
		double netPctGain = (netLiquidation - startingCapital)*100/startingCapital;
		double yearsElapsed = ((double)weekNum)/52;
//		log.info("LetWinnersRun = " + letWinnersRun + "; Net % gain = " + netPctGain + " In " + yearsElapsed + " years");
		
		Formatter f = new Formatter();
		f.format("LetWinnersRun = %s; Net Gain = %,.0f%% in %,.1f years with Slippage = %,.1f%% per trade", letWinnersRun, netPctGain, yearsElapsed, slippagePct);
		String logMessage = f.toString();
		log.info(logMessage);
		f.close();
		
		f = new Formatter();
		double pctROI = 100* (Math.pow((1+(netPctGain/100.0)), (1/yearsElapsed)) - 1);
		f.format("LetWinnersRun = %s; ROI = %.1f%%; Net Liquidation = %,.0f; Cash = %,.0f; Securities = %,.0f", letWinnersRun, pctROI, netLiquidation, portfolioCash, securitiesGpv);
		logMessage = f.toString();
		log.info(logMessage);
		f.close();

		f = new Formatter();
		f.format("LetWinnersRun = %s; Commission costs = %,.0f; Slippage costs = %,.0f", letWinnersRun, totalCommissionPaid, totalSlippagePaid);
		logMessage = f.toString();
		log.info(logMessage);
		f.close();

		//		ArrayList tradesList = getAllTradesFor(startDate);
	}

	private void runInitialTests() {
		numOfTradesTest();
		thereAreMoreWeeksTest(simulationStartDate, simulationEndDate);
		getNextWeekStartDateTest(simulationStartDate);
		getNextWeekStartDateTest(getAncientDate());
	}

	private void updateLastKnownPrices(Date weekStartDate) {
		List<Trade> tradesList = jdbcTemplate.query(selectTradesForWeekSql, new TradeMapper(), weekStartDate);
		if (null != tradesList && null != portfolio) {
			if (weekNum == 1) {
				log.info("Updating Last Known Prices for Week 1");
				log.info("Portfolio before Updating Last Known Prices = " + portfolio);
			}
			for (Trade trade:tradesList) {
				double txnPrice = trade.getTxnPrice();
				String ticker = trade.getTicker().trim();
				for (Map.Entry<String, Position> entry : portfolio.entrySet()) {
					String positionTicker = entry.getKey().trim();
					if (positionTicker.equalsIgnoreCase(ticker)) {
						Position position = entry.getValue();
						position.setLastKnownPrice(txnPrice);
						portfolio.replace(positionTicker, position);
					}
				}
			}
			if (weekNum == 1) {
				log.info("Week # 1: Portfolio after Updating Last Known Prices = " + portfolio);
				displayPositionsInPortfolio();
			}
		}
		securitiesGpv = getSecuritiesGpv();
		netLiquidation = portfolioCash + securitiesGpv;
		idealPositionSize = netLiquidation/PORTFOLIO_SIZE;
	}

	private double getSecuritiesGpv() {
		double securitiesGpv = 0;
		for (Map.Entry<String, Position> entry : portfolio.entrySet()) {
			Position position = entry.getValue();
			securitiesGpv += position.getNumOfShares() * position.getLastKnownPrice();
		}
		return securitiesGpv;
	}

	private void displayPositionsInPortfolio() {
		int posNum = 1;
		for (Map.Entry<String, Position> entry : portfolio.entrySet()) {
			String ticker = entry.getKey().trim();
			Position position = entry.getValue();
			int numOfShares = position.getNumOfShares();
			double purchasePrice = position.getPurchasePrice();
			log.info("Week # " + weekNum + " Position # " + posNum + ": Ticker = " + ticker + "; No Of Shares = " + numOfShares + "; Purchase Price = " + purchasePrice);
			posNum++;
		}
	}

	/**
	 * This should first process all the new position Open trades.
	 * Only then should it process the rebalance buy trades.
	 * Before each buy it should ensure that the buy amount (plus commission and slippage) is not more than the remaining cash at hand.
	 * @param weekStartDate
	 */
	private void processNewBuysForWeek(Date weekStartDate) {
		List<Trade> tradesList = jdbcTemplate.query(selectNewBuysForWeekSql, new TradeMapper(), weekStartDate);
		int numBuys = tradesList.size();
		letWinnersRunBuyAmount = portfolioCash/numBuys;
		if (null != tradesList) {
			for (Trade trade:tradesList) {
				double txnPrice = trade.getTxnPrice();
				String ticker = trade.getTicker().trim();
				String txnType = trade.getTxnType().trim();
				Date txnDate = trade.getTxnDate();
				String notes = trade.getNotes().trim();
				if (weekNum <= LAST_LOG_OUTPUT_WEEK) log.info("Ticker="+ticker+"; Notes="+notes+"; Txn Type="+txnType+"; Txn Date="+txnDate+"; Txn Price: "+txnPrice);
				addPositionToPortfolio(ticker, txnPrice);
			}
		}
	}
	
	private void processPartialBuysForWeek(Date weekStartDate) {
		List<Trade> tradesList = jdbcTemplate.query(selectPartialBuysForWeekSql, new TradeMapper(), weekStartDate);
		if (null != tradesList) {
			for (Trade trade:tradesList) {
				double txnPrice = trade.getTxnPrice();
				String ticker = trade.getTicker().trim();
				String txnType = trade.getTxnType().trim();
				Date txnDate = trade.getTxnDate();
				String notes = trade.getNotes().trim();
				if (weekNum <= LAST_LOG_OUTPUT_WEEK) log.info("Ticker="+ticker+"; Notes="+notes+"; Txn Type="+txnType+"; Txn Date="+txnDate+"; Txn Price: "+txnPrice);
				increasePositionInPortfolio(ticker, txnPrice);
			}
		}
	}

	private void increasePositionInPortfolio(String ticker, double txnPrice) {
		Position position = portfolio.get(ticker);
		double priorPurchasePrice = position.getPurchasePrice();
		int priorNumOfShares = position.getNumOfShares();
		double amountToBuy = idealPositionSize - (priorNumOfShares * txnPrice);
		if (amountToBuy < 0) {
			negativePositionSizeCounter++;
			log.info("Negative trade amount found!!!");
			return;
		}
		
		//first pass at calculating numShares, commission, and slippage
		int numShares = (int) (amountToBuy/txnPrice);
		double actualTradeSize = numShares * txnPrice;
		double commission = getCommission(numShares, txnPrice);
		double slippage = getSlippage(actualTradeSize);
		
		//recalculate numShares so that subtracting commission & slippage doesn't make portfolioCash go negative
		actualTradeSize = actualTradeSize - commission - slippage;
		numShares = (int) (actualTradeSize/txnPrice);
		actualTradeSize = numShares * txnPrice;
		commission = getCommission(numShares, txnPrice);
		slippage = getSlippage(actualTradeSize);
		
		//ensure that the actualPositionSize+commission+slippage < portfolioCash
		if ((actualTradeSize + commission + slippage) >= portfolioCash) {
			actualTradeSize = portfolioCash - commission - slippage;
			numShares = (int) (actualTradeSize/txnPrice);
			actualTradeSize = numShares * txnPrice;
			commission = getCommission(numShares, txnPrice);
			slippage = getSlippage(actualTradeSize);
		}

		position.setNumOfShares(numShares + priorNumOfShares);
		position.setPurchasePrice(priorPurchasePrice);
		position.setLastKnownPrice(txnPrice);
		position.setTicker(ticker);
		portfolio.put(ticker, position);
		
		securitiesGpv += actualTradeSize;
		portfolioCash = portfolioCash - actualTradeSize - commission - slippage;
		totalCommissionPaid += commission;
		totalSlippagePaid += slippage;
		if (weekNum <= LAST_LOG_OUTPUT_WEEK) log.info("Added " + numShares + " shares of " + ticker + " to portfolio; Cash remaining = " + portfolioCash);
}

	private void addPositionToPortfolio(String ticker, double txnPrice) {
		log.debug("Going to add " + ticker + " to portfolio");
		double amountToBuy;
		if (letWinnersRun) {
			amountToBuy = letWinnersRunBuyAmount;
		}
		else {
			amountToBuy = idealPositionSize;
		}
		if (amountToBuy < 0) {
			negativePositionSizeCounter++;
			log.info("Negative trade amount found!!!");
			return;
		}

		//first pass at calculating numShares, commission, and slippage
		int numShares = (int) (amountToBuy/txnPrice);
		double actualTradeSize = numShares * txnPrice;
		double commission = getCommission(numShares, txnPrice);
		double slippage = getSlippage(actualTradeSize);
		
		//recalculate numShares so that subtracting commission & slippage doesn't make portfolioCash go negative
		actualTradeSize = actualTradeSize - commission - slippage;
		numShares = (int) (actualTradeSize/txnPrice);
		actualTradeSize = numShares * txnPrice;
		commission = getCommission(numShares, txnPrice);
		slippage = getSlippage(actualTradeSize);
		
		//ensure that the actualPositionSize+commission+slippage < portfolioCash
		if ((actualTradeSize + commission + slippage) >= portfolioCash) {
			actualTradeSize = portfolioCash - commission - slippage;
			numShares = (int) (actualTradeSize/txnPrice);
			actualTradeSize = numShares * txnPrice;
			commission = getCommission(numShares, txnPrice);
			slippage = getSlippage(actualTradeSize);
		}

		Position position = new Position();
		position.setNumOfShares(numShares);
		position.setPurchasePrice(txnPrice);
		position.setLastKnownPrice(txnPrice);
		position.setTicker(ticker);
		portfolio.put(ticker, position);
		
		securitiesGpv += actualTradeSize;
		portfolioCash = portfolioCash - actualTradeSize - commission - slippage;
		totalCommissionPaid += commission;
		totalSlippagePaid += slippage;
		if (weekNum <= LAST_LOG_OUTPUT_WEEK) log.info("Added " + numShares + " shares of " + ticker + " to portfolio; Cash remaining = " + portfolioCash);
	}

	private double getSlippage(double actualPositionSize) {
		return (actualPositionSize * slippagePct)/100;
	}

	private double getCommission(int numShares, double txnPrice) {
		double actualPositionSize = numShares * txnPrice;
		double commission = numShares * commissionPerShare;
		if (commission < minCommissionAmt) {
			commission = minCommissionAmt;
		}
		if (commission > (maxCommissionPct * actualPositionSize)/100) {
			commission = (maxCommissionPct * actualPositionSize)/100;
		}
		return commission;
	}

	private void processSellTradesForWeek(Date weekStartDate) {
//		numPositionsRemoved = 0;
		List<Trade> tradesList = jdbcTemplate.query(selectSellsForWeekSql, new TradeMapper(), weekStartDate);
		if (null != tradesList) {
			if (weekNum == 1) {
				log.info("Trades List Not NULL for Week 1 !!!");
			}
			for (Trade trade:tradesList) {
				double txnPrice = trade.getTxnPrice();
				String ticker = trade.getTicker().trim();
				String txnType = trade.getTxnType().trim();
				Date txnDate = trade.getTxnDate();
				String notes = trade.getNotes().trim();
				if (weekNum <= LAST_LOG_OUTPUT_WEEK) log.info("Ticker="+ticker+"; Notes="+notes+"; Txn Type="+txnType+"; Txn Date="+txnDate+"; Txn Price: "+txnPrice);

				boolean isRebalanceTxn = notes.equalsIgnoreCase(letWinnersRunNotes); 
				if (letWinnersRun && isRebalanceTxn) {
					if (weekNum <= LAST_LOG_OUTPUT_WEEK) log.info("Ignoring rebalance " + txnType + " txn for " + ticker);
					continue;
				}
				if (isRebalanceTxn) {
					decreasePositionInPortfolio(ticker, txnPrice);
					continue;
				}
				
				removeFullPositionFromPortfolio(ticker, txnPrice);
			}
		}
	}

	private void decreasePositionInPortfolio(String ticker, double txnPrice) {
		Position position = portfolio.get(ticker);
		int numOfShares = position.getNumOfShares();
		double beforePositionSize = numOfShares * position.getLastKnownPrice();
		double amountToSell = beforePositionSize - idealPositionSize;
		int sharesToSell = (int) (amountToSell/txnPrice);
		double commission = getCommission(sharesToSell, txnPrice);
		
		amountToSell = sharesToSell * txnPrice;
		double slippage = getSlippage(amountToSell);
		
		portfolioCash = portfolioCash + amountToSell - commission - slippage;
		totalCommissionPaid += commission;
		position.setNumOfShares(numOfShares - sharesToSell);
		portfolio.put(ticker, position);
	}

	private void removeFullPositionFromPortfolio(String ticker, double txnPrice) {
		log.debug("Going to remove " + ticker + " from portfolio");
		Position position = portfolio.remove(ticker);
//		numPositionsRemoved++;
		int numShares = position.getNumOfShares();
		double actualPositionSize = numShares * txnPrice;
		securitiesGpv -= actualPositionSize;
		portfolioCash += actualPositionSize;
		double commission = getCommission(numShares, txnPrice);
		portfolioCash -= commission;
		totalCommissionPaid += commission;
		portfolioCash -= getSlippage(actualPositionSize);
		if (weekNum <= LAST_LOG_OUTPUT_WEEK) log.info("Removed " + numShares + " shares of " + ticker + " from portfolio; Cash remaining = "+portfolioCash);
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

	@SuppressWarnings({ "unused", "rawtypes" })
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
		@SuppressWarnings("unused")
		ArrayList<Date> distinctDatesList = (ArrayList<Date>) jdbcTemplate.queryForList(selectAllDatesSql, Date.class);
	}
}
