package com.example.demo.dao;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Coordinate {
    private Double x; // 경도 (127.xxx)
    private Double y; // 위도 (37.xxx)
    
 // 직접 생성자 추가
    public Coordinate(Double x, Double y) {
        this.x = x;
        this.y = y;
    }
    
	public Double getX() {
		return x;
	}
	public void setX(Double x) {
		this.x = x;
	}
	public Double getY() {
		return y;
	}
	public void setY(Double y) {
		this.y = y;
	}
}