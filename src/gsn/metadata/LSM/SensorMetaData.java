package gsn.metadata.LSM;

import gsn.utils.PropertiesReader;

public class SensorMetaData {

    public String getSensorName() {
        return sensorName;
    }

    public String getAuthor() {
        return author;
    }

    public String getSensorType() {
        return sensorType;
    }

    public String getInformation() {
        return information;
    }

    public String getSourceType() {
        return sourceType;
    }

    private String sensorName;
    private String author;
    private String sensorType;
    private String information;
    private String sourceType;

    public String toString() {
        return "SensorMetaData{" +
                "sensorName='" + sensorName + '\'' +
                ", author='" + author + '\'' +
                ", sensorType='" + sensorType + '\'' +
                ", information='" + information + '\'' +
                ", sourceType='" + sourceType + '\'' +
                '}';
    }

    public void setSensorName(String sensorName) {
        this.sensorName = sensorName;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public void setSensorType(String sensorType) {
        this.sensorType = sensorType;
    }

    public void setInformation(String information) {
        this.information = information;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public void initFromFile(String fileName) {
        this.setSensorName(PropertiesReader.readProperty(fileName, "sensorName"));
        this.setAuthor(PropertiesReader.readProperty(fileName, "author"));
        this.setInformation(PropertiesReader.readProperty(fileName, "information"));
        this.setSensorType(PropertiesReader.readProperty(fileName, "sensorType"));
        this.setSourceType(PropertiesReader.readProperty(fileName, "sourceType"));
    }

}

