package gsn.metadata.LSM;

import java.util.Date;

public class SensorObservation {
    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    String unit;
    String propertyName;
    double value;

    public Date getTime() {
        return time;
    }

    public void setTime(Date time) {
        this.time = time;
    }

    Date time;



    public String toString() {
        return "SensorObservation{" +
                "unit='" + unit + '\'' +
                ", propertyName='" + propertyName + '\'' +
                ", value=" + value +
                '}';
    }
}

