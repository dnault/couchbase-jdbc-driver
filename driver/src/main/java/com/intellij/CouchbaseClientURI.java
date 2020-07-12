package com.intellij;

import com.couchbase.client.core.env.Authenticator;
import com.couchbase.client.core.env.CertificateAuthenticator;
import com.couchbase.client.core.env.PasswordAuthenticator;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.ClusterOptions;

import java.nio.file.Paths;
import java.security.KeyStore;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.DriverPropertyInfoHelper.ENABLE_SSL;
import static com.intellij.DriverPropertyInfoHelper.ENABLE_SSL_DEFAULT;
import static com.intellij.DriverPropertyInfoHelper.PASSWORD;
import static com.intellij.DriverPropertyInfoHelper.USER;
import static com.intellij.DriverPropertyInfoHelper.isTrue;

class CouchbaseClientURI {
    static final String PREFIX = "jdbc:couchbase:";

    private static final Set<String> JDBC_KEYS = new HashSet<>(Arrays.asList(USER, PASSWORD, ENABLE_SSL));

    private final String connectionString;
    private final String uri;
    private final String hosts;
    private final String userName;
    private final String password;
    private final boolean sslEnabled;

    public CouchbaseClientURI(String uri, Properties info) {
        this.uri = uri;
        if (!uri.startsWith(PREFIX)) {
            throw new IllegalArgumentException("URI needs to start with " + PREFIX);
        }

        String trimmedUri = uri.substring(PREFIX.length());
        Map<String, List<String>> options = null;
        String serverPart;

        int optionsStartIndex = trimmedUri.indexOf("?");
        if (optionsStartIndex < 0) {
            serverPart = trimmedUri;
        } else {
            serverPart = trimmedUri.substring(0, optionsStartIndex);
            options = parseOptions(trimmedUri.substring(optionsStartIndex + 1));
        }

        this.userName = getOption(info, options, USER, null);
        this.password = getOption(info, options, PASSWORD, null);
        this.sslEnabled = isTrue(getOption(info, options, ENABLE_SSL, ENABLE_SSL_DEFAULT));
        this.hosts = serverPart;
        this.connectionString = createConnectionString(serverPart, options);
    }

    /**
     * @return option from properties or from uri if it is not found in properties.
     * null if options was not found.
     */
    private String getOption(Properties properties, Map<String, List<String>> options, String optionName, String defaultValue) {
        if (properties != null) {
            String option = (String) properties.get(optionName);
            if (option != null) {
                return option;
            }
        }
        String value = getLastValue(options, optionName);
        return value != null ? value : defaultValue;
    }

    Cluster createCluster() throws SQLException {
        Authenticator authenticator;
        if (sslEnabled) {
            String keyStoreType = System.getProperty("javax.net.ssl.keyStoreType", KeyStore.getDefaultType());
            String keyStorePassword = System.getProperty("javax.net.ssl.keyStorePassword", "");
            String keyStorePath = System.getProperty("javax.net.ssl.keyStore", "");
            authenticator = CertificateAuthenticator.fromKeyStore(
                    Paths.get(keyStorePath), keyStorePassword, Optional.of(keyStoreType));
        } else {
            if (userName == null || userName.isEmpty() || password == null) {
                throw new SQLException("Username or password is not provided");
            }
            authenticator = PasswordAuthenticator.create(userName, password);
        }
        return Cluster.connect(connectionString, ClusterOptions.clusterOptions(authenticator));
    }

    private String getLastValue(final Map<String, List<String>> optionsMap, final String key) {
        if (optionsMap == null) return null;
        List<String> valueList = optionsMap.get(key);
        if (valueList == null || valueList.size() == 0) return null;
        return valueList.get(valueList.size() - 1);
    }

    private Map<String, List<String>> parseOptions(String optionsPart) {
        Map<String, List<String>> optionsMap = new HashMap<>();

        for (String _part : optionsPart.split("&")) {
            int idx = _part.indexOf("=");
            if (idx >= 0) {
                String key = _part.substring(0, idx).toLowerCase(Locale.ENGLISH);
                String value = _part.substring(idx + 1);
                List<String> valueList = optionsMap.get(key);
                if (valueList == null) {
                    valueList = new ArrayList<>(1);
                }
                valueList.add(value);
                optionsMap.put(key, valueList);
            }
        }

        return optionsMap;
    }

    private String createConnectionString(String hosts, Map<String, List<String>> optionsMap) {
        if (optionsMap == null) {
            return hosts;
        }
        return optionsMap.keySet().stream()
                .filter(key -> !JDBC_KEYS.contains(key))
                .map(key -> key + "=" + getLastValue(optionsMap, key))
                .collect(Collectors.joining("&", hosts + "?", ""));
    }

    // ---------------------------------

    /**
     * Gets the username
     *
     * @return the username
     */
    public String getUsername() {
        return userName;
    }

    /**
     * Gets the password
     *
     * @return the password
     */
    public String getPassword() {
        return password;
    }

    /**
     * Gets the ssl enabled property
     *
     * @return the ssl enabled property
     */
    public Boolean getSslEnabled() {
        return sslEnabled;
    }

    /**
     * Gets the list of hosts and params sent directly to Java SDK
     *
     * @return the host list
     */
    public String getConnectionString() {
        return connectionString;
    }

    /**
     * Gets the list of hosts
     *
     * @return the host list
     */
    public String getHosts() {
        return hosts;
    }

    /**
     * Get the unparsed URI.
     *
     * @return the URI
     */
    public String getURI() {
        return uri;
    }

    @Override
    public String toString() {
        return uri;
    }
}
