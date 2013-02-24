package org.dasein.cloud.test;

import org.apache.log4j.Logger;
import org.dasein.cloud.CloudProvider;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.compute.VmState;
import org.dasein.cloud.compute.VolumeFormat;
import org.dasein.cloud.network.Firewall;
import org.dasein.cloud.network.FirewallSupport;
import org.dasein.cloud.network.IPVersion;
import org.dasein.cloud.network.NetworkServices;
import org.dasein.cloud.test.compute.ComputeResources;
import org.dasein.cloud.test.identity.IdentityResources;
import org.dasein.cloud.test.network.NetworkResources;
import org.dasein.cloud.util.APITrace;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Properties;
import java.util.TreeSet;

/**
 * Consolidates and manages cloud resources shared across many different tests.
 * <p>Created by George Reese: 2/17/13 3:23 PM</p>
 * @author George Reese
 * @version 2013.04 initial version
 * @since 2013.04
 */
public class DaseinTestManager {
    static public final String STATEFUL  = "stateful";
    static public final String STATELESS = "stateless";
    static public final String REMOVED   = "removed";

    static private HashMap<String,Integer> apiAudit = new HashMap<String, Integer>();

    static private ComputeResources  computeResources;
    static private TreeSet<String>   exclusions;
    static private IdentityResources identityResources;
    static private NetworkResources  networkResources;
    static private TreeSet<String>   inclusions;

    static public @Nonnull CloudProvider constructProvider() {
        String cname = System.getProperty("providerClass");
        CloudProvider provider;

        if( cname == null ) {
            throw new RuntimeException("Invalid class name for provider: " + cname);
        }
        try {
            provider = (CloudProvider)Class.forName(cname).newInstance();
        }
        catch( Exception e ) {
            throw new RuntimeException("Invalid class name " + cname + " for provider: " + e.getMessage());
        }
        ProviderContext ctx = new ProviderContext();

        try {
            String prop;

            prop = System.getProperty("accountNumber");
            if( prop != null ) {
                ctx.setAccountNumber(prop);
            }
            prop = System.getProperty("accessPublic");
            if( prop != null ) {
                ctx.setAccessPublic(prop.getBytes("utf-8"));
            }
            prop = System.getProperty("accessPrivate");
            if( prop != null ) {
                ctx.setAccessPrivate(prop.getBytes("utf-8"));
            }
            prop = System.getProperty("x509CertFile");
            if( prop != null ) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(prop)));
                StringBuilder str = new StringBuilder();
                String line;

