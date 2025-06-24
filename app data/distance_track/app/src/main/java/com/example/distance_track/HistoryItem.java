package com.example.distance_track;

public class HistoryItem {
    private String date;
    private double distance;

    public HistoryItem(String date, double distance) {
        this.date = date;
        this.distance = distance;
    }

    public String getDate() {
        return date;
    }

    public double getDistance() {
        return distance;
    }
}
