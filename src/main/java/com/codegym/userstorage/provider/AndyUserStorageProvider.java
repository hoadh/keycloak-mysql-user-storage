package com.codegym.userstorage.provider;

import com.codegym.userstorage.helpers.DatabaseUtil;
import com.codegym.userstorage.helpers.PasswordUtil;
import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputUpdater;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.models.*;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.UserLookupProvider;
import org.keycloak.storage.user.UserQueryProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

public class AndyUserStorageProvider implements
        UserStorageProvider,
        UserLookupProvider,
        CredentialInputValidator,
        UserQueryProvider,
        CredentialInputUpdater
{

    private static final Logger log = LoggerFactory.getLogger(AndyUserStorageProvider.class);
    public static final int FIRST_RESULT = 0;
    public static final int MAX_RESULTS = 50;
    public static final String SELECT_USER_BY_ID        = "select id, first_name, last_name, email, is_locked from users where id = ?";
    public static final String SELECT_USER_BY_EMAIL     = "select id, first_name, last_name, email, is_locked from users where email = ?";
    public static final String SELECT_USERS_LIMIT       = "select id, first_name, last_name, email, is_locked from users order by id limit ? offset ?";
    public static final String SEARCH_USERS_BY_EMAIL    = "select id, first_name, last_name, email, is_locked from users where email like ? order by id limit ? offset ?";
    public static final String UPDATE_PASSWORD_BY_ID    = "UPDATE users SET password = ? WHERE id = ?";
    public static final String SELECT_PASSWORD_BY_ID    = "select password from users where id = ?";
    public static final String COUNT_USERS              = "select count(*) from users";
    public static final String SELECT_ROLES_BY_USER_ID  = "SELECT r.id, r.role_code, r.name, r.description FROM role_user ru INNER JOIN roles r ON r.id  = ru.role_id WHERE ru.user_id = ?";
    private KeycloakSession keycloakSession;
    private ComponentModel model;

    public AndyUserStorageProvider(KeycloakSession keycloakSession, ComponentModel model) {
        this.keycloakSession = keycloakSession;
        this.model = model;
    }

    @Override
    public void close() {
        log.info("close()");
    }

    @Override
    public UserModel getUserById(String id, RealmModel realm) {
        log.info("getUserById({})",id);
        StorageId sid = new StorageId(id);
        try ( Connection c = DatabaseUtil.getConnection(this.model)) {
            PreparedStatement st = c.prepareStatement(SELECT_USER_BY_ID);
            st.setString(1, sid.getExternalId());
            st.execute();
            return getUserModelAndRoles(realm, st.getResultSet());
        }
        catch(SQLException ex) {
            throw new RuntimeException("Database error:" + ex.getMessage(),ex);
        }
    }

    @Override
    public UserModel getUserByUsername(String username, RealmModel realm) {
        log.info("getUserByUsername({})",username);
        return null;
    }

    @Override
    public UserModel getUserByEmail(String email, RealmModel realm) {
        log.info("getUserByEmail({})",email);
        try ( Connection c = DatabaseUtil.getConnection(this.model)) {
            PreparedStatement st = c.prepareStatement(SELECT_USER_BY_EMAIL);
            st.setString(1, email);
            st.execute();
            return getUserModelAndRoles(realm, st.getResultSet());
        }
        catch(SQLException ex) {
            throw new RuntimeException("Database error:" + ex.getMessage(),ex);
        }
    }

    private UserModel getUserModelAndRoles(RealmModel realm, ResultSet rs) throws SQLException {
        if ( rs.next()) {
            AndyUser user = (AndyUser) mapUser(realm,rs);
            Set<AndyRole> roles = this.getRolesByUserId(rs.getString("id"));
            user.addRoles(roles);
            return (UserModel) user;
        }
        else {
            return null;
        }
    }

    @Override
    public boolean supportsCredentialType(String credentialType) {
        log.info("supportsCredentialType({})",credentialType);
        return PasswordCredentialModel.TYPE.endsWith(credentialType);
    }

    @Override
    public boolean updateCredential(RealmModel realm, UserModel user, CredentialInput input) {
        log.info("updateCredential({})", input.getType());
        if( !this.supportsCredentialType(input.getType())) {
            return false;
        }
        try ( Connection c = DatabaseUtil.getConnection(this.model)) {
            StorageId sid = new StorageId(user.getId());
            String userId = sid.getExternalId();
            String hash = PasswordUtil.convertToPHPHash(input.getChallengeResponse());

            String updateQuery = UPDATE_PASSWORD_BY_ID;
            PreparedStatement st = c.prepareStatement(updateQuery);
            st.setString(1, hash);
            st.setString(2, userId);
            return st.executeUpdate() > 0;
        }
        catch(SQLException ex) {
            throw new RuntimeException("Database error:" + ex.getMessage(),ex);
        }
    }

    @Override
    public void disableCredentialType(RealmModel realm, UserModel user, String credentialType) {}

    @Override
    public Set<String> getDisableableCredentialTypes(RealmModel realm, UserModel user) {
        return null;
    }

    @Override
    public boolean isConfiguredFor(RealmModel realm, UserModel user, String credentialType) {
        log.info("isConfiguredFor(realm={},user={},credentialType={})",realm.getName(), user.getUsername(), credentialType);
        return supportsCredentialType(credentialType);
    }

    @Override
    public boolean isValid(RealmModel realm, UserModel user, CredentialInput credentialInput) {
        log.info("isValid(realm={},user={},credentialInput.type={},enabled={})",realm.getName(), user.getUsername(), credentialInput.getType(), user.isEnabled());
        if( !this.supportsCredentialType(credentialInput.getType())) {
            return false;
        }
        StorageId sid = new StorageId(user.getId());
        String userId = sid.getExternalId();

        try ( Connection c = DatabaseUtil.getConnection(this.model)) {
            PreparedStatement st = c.prepareStatement(SELECT_PASSWORD_BY_ID);
            st.setString(1, userId);
            st.execute();
            ResultSet rs = st.getResultSet();
            if ( rs.next()) {
                String pwd = rs.getString(1);
                return PasswordUtil.check(credentialInput.getChallengeResponse(), pwd);
            }
            else {
                return false;
            }
        }
        catch(SQLException ex) {
            throw new RuntimeException("Database error:" + ex.getMessage(),ex);
        }
    }

    @Override
    public int getUsersCount(RealmModel realm) {
        log.info("getUsersCount: realm={}", realm.getName() );
        try ( Connection c = DatabaseUtil.getConnection(this.model)) {
            Statement st = c.createStatement();
            st.execute(COUNT_USERS);
            ResultSet rs = st.getResultSet();
            rs.next();
            return rs.getInt(1);
        }
        catch(SQLException ex) {
            throw new RuntimeException("Database error:" + ex.getMessage(),ex);
        }
    }

    @Override
    public List<UserModel> getUsers(RealmModel realm) {
        return getUsers(realm, FIRST_RESULT, MAX_RESULTS);
    }

    @Override
    public List<UserModel> getUsers(RealmModel realm, int firstResult, int maxResults) {
        log.info("getUsers: realm={}", realm.getName());

        try ( Connection c = DatabaseUtil.getConnection(this.model)) {
            PreparedStatement st = c.prepareStatement(SELECT_USERS_LIMIT);
            st.setInt(1, maxResults);
            st.setInt(2, firstResult);
            st.execute();
            ResultSet rs = st.getResultSet();
            List<UserModel> users = new ArrayList<>();
            while(rs.next()) {
                users.add(mapUser(realm,rs));
            }
            return users;
        }
        catch(SQLException ex) {
            throw new RuntimeException("Database error:" + ex.getMessage(),ex);
        }
    }

    @Override
    public List<UserModel> searchForUser(String search, RealmModel realm) {
        return searchForUser(search,realm, FIRST_RESULT, MAX_RESULTS);
    }

    @Override
    public List<UserModel> searchForUser(String search, RealmModel realm, int firstResult, int maxResults) {
        log.info("searchForUser: realm={}", realm.getName());

        try ( Connection c = DatabaseUtil.getConnection(this.model)) {
            PreparedStatement st = c.prepareStatement(SEARCH_USERS_BY_EMAIL);
            st.setString(1, search);
            st.setInt(2, maxResults);
            st.setInt(3, firstResult);
            st.execute();
            ResultSet rs = st.getResultSet();
            List<UserModel> users = new ArrayList<>();
            while(rs.next()) {
                users.add(mapUser(realm,rs));
            }
            return users;
        }
        catch(SQLException ex) {
            throw new RuntimeException("Database error:" + ex.getMessage(),ex);
        }
    }

    @Override
    public List<UserModel> searchForUser(Map<String, String> params, RealmModel realm) {
        return searchForUser(params,realm, FIRST_RESULT, MAX_RESULTS);
    }

    @Override
    public List<UserModel> searchForUser(Map<String, String> params, RealmModel realm, int firstResult, int maxResults) {
        return getUsers(realm, firstResult, maxResults);
    }

    @Override
    public List<UserModel> getGroupMembers(RealmModel realm, GroupModel group, int firstResult, int maxResults) {
        return Collections.emptyList();
    }

    @Override
    public List<UserModel> getGroupMembers(RealmModel realm, GroupModel group) {
        return Collections.emptyList();
    }

    @Override
    public List<UserModel> searchForUserByUserAttribute(String attrName, String attrValue, RealmModel realm) {
        return Collections.emptyList();
    }

    private UserModel mapUser(RealmModel realm, ResultSet rs) throws SQLException {
        boolean isEnabled = rs.getInt("is_locked") == 0;
        return new AndyUser.Builder(keycloakSession, realm, model, rs.getString("id"))
          .email(rs.getString("email"))
          .firstName(rs.getString("first_name"))
          .lastName(rs.getString("last_name"))
          .enabled(isEnabled)
          .build();
    }

    private Set<AndyRole> getRolesByUserId(String id) {
        log.info("getRolesByUserId({})",id);
        StorageId sid = new StorageId(id);
        ClientModel clientModel = keycloakSession.getContext().getClient();
        Set<AndyRole> roles = new HashSet<>();
        try ( Connection c = DatabaseUtil.getConnection(this.model)) {
            PreparedStatement st = c.prepareStatement(SELECT_ROLES_BY_USER_ID);
            st.setString(1, sid.getExternalId());
            st.execute();
            ResultSet rs = st.getResultSet();
            while ( rs.next()) {
                AndyRole role = new AndyRole(   rs.getString("id"),
                                                rs.getString("role_code"),
                                                rs.getString("name"),
                                                clientModel);
                roles.add(role);
            }
            return roles;
        }
        catch(SQLException ex) {
            throw new RuntimeException("Database error:" + ex.getMessage(),ex);
        }
    }
}
