package com.tripplanning.transport;

public final class TransportRoutes {

    private TransportRoutes() {}

    public static String format(String startAddress, String endAddress) {
        String start =
                startAddress != null && !startAddress.isBlank() ? startAddress.trim() : "Start";
        String end = endAddress != null && !endAddress.isBlank() ? endAddress.trim() : "End";
        return start + " → " + end;
    }
}
