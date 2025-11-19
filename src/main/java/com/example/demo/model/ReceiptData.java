package com.example.demo.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ReceiptData {
    private String storeName;
    private LocalDateTime datetime;
    private List<ReceiptItem> items;
    private int subtotal;
    private int tax;
    private int total;
    private String paymentMethod;
    
	public String getStoreName() {
		return storeName;
	}
	public void setStoreName(String storeName) {
		this.storeName = storeName;
	}
	public LocalDateTime getDatetime() {
		return datetime;
	}
	public void setDatetime(LocalDateTime datetime) {
		this.datetime = datetime;
	}
	public List<ReceiptItem> getItems() {
		return items;
	}
	public void setItems(List<ReceiptItem> items) {
		this.items = items;
	}
	public int getSubtotal() {
		return subtotal;
	}
	public void setSubtotal(int subtotal) {
		this.subtotal = subtotal;
	}
	public int getTax() {
		return tax;
	}
	public void setTax(int tax) {
		this.tax = tax;
	}
	public int getTotal() {
		return total;
	}
	public void setTotal(int total) {
		this.total = total;
	}
	public String getPaymentMethod() {
		return paymentMethod;
	}
	public void setPaymentMethod(String paymentMethod) {
		this.paymentMethod = paymentMethod;
	}
}
