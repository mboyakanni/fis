package it.niedermann.fis.weather;

import java.util.Objects;

public class WeatherInformationDto {
    public float temperature;
    public String icon;
    public boolean isDay;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WeatherInformationDto that = (WeatherInformationDto) o;
        return Float.compare(that.temperature, temperature) == 0 && isDay == that.isDay && Objects.equals(icon, that.icon);
    }

    @Override
    public int hashCode() {
        return Objects.hash(temperature, icon, isDay);
    }
}
