package com.exam.model;

public class Room {
    private long id;
    private String roomNumber;
    private int capacity;
    private int rows;
    private int cols;

    public Room(long id, String roomNumber, int capacity, int rows, int cols) {
        this.id = id;
        this.roomNumber = roomNumber;
        this.capacity = capacity;
        this.rows = rows;
        this.cols = cols;
    }

    public long getId() { return id; }
    public String getRoomNumber() { return roomNumber; }
    public int getCapacity() { return capacity; }
    public int getRows() { return rows; }
    public int getCols() { return cols; }
}