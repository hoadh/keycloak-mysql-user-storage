package com.codegym.userstorage;

import com.codegym.userstorage.helpers.DatabaseUtil;
import com.codegym.userstorage.provider.AndyUserStorageProvider;
import org.keycloak.component.ComponentModel;
import org.keycloak.component.ComponentValidationException;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.storage.UserStorageProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.List;

import static com.codegym.userstorage.provider.UserStorageProviderConstants.*;

public class AndyStorageProviderFactory implements UserStorageProviderFactory<AndyUserStorageProvider> {
    private static final Logger log = LoggerFactory.getLogger(AndyStorageProviderFactory.class);
    protected final List<ProviderConfigProperty> configMetadata;

    public AndyStorageProviderFactory() {
        log.info("AndyStorageProviderFactory created");

        // Create config metadata
        configMetadata = ProviderConfigurationBuilder.create()
                .property()
                .name(CONFIG_KEY_JDBC_DRIVER)
                .label("JDBC Driver Class")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue("com.mysql.cj.jdbc.Driver")
                .helpText("Fully qualified class name of the JDBC driver")
                .add()
                .property()
                .name(CONFIG_KEY_JDBC_URL)
                .label("JDBC URL")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue("jdbc:mysql://host:port/database")
                .helpText("JDBC URL used to connect to the user database")
                .add()
                .property()
                .name(CONFIG_KEY_DB_USERNAME)
                .label("Database User")
                .type(ProviderConfigProperty.STRING_TYPE)
                .helpText("Username used to connect to the database")
                .add()
                .property()
                .name(CONFIG_KEY_DB_PASSWORD)
                .label("Database Password")
                .type(ProviderConfigProperty.STRING_TYPE)
                .helpText("Password used to connect to the database")
                .secret(true)
                .add()
                .property()
                .name(CONFIG_KEY_VALIDATION_QUERY)
                .label("SQL Validation Query")
                .type(ProviderConfigProperty.STRING_TYPE)
                .helpText("SQL query used to validate a connection")
                .defaultValue("select * from users limit 1")
                .add()
                .build();
    }

    @Override
    public AndyUserStorageProvider create(KeycloakSession ksession, ComponentModel model) {
        log.info("Creating new AndyUserStorageProvider");
        return new AndyUserStorageProvider(ksession,model);
    }
    @Override
    public String getId() {
        return PROVIDER_NAME;
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return configMetadata;
    }

    @Override
    public void validateConfiguration(KeycloakSession session, RealmModel realm, ComponentModel config) throws ComponentValidationException {
        try (Connection c = DatabaseUtil.getConnection(config)) {
            log.info("Testing connection..." );
            c.createStatement().execute(config.get(CONFIG_KEY_VALIDATION_QUERY));
            log.info("Connection OK !" );
        }
        catch(Exception ex) {
            log.warn("Unable to validate connection: ex={}", ex.getMessage());
            throw new ComponentValidationException("Unable to validate database connection",ex);
        }
    }

    @Override
    public void onUpdate(KeycloakSession session, RealmModel realm, ComponentModel oldModel, ComponentModel newModel) {
        log.info("onUpdate()" );
    }

    @Override
    public void onCreate(KeycloakSession session, RealmModel realm, ComponentModel model) {
        log.info("onCreate()" );
    }
}
