package gsn.processor;

import groovy.lang.*;
import gsn.beans.DataField;
import gsn.beans.StreamElement;
import gsn.vsensor.AbstractVirtualSensor;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.util.HashMap;
import java.util.TreeMap;

/**
 * This Processor (processing class) executes a scriptlet upon reception of a new  StreamElement and can be used to
 * implement arbitrary complex processing class by specifying its logic directly in the virtual sensor description file.
 * This is especially useful for setting up flexible, complex and DBMS independent calibration functions.
 * The current implementation supports the Groovy (http://groovy.codehaus.org) scripting language.
 *
 * Data Binding
 * ------------
 * The current implementation automatically binds the data between the StreamElement and the variables of the scriptlet.
 * The binding is based on the mapping between the StreamElement data field names, and the scriptlet variable names.
 *
 * Before executing the scriptlet, all the fields from the StreamElement received are binded to the scriptlet.
 * The scriptlet is then executed and could use both variables hard-coded or dynamically binded from the StreamElement.
 * Once the scriptlet execution is done, a new StreamElement matching the output sctructure defined in the virtual
 * sensor description file is created. The data of this StreamElement are binded from all the variables binded to the
 * scriptlet. If a field name exists in the output sctructure and no variable match it in the scriptlet, then its value
 * is set to null.
 *
 * State
 * -----
 *
 * In order to save the state of a variable for the next evaluation, you can use the following code in your script:
 *
 * // The following fonction must be included once
 * def isdef(var) {
 *   (binding.getVariables().containsKey(var))
 * }
 *
 * // Then you can update or initialize the variable like below:
 * 
 * statefulCounter = isdef('statefulCounter') ? statefulCounter + 1 : 0;
 * 
 * 
 * PREDEFINED VARIABLES
 * --------------------
 *
 * The following variable is accessible directly in the scriptlet:
 * 
 * 1. groovy.lang.Binding binding                This contains the variables binded to your scriptlet.
 *
 * PROCESSING CLASS INIT-PARAMETERS
 * --------------------------------
 * 1. scriptlet, String, mandatory
 * Contains the content of your script.
 * 2. persistant, boolean, optional
 * Sets wether or not a StreamElement is created and stored at the end of the scriplet execution.
 *
 * 
 * LIMITATIONS
 * -----------
 * 
 * 1. The variables names binded into the script are uppercase.
 */
public class ScriptletProcessor extends AbstractVirtualSensor {

    private static final transient Logger logger = Logger.getLogger(ScriptletProcessor.class);

    /**
     * This holds the compiled class which contains the scriplet logic.
     */
    protected Script script = null;

    /**
     * This variable set if the StreamElement generated at the end of the script should be stored or not.
     * By default, it does persist.
     */
    protected boolean persistant = true;

    protected DataField[] outputStructure = null;

    /**
     * This cache hold a mapping between <code>StreamElementComparator</code> and <code>Binding</code>.
     */
    private HashMap<StreamElementComparator, Binding> cache = null;

    private static final int MAX_CACHE_SIZE = 10;

    @Override
    public boolean initialize() {
        return initialize(
                getVirtualSensorConfiguration().getOutputStructure(),
                getVirtualSensorConfiguration().getMainClassInitialParams(),
                new HashMap<StreamElementComparator, Binding>()
        );
    }

    @Override
    public void dispose() {}

    @Override
    public void dataAvailable(String inputStreamName, StreamElement se) {
        // Execute the scriptlet
        Binding context = getContext(se);
        evaluate(script, context);
        if (persistant) {
            // Build the StreamElement to be posted, from the OutputStructure defined in the Virtual Sensor description file.
            StreamElement seo = formatOutputStreamElement(context);
            dataProduced(seo);
        }
    }

