package com.maxonaddon.util;

// Обрати внимание: здесь должно быть слово INTERFACE, а не class!
public interface LegCutState {
    boolean isLegCut();
    void setLegCut(boolean value);
    int getLegCutTimer();
}