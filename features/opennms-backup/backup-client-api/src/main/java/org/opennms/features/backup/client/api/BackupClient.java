package org.opennms.features.backup.client.api;

import com.google.common.io.ByteStreams;
import com.opennms.saas.endpoint.backup.api.model.BackupConfig;
import com.opennms.saas.endpoint.backup.api.model.BackupInfo;
import com.opennms.saas.endpoint.backup.api.model.BackupState;
import com.opennms.saas.endpoint.backup.api.model.ChunkInfo;
import com.opennms.saas.endpoint.backup.api.model.FileInfo;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.FormBodyPart;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackupClient {
    private static final Logger LOG = LoggerFactory.getLogger(BackupClient.class);
    private LocalBackupConfig m_localBackupConfig;
//    private SchedulerFactoryBean schedulerFactory;


    public BackupClient() {
        LocalBackupConfig localBackupConfig = new LocalBackupConfig();
        localBackupConfig.setKeyId("8062629b-d401-426c-b7b3-77d51eb52e65");
        localBackupConfig.setBackupServiceLocation("http://localhost:8080/backup-war/endpoint/backups");
        localBackupConfig.setLocalBackupDirectory("/Users/chris/Desktop/backup");
        localBackupConfig.setBaseDirectory("/opt/opennms");
        localBackupConfig.setPgDumpLocation("/Library/PostgreSQL/9.2/bin/pg_dump");
        localBackupConfig.setMaxConcurrentUploads(4);

        localBackupConfig.addDirectory("etc");
        localBackupConfig.addDirectory("share");
        localBackupConfig.addDirectory("dbdump");

        localBackupConfig.setSecret("password");

        m_localBackupConfig = localBackupConfig;
    }

    public BackupClient(LocalBackupConfig localBackupConfig) {
        m_localBackupConfig = localBackupConfig;
    }

    public void setLocalBackupConfig(LocalBackupConfig localBackupConfig) {
        m_localBackupConfig = localBackupConfig;
    }

    public List<BackupInfo> list(BackupConfig backupConfig) {
        return remoteList();
    }

    public BackupConfig lookupBackupConfig() {
        BackupConfig backupConfig;
        try {
            backupConfig = lookupRemoteBackupConfig();
        } catch (Exception e) {
            throw new BackupClientException("Lookup of remote configuration failed", e);
        }
        return backupConfig;
    }

    public BackupInfo updateBackupInfo(BackupInfo backupInfo) {
        return remoteInfo(backupInfo);
    }

    public BackupInfo prepare() {
        BackupInfo backupInfo = null;

        try {
            backupInfo = (BackupInfo) remoteInitializeBackup();
        } catch (Exception e) {
            throw new BackupClientException("Initializing of backup failed", e);
        }

        return backupInfo;
    }

    public ZipArchive create(BackupConfig backupConfig) throws IOException {
        Date creationDate = Calendar.getInstance().getTime();
        ZipArchive zipArchive = new ZipArchive(m_localBackupConfig.getLocalBackupDirectory() + File.separator + "backup." + creationDate.getTime() + ".zip", m_localBackupConfig.getBaseDirectory(), creationDate, backupConfig.getMaxChunkSize());
        zipArchive.setDirectories(m_localBackupConfig.getDirectories());
        zipArchive.create();

        if (zipArchive.getFilesize() > backupConfig.getMaxFileSize()) {
            throw new BackupClientException("Filesize of local backup exceeds maximum file upload limit of remote server");
        }

        return zipArchive;
    }

    public void upload(final BackupConfig backupConfig, final BackupInfo backupInfo, final ZipArchive zipArchive) throws IOException {
        FileInfo fileInfo = zipArchive.getFileInfo();

        List<ChunkInfo> chunkInfoList = fileInfo.getChunkInfos();
        int c = 0;

        /*Response response = */
        remoteInformAboutFiles(backupInfo, fileInfo);

//        if (response.getStatus() != 200) {
//            throw new BackupClientException("Remote server returned status code " + response.getStatus());
//        }

        int numberOfConcurrentUploads = Math.min(m_localBackupConfig.getMaxConcurrentUploads(), backupConfig.getMaxConcurrentUploads());

        System.out.println(numberOfConcurrentUploads);

        ExecutorService executorService = Executors.newFixedThreadPool(numberOfConcurrentUploads);

        for (final ChunkInfo chunkInfo : chunkInfoList) {
            System.out.println(chunkInfo.getPosition() + " - " + chunkInfo.getHash());

            final int chunkToUpload = c;

            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        InputStream inputStream = zipArchive.getInputStreamForChunk(chunkToUpload);
                        BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream("/Users/chris/Desktop/backup/test" + chunkToUpload));

                        byte[] buffer = new byte[65536];
                        int read = 0;


                        while ((read = inputStream.read(buffer)) != -1) {
                            bufferedOutputStream.write(buffer, 0, read);
                        }

                        inputStream.close();
                        bufferedOutputStream.close();

                       /* Response uploadResponse = */
                        remoteUpload(backupInfo, chunkInfo, zipArchive.getInputStreamForChunk(chunkToUpload));

//                        if (uploadResponse.getStatus() != 200) {
//                            // TODO error handling
//                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });

            c++;
        }

        executorService.shutdown();

        BackupInfo backupInfo1 = remoteInfo(backupInfo);

        for (ChunkInfo chunkInfo1 : backupInfo1.getSuccessfullyReceivedChunks()) {
            System.out.println("Chunk #" + chunkInfo1.getPosition() + " received");
        }
    }

    public BackupState getBackupState(BackupInfo backupInfo) {
        return remoteInfo(backupInfo).getState();
    }

    public BackupInfo finish(BackupInfo backupInfo) {
        return remoteFinishBackup(backupInfo);
    }

    private BackupConfig lookupRemoteBackupConfig() {
        return remoteGetConfig();
    }

