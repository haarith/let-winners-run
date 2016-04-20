package com.example;

import java.util.Date;

public class Trade {
	Date txnDate;
	String ticker;
	String txnType;
	double txnPrice;
	String notes;
	
	public Date getTxnDate() {
		return txnDate;
	}
	public void setTxnDate(Date txnDate) {
		this.txnDate = txnDate;
	}
	public String getTicker() {
		return ticker;
	}
	public void setTicker(String ticker) {
		this.ticker = ticker;
	}
	public String getTxnType() {
		return txnType;
	}
	public void setTxnType(String txnType) {
		this.txnType = txnType;
	}
	public double getTxnPrice() {
		return txnPrice;
	}
	public void setTxnPrice(double txnPrice) {
		this.txnPrice = txnPrice;
	}
	public String getNotes() {
		return notes;
	}
	public void setNotes(String notes) {
		this.notes = notes;
	}
	
}
