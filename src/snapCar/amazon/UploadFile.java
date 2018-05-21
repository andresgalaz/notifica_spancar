package snapCar.amazon;

import java.io.File;
import java.io.IOException;

import org.apache.log4j.Logger;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.PutObjectRequest;

public class UploadFile {
    private static Logger  logger = Logger.getLogger( UploadFile.class );
    private String         bucketServer;
    private String         bucketName;
    private AmazonS3Client s3client;

    @SuppressWarnings("deprecation")
    public UploadFile(String bucketServer, String bucketName, String bucketAccessKey, String bucketSecretKey) {
        this.bucketServer = bucketServer;
        this.bucketName = bucketName;
        AWSCredentials credentials = new BasicAWSCredentials( bucketAccessKey, bucketSecretKey );
        this.s3client = new AmazonS3Client( credentials );
        this.s3client.setEndpoint( bucketServer );
    }

    public void copy(File archivo, String archRemoto) throws IOException {
        try {
            logger.debug( "Subiendo archivo " + archivo.getName() );
            PutObjectRequest por = new PutObjectRequest( bucketName, archRemoto, archivo );
            por.setCannedAcl( CannedAccessControlList.PublicRead );
            s3client.putObject( por );

        } catch (AmazonServiceException ase) {
            logger.error( "Error en el servidor S3:" + bucketServer, ase );
        } catch (AmazonClientException ace) {
            logger.error( "Error en el cliente S3:" + bucketServer, ace );
        }
    }
}