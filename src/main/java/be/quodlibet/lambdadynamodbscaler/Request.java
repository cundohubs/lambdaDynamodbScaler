package be.quodlibet.lambdadynamodbscaler;

/**
 *
 * @author Facundo <facundo@curalate.com>
 */
public class Request
{
    String s3BucketName;
    String s3ObjectKey;

    public String getBucketName() {
        return s3BucketName;
    }

    public void sets3BucketName(String s3BucketName) {
        this.s3BucketName = s3BucketName;
    }

    public String gets3ObjectKey() {
        return s3ObjectKey;
    }

    public void sets3ObjectKey(String s3ObjectKey) {
        this.s3ObjectKey = s3ObjectKey;
    }

    public Request(String s3BucketName, String s3ObjectKey) {
        this.s3BucketName = s3BucketName;
        this.s3ObjectKey = s3ObjectKey;
    }

    public Request() {
    }
}