                while( (line = reader.readLine()) != null ) {
                    str.append(line);
                    str.append("\n");
                }
                ctx.setX509Cert(str.toString().getBytes("utf-8"));
            }
            prop = System.getProperty("x509KeyFile");
            if( prop != null ) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(prop)));
                StringBuilder str = new StringBuilder();
                String line;

                while( (line = reader.readLine()) != null ) {
                    str.append(line);
                    str.append("\n");
                }
                ctx.setX509Key(str.toString().getBytes("utf-8"));
            }
            prop = System.getProperty("endpoint");
            if( prop != null ) {
                ctx.setEndpoint(prop);
            }
            prop= System.getProperty("cloudName");
            if( prop != null ) {
                ctx.setCloudName(prop);
            }
            prop = System.getProperty("providerName");
            if( prop != null ) {
                ctx.setProviderName(prop);
            }
            prop = System.getProperty("regionId");
            if( prop != null ) {
                ctx.setRegionId(prop);
            }
            prop = System.getProperty("customProperties");
            if( prop != null ) {
                JSONObject json = new JSONObject(prop);
                String[] names = JSONObject.getNames(json);

                if( names != null ) {
                    Properties properties = new Properties();

                    for( String name : names ) {
                        properties.put(name, json.getString(name));
                    }
                    ctx.setCustomProperties(properties);
                }
            }
        }
        catch( UnsupportedEncodingException e ) {
            throw new RuntimeException("UTF-8 unsupported: " + e.getMessage());
        }
        catch( FileNotFoundException e ) {
            throw new RuntimeException("No such file: " + e.getMessage());
        }
        catch( IOException e ) {
            throw new RuntimeException("Failed to read file: " + e.getMessage());
        }
        catch( JSONException e ) {
            throw new RuntimeException("Failed to understand custom properties JSON: " + e.getMessage());
        }
        provider.connect(ctx);
        return provider;
    }

    static public @Nullable ComputeResources getComputeResources() {
        return computeResources;
    }

    static public @Nullable String getDefaultDataCenterId(boolean stateless) {
        return (computeResources == null ? null : computeResources.getTestDataCenterId(stateless));
    }

    static public @Nullable IdentityResources getIdentityResources() {
        return identityResources;
    }

    static public @Nullable NetworkResources getNetworkResources() {
        return networkResources;
    }

    static public void init() {
        networkResources = new NetworkResources(constructProvider());
        identityResources = new IdentityResources(constructProvider());
        computeResources = new ComputeResources(constructProvider());
        computeResources.init();

        String prop = System.getProperty("dasein.inclusions");

        if( prop != null && !prop.equals("") ) {
            inclusions = new TreeSet<String>();
            if( prop.contains(",") ) {
                for( String which : prop.split(",") ) {
                    inclusions.add(which.toLowerCase());
                }
            }
            else {
                inclusions.add(prop.toLowerCase());
            }
        }
        prop = System.getProperty("dasein.exclusions");

        if( prop != null && !prop.equals("") ) {
            exclusions = new TreeSet<String>();
            if( prop.contains(",") ) {
                for( String which : prop.split(",") ) {
                    exclusions.add(which.toLowerCase());
                }
            }
            else {
                exclusions.add(prop.toLowerCase());
            }
        }
        APITrace.report("Init");
        APITrace.reset();
    }

    static public void cleanUp() {
        APITrace.report("Clean Up");
        if( computeResources != null ) {
            computeResources.close();
        }
        if( networkResources != null ) {
            networkResources.close();
        }
        if( identityResources != null ) {
            identityResources.close();
        }

    }

    private Logger                  logger;
    private String                  name;
    private String                  prefix;
    private CloudProvider           provider;
    private long                    startTimestamp;
    private String                  suite;

    public DaseinTestManager(@Nonnull Class<?> testClass) {
        logger = Logger.getLogger(testClass);
        suite = testClass.getSimpleName();
        provider = constructProvider();
        changePrefix();
    }

    public void begin(@Nonnull String name) {
        this.name = name;
        APITrace.report("Setup");
        APITrace.reset();
        changePrefix();
        startTimestamp = System.currentTimeMillis();
        out("");
        out(">>> BEGIN ---------------------------------------------------------------------------------------------->>>");
    }

    private void changePrefix() {
        StringBuilder str = new StringBuilder();
        String s;

        if( suite.endsWith("Test") ) {
            s = suite.substring(0, suite.length()-4);
        }
        else if( suite.endsWith("Tests") ) {
            s = suite.substring(0, suite.length()-5);
        }
        else {
            s = suite;
        }
        str.append(provider.getProviderName()).append("/").append(provider.getCloudName()).append(".").append(s);
        if( name != null ) {
            str.append(".").append(name);
        }
        if( str.length() > 44 ) {
            prefix = str.substring(str.length()-44) + "> ";
        }
        else {
            str.append("> ");
            while( str.length() < 46 ) {
                str.append(" ");
            }
            prefix = str.toString();
        }
    }

    public void close() {
        getProvider().close();
    }

    public void end() {
        String[] calls = APITrace.listApis(provider.getProviderName(), provider.getCloudName());

        if( calls.length > 0 ) {
            out("---------- API Log ----------");
            int total = 0;

            for( String call : calls ) {
                int count = (int)APITrace.getAPICountAcrossAccounts(provider.getProviderName(), provider.getCloudName(), call);

                if( apiAudit.containsKey(call) ) {
                    apiAudit.put(call, count + apiAudit.get(call));
                }
                else {
                    apiAudit.put(call, count);
                }
                out("---> " + call, count);
                total += count;
            }
            out("---> Total Calls", total);
        }
        out("Duration", (((float)(System.currentTimeMillis()-startTimestamp))/1000f) + " seconds");
        out("<<< END   ----------------------------------------------------------------------------------------------<<<");
        out("");
        APITrace.report(prefix);
        APITrace.reset();
        name = null;
        changePrefix();
    }

    public void error(@Nonnull String message) {
        logger.error(prefix + " ERROR: " + message);
    }

    public @Nonnull ProviderContext getContext() {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new RuntimeException("Provider context went away");
        }
        return ctx;
    }

    public @Nullable String getName() {
        return name;
    }

    public @Nonnull String getSuite() {
        return suite;
    }

    public @Nullable String getTestAnyFirewallId(@Nonnull String label, boolean provisionIfNull) {
        NetworkServices services = provider.getNetworkServices();

        if( services != null ) {
            FirewallSupport support = services.getFirewallSupport();

            try {
                if( support != null && support.isSubscribed() ) {
                    if( support.supportsFirewallCreation(false) ) {
                        return getTestGeneralFirewallId(label, provisionIfNull);
                    }
                    else if( support.supportsFirewallCreation(true) ) {
                        return getTestVLANFirewallId(DaseinTestManager.REMOVED, true, null);
                    }
                }
            }
            catch( Throwable ignore ) {
                // ignore
            }
        }
        return null;
    }

    public @Nullable String getTestGeneralFirewallId(@Nonnull String label, boolean provisionIfNull) {
        return (networkResources == null ? null : networkResources.getTestFirewallId(label, provisionIfNull, null));
    }

    public @Nullable String getTestImageId(@Nonnull String label, boolean provisionIfNull) {
        return (computeResources == null ? null : computeResources.getTestImageId(label, provisionIfNull));
    }

    public @Nullable String getTestKeypairId(@Nonnull String label, boolean provisionIfNull) {
        return (identityResources == null ? null : identityResources.getTestKeypairId(label, provisionIfNull));
    }

    public @Nullable String getTestSnapshotId(@Nonnull String label, boolean provisionIfNull) {
        return (computeResources == null ? null : computeResources.getTestSnapshotId(label, provisionIfNull));
    }

    public @Nullable String getTestStaticIpId(@Nonnull String label, boolean provisionIfNull, @Nullable IPVersion version) {
        return networkResources == null ? null : networkResources.getTestStaticIpId(label, provisionIfNull, version);
    }

    public @Nullable String getTestSubnetId(@Nonnull String label, boolean provisionIfNull, @Nullable String vlanId, @Nullable String preferredDataCenterId) {
        return (networkResources == null ? null : networkResources.getTestSubnetId(label, provisionIfNull, vlanId, preferredDataCenterId));
    }

    public @Nullable String getTestVLANFirewallId(@Nonnull String label, boolean provisionIfNull, @Nullable String inVlanId) {
        if( inVlanId == null ) {
            if( label.equals(DaseinTestManager.STATELESS) ) {
                inVlanId = getTestVLANId(DaseinTestManager.STATELESS, false, null);
            }
            else {
                inVlanId = getTestVLANId(DaseinTestManager.STATEFUL, true, null);
            }
            if( inVlanId == null ) {
                return null;
            }
        }
        String id = (networkResources == null ? null : networkResources.getTestFirewallId(label, provisionIfNull, inVlanId));

        if( id != null ) {
            try {
                @SuppressWarnings("ConstantConditions") Firewall firewall = provider.getNetworkServices().getFirewallSupport().getFirewall(id);

                if( firewall == null ) {
                    return null;
                }
                if( !inVlanId.equals(firewall.getProviderVlanId()) ) {
                    return getTestVLANFirewallId(label + "a", provisionIfNull, inVlanId);
                }
            }
            catch( Throwable ignore ) {
                // ignore
            }
        }
        return id;
    }

    public @Nullable String getTestVLANId(@Nonnull String label, boolean provisionIfNull, @Nullable String preferredDataCenterId) {
        return (networkResources == null ? null : networkResources.getTestVLANId(label, provisionIfNull, preferredDataCenterId));
    }

    public @Nullable String getTestVMId(@Nonnull String label, @Nullable VmState desiredState, boolean provisionIfNull, @Nullable String preferredDataCenterId) {
        if( computeResources == null ) {
            return null;
        }
        return computeResources.getTestVmId(label, desiredState, provisionIfNull, preferredDataCenterId);
    }

    public @Nullable String getTestVMProductId() {
        return (computeResources == null ? null : computeResources.getTestVMProductId());
    }

    public @Nullable String getTestVolumeId(@Nonnull String label, boolean provisionIfNull, @Nullable VolumeFormat preferredFormat, @Nullable String preferredDataCenterId) {
        if( computeResources == null ) {
            return null;
        }
        return computeResources.getTestVolumeId(label, provisionIfNull, preferredFormat, preferredDataCenterId);
    }

    public @Nullable String getTestVolumeProductId() {
        return (computeResources == null ? null : computeResources.getTestVolumeProductId());
    }

    public @Nonnull CloudProvider getProvider() {
        return provider;
    }

    /**
     * Checks to see if the test currently being executed is supposed to be skipped.
     * A test is assumed to be run unless there are a list of inclusions and the test is not
     * in the list or there is a list of exclusions and the test is in the list. If there are
     * inclusions and exclusions, any conflict is resolved in favor of executing the test.
     * Exclusions and inclusions are set as the {@link System} properties dasein.inclusions and
     * dasein.exclusions. You may specify an entire suite (e.g. "StatelessVMTests") or a specific
     * test (e.g. "StatelessVMTests.listVirtualMachines"). You may also specify multiple tests:
     * <pre>
     *     -Ddasein.inclusions=StatelessVMTests.listVirtualMachines,StatelessDCTests
     * </pre>
     * This will execute only the listVirtualMachines test from StatelessVMTests and all StatelessDCTests. All other
     * tests will be skipped.
     * @return true if the current test is to be skipped
     */
    public boolean isTestSkipped() {
        if( inclusions == null && exclusions == null ) {
            return false;
        }
        String s = suite.toLowerCase();
        String t = (name == null ? null : name.toLowerCase());

        Boolean suiteIncluded = null;
        Boolean testIncluded = null;

        if( inclusions != null ) {
            if( inclusions.contains(s) ) {
                suiteIncluded = true;
            }
            if( t != null && inclusions.contains(s + "." + t) ) {
                testIncluded = true;
            }
            if( suiteIncluded == null && testIncluded == null ) {
                skip();
                return true;
            }
        }
        if( exclusions != null ) {
            if( t != null && exclusions.contains(s + "." + t) ) {
                if( testIncluded == null || !testIncluded ) {
                    skip();
                    return true;
                }
                logger.debug("Executing (a) " + s + "." + t + " ->\n\t" + inclusions + "\n\t" + exclusions);
                return false; // conflict goes to not skipping
            }
            if( exclusions.contains(s) ) {
                if( testIncluded != null && testIncluded ) {
                    logger.debug("Executing (b) " + s + "." + t + " ->\n\t" + inclusions + "\n\t" + exclusions);
                    return false; // specific test inclusion overrides suite exclusion
                }
                // suite included must be true to get this far
                if( suiteIncluded != null && suiteIncluded ) {
                    logger.debug("Executing (c) " + s + "." + t + " ->\n\t" + inclusions + "\n\t" + exclusions);
                    return false; // conflict goes to skipping
                }
            }
        }
        logger.debug("Executing " + s + "." + t + " ->\n\t" + inclusions + "\n\t" + exclusions);
        return false;
    }

    public void ok(@Nonnull String message) {
        logger.info(prefix + message + " (OK)");
    }

    public void out(@Nonnull String message) {
        logger.info(prefix + message);
    }

    public void out(@Nonnull String key, boolean value) {
        out(key, String.valueOf(value));
    }

    public void out(@Nonnull String key, int value) {
        out(key, String.valueOf(value));
    }

    public void out(@Nonnull String key, long value) {
        out(key, String.valueOf(value));
    }

    public void out(@Nonnull String key, double value) {
        out(key, String.valueOf(value));
    }

    public void out(@Nonnull String key, float value) {
        out(key, String.valueOf(value));
    }

    public void out(@Nonnull String key, Object value) {
        out(key, value == null ? "null" : value.toString());
    }

    public void out(@Nonnull String key, @Nullable String value) {
        StringBuilder str = new StringBuilder();

        if( key.length() > 36 ) {
            str.append(key.substring(0, 36)).append(": ");
        }
        else {
            str.append(key).append(": ");
            while( str.length() < 38 ) {
                str.append(" ");
            }
        }
        out( str.toString() + value);
    }

    public void skip() {
        out("SKIPPING");
    }

    public void warn(@Nonnull String message) {
        logger.warn(prefix + "WARNING: " + message);
    }
}
