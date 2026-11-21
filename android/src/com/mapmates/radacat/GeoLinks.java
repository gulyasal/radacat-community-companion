package com.mapmates.radacat;
import java.util.Locale;
import java.util.regex.Pattern;
public final class GeoLinks {
    public static final Pattern PATTERN=Pattern.compile("geo:(-?\\d{1,2}\\.\\d{5}),(-?\\d{1,3}\\.\\d{5})");
    public static String coordinate(double latitude,double longitude){
        if(!Double.isFinite(latitude)||!Double.isFinite(longitude)||Math.abs(latitude)>90||Math.abs(longitude)>180)throw new IllegalArgumentException("Invalid coordinate");
        return String.format(Locale.US,"geo:%.5f,%.5f",latitude,longitude);
    }
    public static String url(String latitude,String longitude){
        double lat=Double.parseDouble(latitude),lon=Double.parseDouble(longitude);
        if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)return null;
        return "https://www.google.com/maps/search/?api=1&query="+latitude+","+longitude;
    }
}
