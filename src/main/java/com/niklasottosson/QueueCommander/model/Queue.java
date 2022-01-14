package com.niklasottosson.QueueCommander.model;

import org.apache.commons.lang3.StringUtils;


/**
 * Created by malen on 2019-08-16.
 */
public class Queue {
    private String name;
    private int depth;
    private String lastPut;
    private String lastGet;

    public Queue(String name, int depth, String lastPut, String lastGet){
        this.name = name;
        this.depth = depth;
        this.lastPut = lastPut;
        this.lastGet = lastGet;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public String getLastPut() {
        return lastPut;
    }

    public void setLastPut(String lastPut) {
        this.lastPut = lastPut;
    }

    public String getLastGet() {
        return lastGet;
    }

    public void setLastGet(String lastGet) {
        this.lastGet = lastGet;
    }

    @Override
    public String toString() {
        return name;
    }

    public String getActionBoxLabel(int maxLength){
        return StringUtils.rightPad(this.getName(), maxLength) + " " + StringUtils.leftPad(String.valueOf(this.getDepth()),6) + "   " + this.getLastGet() + "   " + this.getLastPut() + "  ";
    }
}
