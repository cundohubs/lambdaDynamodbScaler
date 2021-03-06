package be.quodlibet.lambdadynamodbscaler;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.AmazonServiceException;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.util.Calendar;
import java.util.Objects;
import java.util.Properties;
import java.util.TimeZone;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 *
 * @author Dries Horions <dries@quodlibet.be>
 */
public class Scaler
{
    private String access_key_id;
    private String secret_access_key;
    private static final String configBucketName = "curalate-configuration";
    private static final String configKey = "scaler.properties";
    private static final String configPrefix = "dynamodbscaler";
    private static final Region defaultRegion = Region.getRegion(Regions.US_EAST_1);
    private Region region = Regions.getCurrentRegion();
   

    private FileInputStream input;
    private static final String configFileLocation = "config.properties";
    private boolean useIAMRole = false;
    private BasicAWSCredentials awsCreds;
    private Properties awsCredsProperties;
    private Properties ScalingProperties;
    private AmazonS3 s3Client;
    private AmazonDynamoDBClient clnt;
    private DynamoDB dynamoDB;
    private LambdaLogger log;

    public Response scale(Request request, Context context)
    {
        if (context != null)
        {
            log = context.getLogger();
        }
        setup();
        //Get the hour that was started
        Calendar rightNow = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        String hour = String.format("%02d", rightNow.get(Calendar.HOUR_OF_DAY));
        String message = "Scaling for hour : " + hour + "\n";
        log(message);
        //Get the table names
        if (ScalingProperties.containsKey("tablenames"))
        {
            String value = (String) ScalingProperties.get("tablenames");
            String[] tableNames = value.split(",");
            for (String tableName : tableNames)
            {
                //Check if there is a change requested for this hour
            	Properties tableProperties = getTableScalingProperties(tableName);
                String readProp = hour + "." + tableName + ".read";
                String writeProp = hour + "." + tableName + ".write";

                if (tableProperties.containsKey(readProp)
                    && tableProperties.containsKey(writeProp))
                {
                    String readCapacity = (String) tableProperties.getProperty(readProp);
                    String writeCapacity = (String) tableProperties.getProperty(writeProp);
                    //Execute the scaling change
                    message += scaleTable(tableName, Long.parseLong(readCapacity), Long.parseLong(writeCapacity));
                }
                else if (tableProperties.containsKey(readProp))
                {
                    String readCapacity = (String) tableProperties.getProperty(readProp);
                  //Execute the scaling change
                    message += scaleTable(tableName, Long.parseLong(readCapacity), Long.parseLong("0"));
                }
                else if (tableProperties.containsKey(writeProp))
                {
                    String writeCapacity = (String) tableProperties.getProperty(writeProp);
                    //Execute the scaling change
                    message += scaleTable(tableName, Long.parseLong("0"), Long.parseLong(writeCapacity));
                }
                else
                {
                	String hourTableMessage = " No values found for table " + hour + "." + tableName ;
                    log(hourTableMessage);
                    message += "\n" + hourTableMessage + "\n";
                }
            }
        }
        else
        {
            log("tables parameter not found in properties file");
        }
        //log(message);
        Response response = new Response(true, message);
        return response;
    }
    /**
     * Ensure we can also test this locally without context
     * @param message
     */
    private void log(String message)
    {
    	java.util.Date date= new java.util.Date();
   	 	Timestamp timestamp = new Timestamp(date.getTime());
   	 	String simpleTimestamp = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(timestamp);
   	 	
        if (log != null)
        {
            log.log(timestamp + ": " + message);
        }
        else
        {
            System.out.println(simpleTimestamp + ":" + message);
        }
    }

