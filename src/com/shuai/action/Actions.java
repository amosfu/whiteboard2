package com.shuai.action;

/**
 * Created by Amos on 2017-03-08.
 */

import com.opensymphony.xwork2.ActionSupport;
import com.shuai.dao.UserDao;
import com.shuai.util.Utils;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.dispatcher.SessionMap;
import org.apache.struts2.interceptor.SessionAware;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;


public class Actions extends ActionSupport implements SessionAware {
    private static final Logger logger = LogManager.getLogger(Actions.class);
    private String dbHost = getText("mysql.host");
    private String dbPort = getText("mysql.port");
    private String dbName = getText("mysql.name");
    private String dbUser = getText("mysql.user");
    private String dbPwd = getText("mysql.pwd");

    private UserDao userDao = new UserDao("jdbc:mysql://" + dbHost + ":" + dbPort + "/" + dbName, dbUser, dbPwd);

    private String username, userpass;
    private SessionMap<String, String> sessionmap;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUserpass() {
        return userpass;
    }

    public void setUserpass(String userpass) {
        this.userpass = userpass;
    }

    public String execute() throws Exception {
        if (userDao.login(username, userpass)) {
            sessionmap.put("login", "true");
            sessionmap.put("id", username);
            return ActionSupport.SUCCESS;
        } else {
            return ActionSupport.ERROR;
        }
    }

    private InputStream keyInputStream;

    public InputStream getKeyInputStream() {
        return keyInputStream;
    }

    private File keyUpload;
    private String keyUploadContentType;
    private String keyUploadFileName;
    private File dataUpload;
    private String dataUploadContentType;
    private String dataUploadFileName;

    private File followUpload;
    private String followUploadContentType;
    private String followUploadFileName;
    private InputStream followInputStream;

    public InputStream getFollowInputStream() {
        return followInputStream;
    }

    public File getFollowUpload() {
        return followUpload;
    }

    public void setFollowUpload(File followUpload) {
        this.followUpload = followUpload;
    }

    public String getFollowUploadContentType() {
        return followUploadContentType;
    }

    public void setFollowUploadContentType(String followUploadContentType) {
        this.followUploadContentType = followUploadContentType;
    }

    public String getFollowUploadFileName() {
        return followUploadFileName;
    }

    public void setFollowUploadFileName(String followUploadFileName) {
        this.followUploadFileName = followUploadFileName;
    }

    public File getKeyUpload() {
        return keyUpload;
    }

    public void setKeyUpload(File keyUpload) {
        this.keyUpload = keyUpload;
    }

    public String getKeyUploadContentType() {
        return keyUploadContentType;
    }

    public void setKeyUploadContentType(String keyUploadContentType) {
        this.keyUploadContentType = keyUploadContentType;
    }

    public String getKeyUploadFileName() {
        return keyUploadFileName;
    }

    public void setKeyUploadFileName(String keyUploadFileName) {
        this.keyUploadFileName = keyUploadFileName;
    }

    public File getDataUpload() {
        return dataUpload;
    }

    public void setDataUpload(File dataUpload) {
        this.dataUpload = dataUpload;
    }

    public String getDataUploadContentType() {
        return dataUploadContentType;
    }

    public void setDataUploadContentType(String dataUploadContentType) {
        this.dataUploadContentType = dataUploadContentType;
    }

    public String getDataUploadFileName() {
        return dataUploadFileName;
    }

    public void setDataUploadFileName(String dataUploadFileName) {
        this.dataUploadFileName = dataUploadFileName;
    }

    // auto-expiring map , TTL = 60s
    private static Map<String, byte[]> PUBLISHED_PK = new PassiveExpiringMap<String, byte[]>
            (new PassiveExpiringMap.ConstantTimeToLiveExpirationPolicy<String, byte[]>(60 * 1000), new LinkedHashMap<String, byte[]>());
    // auto-expiring map , TTL = 60s
    private static Map<String, Map<String, String>> FOLLOW_PUBLISHED_PK = new PassiveExpiringMap<String, Map<String, String>>
            (new PassiveExpiringMap.ConstantTimeToLiveExpirationPolicy<String, Map<String, String>>(60 * 1000), new LinkedHashMap<String, Map<String, String>>());

    public String publishPK() throws Exception {
        if ("true".equalsIgnoreCase(sessionmap.get("login"))) {
            byte[] receivedBytes = IOUtils.toByteArray(new FileInputStream(keyUpload));
            synchronized (PUBLISHED_PK) {
                PUBLISHED_PK.put(sessionmap.get("id"), receivedBytes);
            }
            byte[] keyMapJson = new byte[]{};
            synchronized (FOLLOW_PUBLISHED_PK) {
                keyMapJson = Utils.encodeObjectToJson(FOLLOW_PUBLISHED_PK.get(sessionmap.get("id")));
            }
            keyInputStream = new ByteArrayInputStream(keyMapJson);
            return ActionSupport.SUCCESS;
        } else {
            return ActionSupport.ERROR;
        }
    }

