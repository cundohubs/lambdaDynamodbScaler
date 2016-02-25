package be.quodlibet.lambdadynamodbscaler;

import com.amazonaws.services.lambda.runtime.Context;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author Dries Horions <dries@quodlibet.be>
 */
public class ScalerTest
{
    String S3BucketName = "curalate-configuration-qa";
    String S3ObjectKey  = "scaler.properties";

    public ScalerTest()
    {
    }
    /**
     * Test of scale method, of class Scaler.
     */
    @Test
    public void testScale()
    {
        System.out.println("scale");
        Context context = null;
        Scaler instance = new Scaler();
        Object expResult = null;
        Request request = new Request(S3BucketName, S3ObjectKey);
        System.out.println(request.s3BucketName);
        System.out.println(request.s3ObjectKey);
        Response result = instance.scale(request, context);
        assertTrue(result.getSuccess());
    }

}