    private String scaleTable(String tableName, Long readCapacity, Long writeCapacity)
    {
        Table table = dynamoDB.getTable(tableName);
        TableDescription d = table.describe();
        ProvisionedThroughput tp = new ProvisionedThroughput();
        
        if (readCapacity == 0)
        	readCapacity = d.getProvisionedThroughput().getReadCapacityUnits();
        if (writeCapacity == 0)
        	writeCapacity = d.getProvisionedThroughput().getWriteCapacityUnits();

        tp.setReadCapacityUnits(readCapacity);
        tp.setWriteCapacityUnits(writeCapacity);
        
        if (!Objects.equals(d.getProvisionedThroughput().getReadCapacityUnits(), readCapacity)
            || !Objects.equals(d.getProvisionedThroughput().getWriteCapacityUnits(), writeCapacity))
        {
            d = table.updateTable(tp);
            return tableName + "\nRequested read/write : " + readCapacity + "/" + writeCapacity
                   + "\nCurrent read/write :" + d.getProvisionedThroughput().getReadCapacityUnits() + "/" + d.getProvisionedThroughput().getWriteCapacityUnits()
                   + "\nStatus : " + d.getTableStatus() + "\n";
        }
        else
        {
            return tableName + "\n Requested throughput equals current throughput\n";
        }
    }
    
    private Properties getTableScalingProperties(String tableName)
    {
    	Properties tableProperties = new Properties();
    	try
    	{
    		S3Object object = s3Client.getObject(new GetObjectRequest(configBucketName, configPrefix + "/" + tableName + ".properties"));
    		S3ObjectInputStream stream = object.getObjectContent();
    		tableProperties.load(stream);
    	}
        catch (IOException ex)
        {
           log("Failed to read config file : s3://" + configBucketName + "/" + configPrefix + "/" + tableName + " (" + ex.getMessage() + ")");
        }
    	return tableProperties;
    }
    
    private void setup()
    {
        //Setup credentials
        File configFile = new File(configFileLocation);

        if (awsCreds == null && configFile.exists())
        {
            awsCredsProperties = new Properties();
            input = null;
            try {
            	input = new FileInputStream(configFileLocation);
                awsCredsProperties.load(input);

                if (awsCredsProperties.containsKey("access_key_id"))
                {
                  access_key_id = (String) awsCredsProperties.get("access_key_id");
                }
                if (awsCredsProperties.containsKey("secret_access_key"))
                {
                  secret_access_key = (String) awsCredsProperties.get("secret_access_key");
                } 

                awsCreds = new BasicAWSCredentials(access_key_id, secret_access_key);
                useIAMRole = false;
            } catch (IOException ex) {
                useIAMRole = true;
		log("Failed to read config file : " + configFileLocation  + " (" + ex.getMessage() + ")");
	    } finally {
		if (input != null) {
			try {
				input.close();
			} catch (IOException e) {
				log("Failed to close file properly: " + configFileLocation);
			}
		}
	    }
        }
        else
        {
           //Going to use IAM Role instead of AWS credentials
           useIAMRole = true;
        }
        //Setup S3 client
        if (s3Client == null)
        {
            if (!useIAMRole)
            {
              s3Client = new AmazonS3Client(awsCreds);
            }
            else
            {
              s3Client = new AmazonS3Client();
            }
        }
        //Setup DynamoDB client
        if (clnt == null)
        {
            if (!useIAMRole)
            {
              clnt = new AmazonDynamoDBClient(awsCreds);
            }
            else
            {
              clnt = new AmazonDynamoDBClient();
            }
            dynamoDB = new DynamoDB(clnt);
            if (region == null) region = defaultRegion;
            clnt.setRegion(region);
        }
        //Load properties from S3
        if (ScalingProperties == null)
        {
             try
             {
                ScalingProperties = new Properties();
                S3Object object = s3Client.getObject(new GetObjectRequest(configBucketName, configPrefix + "/" + configKey));
                S3ObjectInputStream stream = object.getObjectContent();
                ScalingProperties.load(stream);
            }
             catch (IOException ex)
            {
                log("Failed to read config file : " + configBucketName + "/" + configPrefix + "/" + configKey + "(" + ex.getMessage() + ")");
            }
        }
    }
}
