package com.exam.model;

public class Seat {
    private long id;
    private long roomId; // FK to Room
    private int seatNumber;
    private int row;     // X coordinate
    private int col;     // Y coordinate
    private boolean isOccupied; // Helper flag for algorithms

    public Seat(long id, long roomId, int seatNumber, int row, int col) {
        this.id = id;
        this.roomId = roomId;
        this.seatNumber = seatNumber;
        this.row = row;
        this.col = col;
        this.isOccupied = false;
    }

    public long getId() { return id; }
    public int getRow() { return row; }
    public int getCol() { return col; }
    public int getSeatNumber() { return seatNumber; }

    // We will use this during the allocation algorithm in memory
    public boolean isOccupied() { return isOccupied; }
    public void setOccupied(boolean occupied) { isOccupied = occupied; }

    @Override
    public String toString() {
        return "Seat " + seatNumber + " (R" + row + ":C" + col + ")";
    }
}