    public String follow() throws Exception {
        if ("true".equalsIgnoreCase(sessionmap.get("login"))) {
            byte[] receivedBytes = IOUtils.toByteArray(new FileInputStream(followUpload));
            Map<String, String> followKeyMap = (Map<String, String>) Utils.decodeJsonToObject(receivedBytes, Map.class);
            Map<String, String> publishedKeyMap = new LinkedHashMap<>();
            for (String idToFollow : followKeyMap.keySet()) {
                //output: published key map
                synchronized (PUBLISHED_PK) {
                    if (PUBLISHED_PK.get(idToFollow) != null) {
                        publishedKeyMap.put(idToFollow, Base64.encodeBase64String(PUBLISHED_PK.get(idToFollow))); //encode before Json
                    }
                }
                //input follow up key map
                synchronized (FOLLOW_PUBLISHED_PK) {
                    Map<String, String> individualMap = FOLLOW_PUBLISHED_PK.get(idToFollow);
                    if (individualMap == null) {
                        individualMap = new LinkedHashMap<>();
                        FOLLOW_PUBLISHED_PK.put(idToFollow, individualMap);
                    }
                    individualMap.put(sessionmap.get("id"), followKeyMap.get(idToFollow));
                }
            }
            followInputStream = new ByteArrayInputStream(Utils.encodeObjectToJson(publishedKeyMap));
            return ActionSupport.SUCCESS;
        } else {
            return ActionSupport.ERROR;
        }
    }

    // auto-expiring map , TTL = 60s
    private static Map<String, Map<String, String>> GPS_TRANSFER_OBJECT_MAP = new PassiveExpiringMap<String, Map<String, String>>
            (new PassiveExpiringMap.ConstantTimeToLiveExpirationPolicy<String, Map<String, String>>(60 * 1000), new LinkedHashMap<String, Map<String, String>>());

    public String pushData() throws Exception {
        if ("true".equalsIgnoreCase(sessionmap.get("login"))) {
            byte[] receivedBytes = IOUtils.toByteArray(new FileInputStream(dataUpload));
            Map<String, String> dataMap = (Map<String, String>) Utils.decodeJsonToObject(receivedBytes, Map.class);
            for (String pushToId : dataMap.keySet()) {
                synchronized (GPS_TRANSFER_OBJECT_MAP) {
                    Map<String, String> individualPushData = GPS_TRANSFER_OBJECT_MAP.get(pushToId);
                    if (individualPushData == null) {
                        individualPushData = new LinkedHashMap<>();
                        GPS_TRANSFER_OBJECT_MAP.put(pushToId, individualPushData);
                    }
                    individualPushData.put(sessionmap.get("id"), dataMap.get(pushToId));
                }
            }
            return ActionSupport.SUCCESS;
        } else {
            return ActionSupport.ERROR;
        }
    }

    private InputStream dataInputStream;

    public InputStream getDataInputStream() {
        return dataInputStream;
    }

    public String pullData() throws Exception {
        if ("true".equalsIgnoreCase(sessionmap.get("login"))) {
            byte[] keyMapJson = new byte[]{};
            synchronized (GPS_TRANSFER_OBJECT_MAP) {
                keyMapJson = Utils.encodeObjectToJson(GPS_TRANSFER_OBJECT_MAP.get(sessionmap.get("id")));
            }
            dataInputStream = new ByteArrayInputStream(keyMapJson);
            return ActionSupport.SUCCESS;
        } else {
            return ActionSupport.ERROR;
        }
    }

    public String chkName() {
        if (userDao.checkName(username)) {
            return ActionSupport.SUCCESS;
        } else {
            return ActionSupport.ERROR;
        }
    }

    public String logout() {
        if ("true".equalsIgnoreCase(sessionmap.get("login"))) {
            sessionmap.invalidate();
            return ActionSupport.SUCCESS;
        } else {
            return ActionSupport.ERROR;
        }
    }

    public String register() throws Exception {
        // hash password
        String salt = Utils.getPasswordSalt();
        String hashedPwd = Utils.getSHA256SecurePassword(userpass, salt);
        if (userDao.register(username, hashedPwd, salt)) {
            sessionmap.put("login", "true");
            sessionmap.put("id", username);
            // use password as shared secret for DH-EKE key encryption
            return ActionSupport.SUCCESS;
        } else {
            logger.debug("Register Failed");
            return ActionSupport.ERROR;
        }
    }

    @Override
    public void setSession(Map<String, Object> map) {
        sessionmap = (SessionMap) map;
    }

}
