package com.shuai.dao;

import com.shuai.util.Utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Created by Amos on 2017-03-08.
 */
public class UserDao {
    private static final String REGISTER_SQL = "insert into user (name,pwd,salt,timestamp) VALUES (?,?,?,now());";
    private static final String LOGIN_SQL = "select pwd,salt from user where name=? ;";
    private static final String CHECK_NAME_SQL = "select count(*) from user where name=?;";

    private String dbStr;
    private String dbUsr;
    private String dbPwd;

    public UserDao(String dbStr, String dbUsr, String dbPwd) {
        this.dbStr = dbStr;
        this.dbUsr = dbUsr;
        this.dbPwd = dbPwd;
    }

    public boolean register(String username, String userpass, String salt) {
        boolean isSuccess = false;
        try {
            Connection c = Utils.getDBConnection(dbStr,dbUsr,dbPwd);
            PreparedStatement st = c.prepareStatement(REGISTER_SQL);
            st.setString(1, username);
            st.setString(2, userpass);
            st.setString(3, salt);
            if (st.executeUpdate() != 0) {
                isSuccess = true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return isSuccess;
    }
    public boolean checkName(String username) {
        boolean isSuccess = false;
        try {
            Connection c = Utils.getDBConnection(dbStr,dbUsr,dbPwd);
            PreparedStatement st = c.prepareStatement(CHECK_NAME_SQL);
            st.setString(1, username);
            ResultSet rs = st.executeQuery();
            rs.next();
            int rst = rs.getInt(1);
            if (rst > 0) {
                isSuccess = true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return isSuccess;
    }

    public boolean login(String username, String userpass) {
        boolean isSuccess = false;
        try {
            Connection c = Utils.getDBConnection(dbStr, dbUsr, dbPwd);
            PreparedStatement st = c.prepareStatement(LOGIN_SQL);
            st.setString(1, username);
            ResultSet rs = st.executeQuery();
            if (rs.next()) {
                String pwd = rs.getString("pwd");
                String salt = rs.getString("salt");
                if (pwd.equals(Utils.getSHA256SecurePassword(userpass, salt))) {
                    isSuccess = true;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return isSuccess;
    }
}
