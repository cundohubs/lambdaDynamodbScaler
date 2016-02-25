package be.quodlibet.lambdadynamodbscaler;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.document.DeleteItemOutcome;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.UpdateItemOutcome;
import com.amazonaws.services.dynamodbv2.document.spec.DeleteItemSpec;
import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec;
import com.amazonaws.services.dynamodbv2.document.utils.NameMap;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.dynamodbv2.model.ReturnValue;


public class ScalerConfig {

    static DynamoDB dynamoDB = new DynamoDB(new AmazonDynamoDBClient(
            new ProfileCredentialsProvider()));

    static String tableName = "com.curalate.dynamic";

    public static void main(String[] args) throws IOException {

        createItems();

        retrieveItem();

        // Perform various updates.
        // updateMultipleAttributes();
        // updateAddNewAttribute();
        // updateExistingAttributeConditionally();

        // Delete the item.
        //deleteItem();

    }

    private static void createItems() {

        Table table = dynamoDB.getTable(tableName);
        try {

            Item item = new Item()
                .withPrimaryKey("dynamic-dynamodb", "120")
                .withString("Title", "Book 120 Title")
                .withString("ISBN", "120-1111111111")
                .withStringSet( "Authors", 
                    new HashSet<String>(Arrays.asList("Author12", "Author22")))
                .withNumber("Price", 20)
                .withString("Dimensions", "8.5x11.0x.75")
                .withNumber("PageCount", 500)
                .withBoolean("InPublication", false)
                .withString("ProductCategory", "Book");
            table.putItem(item);

            item = new Item()
                .withPrimaryKey("Id", 121)
                .withString("Title", "Book 121 Title")
                .withString("ISBN", "121-1111111111")
                .withStringSet( "Authors",
                    new HashSet<String>(Arrays.asList("Author21", "Author 22")))
                .withNumber("Price", 20)
                .withString("Dimensions", "8.5x11.0x.75")
                .withNumber("PageCount", 500)
                .withBoolean("InPublication", true)
                .withString("ProductCategory", "Book");
            table.putItem(item);

        } catch (Exception e) {
            System.err.println("Create items failed.");
            System.err.println(e.getMessage());

        }
    }

    private static void retrieveItem() {
        Table table = dynamoDB.getTable(tableName);

        try {

            Item item = table.getItem("Id", 120,  "Id, ISBN, Title, Authors", null);

            System.out.println("Printing item after retrieving it....");
            System.out.println(item.toJSONPretty());

        } catch (Exception e) {
            System.err.println("GetItem failed.");
            System.err.println(e.getMessage());
        }   

    }
}
