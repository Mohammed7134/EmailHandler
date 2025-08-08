package com.function;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;

import microsoft.exchange.webservices.data.core.ExchangeService;
import microsoft.exchange.webservices.data.core.enumeration.property.WellKnownFolderName;
import microsoft.exchange.webservices.data.core.enumeration.service.ConflictResolutionMode;
import microsoft.exchange.webservices.data.core.service.item.EmailMessage;
import microsoft.exchange.webservices.data.core.service.item.Item;
import microsoft.exchange.webservices.data.property.complex.Attachment;
import microsoft.exchange.webservices.data.property.complex.FileAttachment;
import microsoft.exchange.webservices.data.property.complex.MessageBody;
import microsoft.exchange.webservices.data.search.FindItemsResults;
import microsoft.exchange.webservices.data.search.ItemView;

public class EmailHandler {

    @FunctionName("processEmails")
    public void run(
            @TimerTrigger(name = "timerInfo", schedule = "0 */1 * * * *") String timerInfo,
            ExecutionContext context) {

        Logger logger = context.getLogger();
        logger.log(Level.INFO, "Java Timer trigger function executed at: {0}", LocalDateTime.now());

        String apiEndpoint = System.getenv("API_ENDPOINT");
        if (apiEndpoint == null || apiEndpoint.isEmpty()) {
            logger.severe("API_ENDPOINT environment variable is not set.");
            return;
        }

        try {
            ExchangeService service = ServerConnect.connect();
            if (service == null) {
                logger.severe("Failed to connect to Exchange Service.");
                return;
            }

            ItemView view = new ItemView(10);
            FindItemsResults<Item> results = service.findItems(WellKnownFolderName.Inbox, view);

            for (Item item : results.getItems()) {
                if (!(item instanceof EmailMessage)) continue;

                EmailMessage email = (EmailMessage) item;
                email.load();

                if (email.getIsRead()) {
                    logger.info("Skipping read email: " + email.getSubject());
                    continue;
                }

                String subject = email.getSubject() != null ? email.getSubject().trim() : "";
                if (!"Talabiya Processor".equalsIgnoreCase(subject)) {
                    logger.info("Skipping email with different subject: " + subject);
                    continue;
                }

                if (email.getAttachments().getCount() != 3) {
                    logger.info("Skipping email with incorrect attachment count: " + email.getAttachments().getCount());
                    continue;
                }

                logger.info("Processing email: " + subject);
                //initialize files each by name expiriesFile, detailedFile, breifFile
                File ExpiriesFile = null;
                File DetailedFile = null;
                File BreifFile = null;

                for (Attachment attachment : email.getAttachments()) {
                    if (attachment instanceof FileAttachment) {
                        FileAttachment fileAttachment = (FileAttachment) attachment;
                        fileAttachment.load(); // Load content

                        try {
                            File tempFile = Files.createTempFile("attachment_", "_" + fileAttachment.getName()).toFile();
                            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                                fos.write(fileAttachment.getContent());
                            }
                            logger.info("Saved attachment: " + tempFile.getAbsolutePath());
                            // check the name of the file if contains "expiries", "detailed", or "breif"
                            if (fileAttachment.getName().toLowerCase().contains("expiries")) {
                                ExpiriesFile = tempFile; // expiries
                            } else if (fileAttachment.getName().toLowerCase().contains("detailed")) {
                               DetailedFile = tempFile; // detailed
                            } else if (fileAttachment.getName().toLowerCase().contains("breif")) {
                               BreifFile = tempFile; // breif
                            }
                        } catch (IOException e) {
                            logger.log(Level.SEVERE, "Error writing attachment to file", e);
                        }
                    }
                }

                // Proceed only if all 3 files were downloaded
                logger.info("Sending files to external API...");
                String htmlContent = callCustomApiWithMultipleFiles(apiEndpoint, ExpiriesFile, DetailedFile, BreifFile, logger);

               if (htmlContent != null) {
                    logger.info("Got HTML content from API, storing or sending email...");
                    

                    // Create reply
                    EmailMessage reply = new EmailMessage(service);
                    reply.setSubject("Processed Catalogue");

                    // Set email body
                    MessageBody body = MessageBody.getMessageBodyFromText(htmlContent);
                    reply.setBody(body);
                    reply.getToRecipients().add(email.getFrom().getAddress());

                    // Add HTML file as attachment
                    String fileName = "processed_catalogue.html";
                    byte[] htmlBytes = htmlContent.getBytes(StandardCharsets.UTF_8);
                    reply.getAttachments().addFileAttachment(fileName, htmlBytes);

                    logger.info("Attached HTML file: " + fileName);

                    // Send email
                    reply.send();
                }




                // Mark as read
                email.setIsRead(true);
                email.update(ConflictResolutionMode.AlwaysOverwrite);

                // Clean up
                    if (ExpiriesFile != null && ExpiriesFile.exists()) {
                        if (ExpiriesFile.delete()) {
                            logger.info("Deleted temp file: " + ExpiriesFile.getAbsolutePath());
                        } else {
                            logger.warning("Failed to delete temp file: " + ExpiriesFile.getAbsolutePath());
                        }
                    }
                    if (DetailedFile != null && DetailedFile.exists()) {
                        if (DetailedFile.delete()) {
                            logger.info("Deleted temp file: " + DetailedFile.getAbsolutePath());
                        } else {
                            logger.warning("Failed to delete temp file: " + DetailedFile.getAbsolutePath());
                        }
                    }
                    if (BreifFile != null && BreifFile.exists()) {
                        if (BreifFile.delete()) {
                            logger.info("Deleted temp file: " + BreifFile.getAbsolutePath());
                        } else {
                            logger.warning("Failed to delete temp file: " + BreifFile.getAbsolutePath());
                        }
                    }
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing emails", e);
        }
    }

    private String callCustomApiWithMultipleFiles(String apiEndpoint, File expiriesFile, File detailedFile, File breifFile, Logger logger) {
    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
        HttpPost uploadRequest = new HttpPost(apiEndpoint);

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.addBinaryBody("expiries", expiriesFile, ContentType.APPLICATION_OCTET_STREAM, expiriesFile.getName());
        builder.addBinaryBody("detailed", detailedFile, ContentType.APPLICATION_OCTET_STREAM, detailedFile.getName());
        builder.addBinaryBody("breif", breifFile, ContentType.APPLICATION_OCTET_STREAM, breifFile.getName());

        HttpEntity multipart = builder.build();
        uploadRequest.setEntity(multipart);

        try (CloseableHttpResponse response = httpClient.execute(uploadRequest)) {
            int statusCode = response.getStatusLine().getStatusCode();
            logger.info("Custom API responded with status: " + statusCode);

            if (statusCode == 200) {
                String jsonResponse = new String(response.getEntity().getContent().readAllBytes());
                logger.info("API Response: " + jsonResponse);

                // Parse JSON and extract "html"
                ObjectMapper mapper = new ObjectMapper();
                java.util.Map<String, String> map = mapper.readValue(jsonResponse, java.util.Map.class);
                return map.get("html");
            }
        }
    } catch (Exception e) {
        logger.log(Level.SEVERE, "Failed to send files to custom API", e);
    }
    return null;
}


}