//    private BackupInfo remoteInfo(BackupInfo backupInfo) {
//        Client client = ClientBuilder.newClient();
//
//        WebTarget webTarget = client.target(m_localBackupConfig.getBackupServiceLocation())
//                .path(m_localBackupConfig.getKeyId())
//                .path(backupInfo.getId());
//
//        Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON_TYPE);
//
//        Response response = invocationBuilder.get();
//
//        return response.readEntity(BackupInfo.class);
//    }
//
//
//    private BackupConfig remoteGetConfig() {
//        Client client = ClientBuilder.newClient();
//
//        WebTarget webTarget = client.target(m_localBackupConfig.getBackupServiceLocation())
//                .path(m_localBackupConfig.getKeyId())
//                .path("config");
//
//        Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON_TYPE);
//
//        Response response = invocationBuilder.accept(MediaType.APPLICATION_JSON_TYPE).get();
//
//        return response.readEntity(BackupConfig.class);
//    }
//
//
//    private BackupInfo remoteFinishBackup(BackupInfo backupInfo) {
//        Client client = ClientBuilder.newClient();
//        WebTarget webTarget = client.target(m_localBackupConfig.getBackupServiceLocation())
//                .path(m_localBackupConfig.getKeyId())
//                .path(backupInfo.getId())
//                .path("finish");
//
//        Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON_TYPE);
//
//        Response response = invocationBuilder.post(null);
//
//        return response.readEntity(BackupInfo.class);
//    }
//
//    private BackupInfo remoteInitializeBackup() {
//        Client client = ClientBuilder.newClient();
//        WebTarget webTarget = client.target(m_localBackupConfig.getBackupServiceLocation())
//                .path(m_localBackupConfig.getKeyId());
//
//        Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON_TYPE);
//
//        Response response = invocationBuilder.post(null);
//
//        return response.readEntity(BackupInfo.class);
//    }
//
//    private Response remoteInformAboutFiles(BackupInfo backupInfo, FileInfo fileInfo) {
//        Client client = ClientBuilder.newBuilder().register(MultiPartFeature.class).build();
//
//        WebTarget webTarget = client.target(m_localBackupConfig.getBackupServiceLocation())
//                .path(m_localBackupConfig.getKeyId())
//                .path(backupInfo.getId())
//                .path("fileInfo");
//
//        Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON_TYPE);
//
//        Response response = invocationBuilder.post(Entity.entity(fileInfo, MediaType.APPLICATION_JSON_TYPE));
//
//        return response;
//    }
//
//
//    private Response remoteUpload(BackupInfo backupInfo, ChunkInfo chunkInfo, InputStream inputStream) {
//        FormDataMultiPart formDataMultiPart = new FormDataMultiPart();
//        formDataMultiPart.bodyPart(new FormDataBodyPart("file", inputStream, MediaType.APPLICATION_OCTET_STREAM_TYPE));
//
//        Client client = ClientBuilder.newBuilder().register(MultiPartFeature.class).build();
//
//        WebTarget webTarget = client.target(m_localBackupConfig.getBackupServiceLocation())
//                .path(m_localBackupConfig.getKeyId())
//                .path(backupInfo.getId())
//                .path(chunkInfo.getHash());
//
//        Invocation.Builder invocationBuilder = webTarget.request();
//
//        return invocationBuilder.post(Entity.entity(formDataMultiPart, MediaType.MULTIPART_FORM_DATA_TYPE));
//    }
//
//    private List<BackupInfo> remoteList() {
//        Client client = ClientBuilder.newClient();
//
//        WebTarget webTarget = client.target(m_localBackupConfig.getBackupServiceLocation())
//                .path(m_localBackupConfig.getKeyId());
//
//        Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON_TYPE);
//
//        Response response = invocationBuilder.get();
//
//        return response.readEntity(List.class);
//    }

    private List<BackupInfo> remoteList() {
        DefaultHttpClient client = new DefaultHttpClient();
        try {
            HttpGet get = new HttpGet(buildPath(m_localBackupConfig.getBackupServiceLocation(), m_localBackupConfig.getKeyId()));
            get.addHeader("accept", "application/json");

            HttpResponse response = client.execute(get);
            byte[] content = new byte[(int)response.getEntity().getContentLength()];
            ByteStreams.readFully(response.getEntity().getContent(), content);
            LOG.debug(new String(content));

            return new ObjectMapper().readValue(new String(content), new TypeReference<List<BackupInfo>>(){});
        } catch (ClientProtocolException e) {
            e.printStackTrace();  
        } catch (IOException e) {
            e.printStackTrace();  
        } finally {
            client.getConnectionManager().shutdown();
        }
        return Collections.emptyList();
    }

    private String buildPath(String... path) {
        StringBuilder builder = new StringBuilder();
        for (String eachPath : path) {
            if (builder.length() > 0 && builder.charAt(builder.length()-1) != '/') {
                builder.append("/");
            }
            if (eachPath.startsWith("/")) {
                eachPath.replaceFirst("/", "");
            }
            builder.append(eachPath);
        }

        String buildPath = builder.toString();
        LOG.info("build path: " + buildPath);
        return buildPath;
    }

    private BackupInfo remoteInfo(BackupInfo backupInfo) {
        DefaultHttpClient client = new DefaultHttpClient();
        try {
            HttpGet get = new HttpGet(
                    buildPath(
                            m_localBackupConfig.getBackupServiceLocation(),
                            m_localBackupConfig.getKeyId(),
                            backupInfo.getId()));
            get.addHeader("accept", "application/json");

            HttpResponse response = client.execute(get);
            byte[] content = new byte[(int)response.getEntity().getContentLength()];
            ByteStreams.readFully(response.getEntity().getContent(), content);
            LOG.debug(new String(content));

            return new ObjectMapper().readValue(new String(content), BackupInfo.class);
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            client.getConnectionManager().shutdown();
        }
        return null;
    }

    private BackupConfig remoteGetConfig() {
        DefaultHttpClient client = new DefaultHttpClient();
        try {
            HttpGet get = new HttpGet(
                    buildPath(m_localBackupConfig.getBackupServiceLocation(),
                            m_localBackupConfig.getKeyId(),
                            "config"));
            get.addHeader("accept", "application/json");

            HttpResponse response = client.execute(get);
            byte[] content = new byte[(int)response.getEntity().getContentLength()];
            ByteStreams.readFully(response.getEntity().getContent(), content);
            LOG.debug(new String(content));

            return new ObjectMapper().readValue(new String(content), BackupConfig.class);
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            client.getConnectionManager().shutdown();
        }
        return null;
    }


    private BackupInfo remoteFinishBackup(BackupInfo backupInfo) {
        DefaultHttpClient client = new DefaultHttpClient();
        try {
            HttpPost post= new HttpPost(
                    buildPath(m_localBackupConfig.getBackupServiceLocation(),
                            m_localBackupConfig.getKeyId(),
                            backupInfo.getId(),
                            "finish"));
            StringEntity input = new StringEntity(new ObjectMapper().writeValueAsString(backupInfo));
            input.setContentType("application/json");
            post.setEntity(input);

            HttpResponse response = client.execute(post);
            byte[] content = new byte[(int)response.getEntity().getContentLength()];
            ByteStreams.readFully(response.getEntity().getContent(), content);
            LOG.debug(new String(content));

            return new ObjectMapper().readValue(new String(content), BackupInfo.class);
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            client.getConnectionManager().shutdown();
        }
        return null;
    }


    private BackupInfo remoteInitializeBackup() {
        DefaultHttpClient client = new DefaultHttpClient();
        try {
            HttpPost post= new HttpPost(
                    buildPath(m_localBackupConfig.getBackupServiceLocation(),
                            m_localBackupConfig.getKeyId()));
            StringEntity input = new StringEntity("{}");
            input.setContentType("application/json");
            post.setEntity(input);

            HttpResponse response = client.execute(post);
            byte[] content = new byte[(int)response.getEntity().getContentLength()];
            ByteStreams.readFully(response.getEntity().getContent(), content);
            LOG.debug(new String(content));

            return new ObjectMapper().readValue(new String(content), BackupInfo.class);
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            client.getConnectionManager().shutdown();
        }
        return null;
    }


    private void remoteInformAboutFiles(BackupInfo backupInfo, FileInfo fileInfo) {
        DefaultHttpClient client = new DefaultHttpClient();
        try {
            HttpPost post= new HttpPost(
                    buildPath(m_localBackupConfig.getBackupServiceLocation(),
                            m_localBackupConfig.getKeyId(),
                            backupInfo.getId(),
                            "fileInfo"));
            StringEntity input = new StringEntity(new ObjectMapper().writeValueAsString(fileInfo));
            input.setContentType("application/json");
            post.setEntity(input);

            HttpResponse response = client.execute(post);
            byte[] content = new byte[(int)response.getEntity().getContentLength()];
            ByteStreams.readFully(response.getEntity().getContent(), content);
            LOG.debug(new String(content));

//            return new ObjectMapper().readValue(new String(content), BackupInfo.class);
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            client.getConnectionManager().shutdown();
        }
//        return null;
//
//        Client client = Client.create();
//
//        WebResource webResource = client.resource(m_localBackupConfig.getBackupServiceLocation())
//                .path(m_localBackupConfig.getKeyId())
//                .path(backupInfo.getId())
//                .path("fileInfo");
//
//        webResource.getRequestBuilder().accept(MediaType.APPLICATION_JSON_TYPE).post(fileInfo);
    }


    private void remoteUpload(BackupInfo backupInfo, ChunkInfo chunkInfo, InputStream inputStream) {
        DefaultHttpClient client = new DefaultHttpClient();
        try {
            HttpPost post= new HttpPost(
                    buildPath(m_localBackupConfig.getBackupServiceLocation(),
                            m_localBackupConfig.getKeyId(),
                            backupInfo.getId(),
                            chunkInfo.getHash()));


            MultipartEntity multipart= new MultipartEntity();
            FormBodyPart bodyPart = new FormBodyPart("file", new InputStreamBody(inputStream, "file"));
            multipart.addPart(bodyPart);
            post.setEntity(multipart);

            HttpResponse response = client.execute(post);
            byte[] content = new byte[(int)response.getEntity().getContentLength()];
            ByteStreams.readFully(response.getEntity().getContent(), content);
            LOG.debug(new String(content));

//            return new ObjectMapper().readValue(new String(content), BackupInfo.class);
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            client.getConnectionManager().shutdown();
        }
//        return null;

    }


    public static void main(String[] args) {

        LocalBackupConfig localBackupConfig = new LocalBackupConfig();
        localBackupConfig.setKeyId("3086c1a5-1314-469e-afba-cf42484266a0");
        localBackupConfig.setBackupServiceLocation("http://localhost:8080/backup-war/endpoint/backups");
        localBackupConfig.setLocalBackupDirectory("/Users/chris/Desktop/backup");
        localBackupConfig.setBaseDirectory("/opt/opennms");
        localBackupConfig.setPgDumpLocation("/Library/PostgreSQL/9.2/bin/pg_dump");
        localBackupConfig.setMaxConcurrentUploads(4);

        localBackupConfig.addDirectory("etc");
        localBackupConfig.addDirectory("share");
        localBackupConfig.addDirectory("dbdump");

        localBackupConfig.setSecret("password");

        BackupClient backupClient = new BackupClient(localBackupConfig);

        try {
            BackupConfig backupConfig = backupClient.lookupRemoteBackupConfig();

            BackupInfo backupInfo = backupClient.prepare();

            System.out.println(backupClient.getBackupState(backupInfo));

            /*
            try {
                BackupDbUtil backupDbUtil = new BackupDbUtil(localBackupConfig, "/opt/opennms/etc/opennms-datasources.xml");

                backupDbUtil.createDump();
            } catch (Exception e) {
                e.printStackTrace();
            }
            */

            ZipArchive zipArchive = backupClient.create(backupConfig);

            System.out.println(backupClient.getBackupState(backupInfo));

            backupClient.upload(backupConfig, backupInfo, zipArchive);

            System.out.println(backupClient.getBackupState(backupInfo));

            backupClient.finish(backupInfo);

            System.out.println(backupClient.getBackupState(backupInfo));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
