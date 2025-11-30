package com.exam.model;

public class Allocation {
    private long examId;
    private long studentId;
    private long seatId;

    public Allocation(long examId, long studentId, long seatId) {
        this.examId = examId;
        this.studentId = studentId;
        this.seatId = seatId;
    }

    public long getExamId() { return examId; }

    public long getStudentId() { return studentId; }
    public long getSeatId() { return seatId; }

    @Override
    public String toString() {
        return "Allocation{Exam=" + examId + ", Student=" + studentId + ", Seat=" + seatId + "}";
    }
}