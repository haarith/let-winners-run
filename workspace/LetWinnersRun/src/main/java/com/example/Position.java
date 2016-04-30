package com.example;

public class Position {
	
	String ticker;
	int numOfShares;
	
	//This is the price at which the position is opened
	double purchasePrice;
	
	//This is the price at which a partial position was last bought/sold during a rebalance
	double lastKnownPrice;
	
	public String getTicker() {
		return ticker;
	}
	public void setTicker(String ticker) {
		this.ticker = ticker;
	}
	public int getNumOfShares() {
		return numOfShares;
	}
	public void setNumOfShares(int numOfShares) {
		this.numOfShares = numOfShares;
	}
	public double getPurchasePrice() {
		return purchasePrice;
	}
	public void setPurchasePrice(double purchasePrice) {
		this.purchasePrice = purchasePrice;
	}
	public double getLastKnownPrice() {
		return lastKnownPrice;
	}
	public void setLastKnownPrice(double lastKnownPrice) {
		this.lastKnownPrice = lastKnownPrice;
	}
	
}
