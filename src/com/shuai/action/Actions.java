package com.shuai.action;

/**
 * Created by Amos on 2017-03-08.
 */

import com.opensymphony.xwork2.ActionSupport;
import com.shuai.bean.GpsTransfer;
import com.shuai.bean.KeyObject;
import com.shuai.dao.UserDao;
import com.shuai.util.Utils;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.dispatcher.SessionMap;
import org.apache.struts2.interceptor.SessionAware;

import java.io.*;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class Actions extends ActionSupport implements SessionAware {
    private static final Logger logger = LogManager.getLogger(Actions.class);
    private String dbHost = getText("mysql.host");
    private String dbPort = getText("mysql.port");
    private String dbName = getText("mysql.name");
    private String dbUser = getText("mysql.user");
    private String dbPwd = getText("mysql.pwd");

    private UserDao userDao = new UserDao("jdbc:mysql://" + dbHost + ":" + dbPort + "/" + dbName, dbUser, dbPwd);

    private String username, userpass, key, fetchId;
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

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getFetchId() {
        return fetchId;
    }

    public void setFetchId(String fetchId) {
        this.fetchId = fetchId;
    }

    // KeyObject map
    private static Map<String, KeyObject> KEY_OBJECT_MAP = new ConcurrentHashMap<>();

    public String execute() throws Exception {
        if (userDao.login(username, userpass)) {
            sessionmap.put("login", "true");
            sessionmap.put("id", username);
            // use password as shared secret for DH-EKE key encryption
            KEY_OBJECT_MAP.put(username, new KeyObject(userpass));
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

    public String exchangeKey() throws Exception {
        if ("true".equalsIgnoreCase(sessionmap.get("login"))) {
            KeyObject keyObject = KEY_OBJECT_MAP.get(sessionmap.get("id"));
            byte[] receivedBytes = IOUtils.toByteArray(new FileInputStream(keyUpload));
            keyObject.parseKeyExchangeMsg(receivedBytes);

            keyInputStream = new ByteArrayInputStream(keyObject.generateKeyExchangeMsg());
            return ActionSupport.SUCCESS;
        } else {
            return ActionSupport.ERROR;
        }
    }

    // auto-expiring map , TTL = 60s
    private static Map<String, byte[]> GPS_TRANSFER_OBJECT_MAP = new PassiveExpiringMap<>
            (new PassiveExpiringMap.ConstantTimeToLiveExpirationPolicy<String, byte[]>(60 * 1000), new ConcurrentHashMap<String, byte[]>());

    public String pushData() throws Exception {
        if ("true".equalsIgnoreCase(sessionmap.get("login"))) {
            KeyObject keyObject = KEY_OBJECT_MAP.get(sessionmap.get("id"));
            byte[] receivedBytes = IOUtils.toByteArray(new FileInputStream(dataUpload));

            Byte[] GPSCipher = Utils.decryptJsonObject(receivedBytes, keyObject.getSecretKey(), Byte[].class);

            GPS_TRANSFER_OBJECT_MAP.put(sessionmap.get("id"), ArrayUtils.toPrimitive(GPSCipher));

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
            byte[] GPSCipher = GPS_TRANSFER_OBJECT_MAP.get(fetchId);
            if (GPSCipher == null || ArrayUtils.isEmpty(GPSCipher)) {
                GPSCipher = new byte[]{};
            }
            KeyObject keyObject = KEY_OBJECT_MAP.get(sessionmap.get("id"));
            dataInputStream = new ByteArrayInputStream(Utils.encryptJsonObject(GPSCipher, keyObject.getSecretKey()));
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
            KEY_OBJECT_MAP.remove(sessionmap.get("id"));
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
            KEY_OBJECT_MAP.put(username, new KeyObject(userpass));
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
