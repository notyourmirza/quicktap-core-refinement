package com.quicktap.pos.data.model;

/** Aggregate row used by the dashboard and report cards. */
public class DaySummary {
    public int orders;
    public double revenue;
    public int paidCount;
    public int unpaidCount;
    public double unpaidAmount;
}