    protected boolean initialize(DataField[] outputStructure, TreeMap<String, String> parameters, HashMap<StreamElementComparator, Binding> cache) {
        this.cache = cache;
        //
        if (outputStructure == null) {
            logger.warn("Failed to initialize the processing class because the outputStructure is null.");
            return false;
        } else
            this.outputStructure = outputStructure;

        // Mandatory Parameters

        String scriptlet = parameters.get("scriptlet");
        if (scriptlet == null) {
            logger.warn("The Initial Parameter >scriptlet< MUST be provided in the configuration file for the processing class.");
            return false;
        }
        GroovyShell shell = new GroovyShell();
        try {
            script = shell.parse(scriptlet);
        }
        catch (Exception e) {
            logger.warn("Failed to compile the scriptlet " + e.getMessage());
            return false;
        }
        logger.info("scriptlet: " + scriptlet);

        // Optional Parameters

        if (parameters.containsKey("persistant")) {
            persistant = Boolean.parseBoolean(parameters.get("persistant"));
        }
        logger.info("persistant: " + persistant);

        return true;
    }

    protected StreamElement formatOutputStreamElement(Binding binding) {
        Serializable[] data = new Serializable[outputStructure.length];
        for (int i = 0; i < outputStructure.length; i++) {
            DataField df = outputStructure[i];
            Object o = null;
            try {
                o = binding.getVariable(df.getName().toUpperCase());
            }
            catch (MissingPropertyException e) {
                // ...   
            }
            data[i] = (Serializable) o;
        }
        StreamElement seo = new StreamElement(outputStructure, data);
        try {
            Long timed = (Long)binding.getVariable("TIMED");
            seo.setTimeStamp(timed);
        }
        catch (MissingPropertyException e) {
            // ...
        }
        return seo;
    }

    protected Binding getContext(StreamElement se) {
        StreamElementComparator sec = new StreamElementComparator(se);
        Binding context = cache.get(sec);
        if (context == null) {
            // Check if the cache is not full and remove the first element if so, in order to make space.
            if (cache.size() >= MAX_CACHE_SIZE)
                cache.remove(cache.keySet().iterator().next());
            // Create a new context for the StreamElement and put it in the cache.
            context = new Binding();
            cache.put(sec, context);
        }
        // Update the bindings
        for (String fieldName : se.getFieldNames()) {
            context.setVariable(fieldName.toUpperCase(), se.getData(fieldName));
        }
        context.setVariable("TIMED", se.getTimeStamp());
        return context;
    }

    protected Object evaluate(Script script, Binding context) {
        script.setBinding(context);
        return script.run();
    }


    /**
     *
     */
    public static class StreamElementComparator {

        private StreamElement se = null;

        public StreamElementComparator(StreamElement se) {
            this.se = se;
        }

        public StreamElement getStreamElement() {
            return se;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof StreamElementComparator))
                return false;
            StreamElementComparator se2 = (StreamElementComparator) o;
            if (
                    se2 == null ||
                            se2.getStreamElement() == null ||
                            se2.getStreamElement().getFieldNames() == null ||
                            this.se == null ||
                            this.se.getFieldNames() == null ||
                            this.se.getFieldNames().length != se2.getStreamElement().getFieldNames().length)
                return false;
            for (int j = 0 ; j < this.se.getFieldNames().length ; j++) {
                String fieldName = this.se.getFieldNames()[j];
                int index = findFieldNameIndex(fieldName, se2.getStreamElement().getFieldNames());
                if (index == -1 || this.se.getFieldTypes()[j] != se2.getStreamElement().getFieldTypes()[index])
                    return false;
            }
            return true;
        }

        @Override
        public int hashCode() { return 0; }

        private int findFieldNameIndex(String fieldName, String[] fieldNames) {
            if (fieldName == null)
                return -1;
            for (int i = 0; i < fieldNames.length; i++) {
                if (fieldName.compareToIgnoreCase(fieldNames[i]) == 0)
                    return i;
            }
            return -1;
        }
    }
}
