package gsn.utils;

import org.apache.log4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class PropertiesReader {

    private static final transient Logger logger = Logger.getLogger(PropertiesReader.class);

    public static String readProperty(String fileName, String propertyName) {
        Properties prop = new Properties();

        String output = null;

        try {
            prop.load(new FileInputStream(fileName));
            output = prop.getProperty(propertyName);

        } catch (IOException ex) {
            logger.error(new StringBuilder("Can't read property \"")
                    .append(propertyName)
                    .append("\" from file ")
                    .append(fileName));
        }
        return output;
    }
}
