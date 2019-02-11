import jenkins.model.*;
import hudson.security.*;
import java.util.logging.Logger;

import com.amazonaws.ClientConfiguration
import com.amazonaws.AmazonServiceException
import com.amazonaws.regions.RegionUtils
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.ListObjectsRequest;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

def env = System.getenv()

def String localPath = "/var/opt/codeontap/"

// Get files from S3
def String opsS3Bucket = env.OPSDATA_BUCKET
def String settingsPrefix = env.SETTINGS_PREFIX

AmazonS3 s3 = 
AmazonS3ClientBuilder.standard()
                        .withRegion("ap-southeast-2") // The first region to try your request against
                        .withForceGlobalBucketAccessEnabled(true) // If a bucket is in a different region, try again in the correct region
                        .build();

def ObjectListing objectListing = s3.listObjects(opsS3Bucket, settingsPrefix);

Logger.global.info("Checking s3://$opsS3Bucket/$settingsPrefix for files")

List<S3ObjectSummary> s3ObjectSummaries = objectListing.getObjectSummaries();
while (objectListing.isTruncated()) 
{
    objectListing = s3.listNextBatchOfObjects (objectListing);
    s3ObjectSummaries.addAll (objectListing.getObjectSummaries());
}

for(S3ObjectSummary s3ObjectSummary : s3ObjectSummaries)
{
    try {
        String s3ObjectKey = s3ObjectSummary.getKey();
        
        String localFileName = localPath + s3ObjectKey.split("/")[-1]

        if ( localFileName != (localPath + "site.properties") && localFileName.endsWith(".properties") ) {
            localFileName = localFileName + ".tmp"
        } 
    
        S3Object o = s3.getObject(opsS3Bucket, s3ObjectKey);
        S3ObjectInputStream s3is = o.getObjectContent();
        
        Logger.global.info("Adding file to $localFileName")

        FileOutputStream fos = new FileOutputStream( new File(localFileName) );
        byte[] read_buf = new byte[1024];
        int read_len = 0;
        while ((read_len = s3is.read(read_buf)) > 0) {
            fos.write(read_buf, 0, read_len);
        }
        s3is.close();
        fos.close();

        new File(localFileName).setWritable(true, true); 

    } catch (AmazonServiceException e) {
        Logger.global.info(e.getErrorMessage());
    } catch (FileNotFoundException e) {
        Logger.global.info(e.getMessage());
    } catch (IOException e) {
        Logger.global.info(e.getErrorMessage());
    }
}

// Merge site.properties into other properties files 
def String sitePropertiesFile = env.PROPERTIES_SITE_FILE ?: "site.properties"
def propertiesFiles = new FileNameFinder().getFileNames(localPath, '**/*.properties.tmp', "**/$sitePropertiesFile") 

// Get a writer to your new file
propertiesFiles.each{ propFile -> 
    new File( (propFile).minus(".tmp") ).withWriter { w ->

    // For each input file path
    [ "$localPath/$sitePropertiesFile", propFile ].each { f ->
        
      // Get a reader for the input file
      new File( f ).withReader { r ->
  
        // And write data from the input into the output
        w << r << '\n'
      }
    }
  }
  Logger.global.info("removing" + propFile )
  new File( propFile).delete()
}