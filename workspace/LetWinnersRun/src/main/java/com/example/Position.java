package com.example;

public class Position {
	
	String ticker;
	double numOfShares;
	double purchasePrice;
	
	public String getTicker() {
		return ticker;
	}
	public void setTicker(String ticker) {
		this.ticker = ticker;
	}
	public double getNumOfShares() {
		return numOfShares;
	}
	public void setNumOfShares(double numOfShares) {
		this.numOfShares = numOfShares;
	}
	public double getPurchasePrice() {
		return purchasePrice;
	}
	public void setPurchasePrice(double purchasePrice) {
		this.purchasePrice = purchasePrice;
	}
	
}
