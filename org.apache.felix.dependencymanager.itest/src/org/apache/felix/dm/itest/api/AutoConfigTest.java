package org.apache.felix.dm.itest.api;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import junit.framework.Assert;

import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.apache.felix.dm.itest.util.Ensure;
import org.apache.felix.dm.itest.util.TestBase;
import org.osgi.framework.Constants;

@SuppressWarnings({"unchecked", "rawtypes"})
public class AutoConfigTest extends TestBase {
    private final Ensure m_ensure = new Ensure();

    public void testField() throws Exception {
        final DependencyManager dm = getDM();
        // Create a consumer, depending on some providers (autoconfig field).
        ConsumeWithProviderField consumer = new ConsumeWithProviderField();
        Component c = createConsumer(dm, consumer);
        // Create two providers
        Component p1 = createProvider(dm, 10, new Provider() {
            public String toString() { return "provider1"; }
            public void run() { m_ensure.step(); }
        });
        Component p2 = createProvider(dm, 20, new Provider() {
            public String toString() { return "provider2"; }
            public void run() { m_ensure.step(); }
        });

        // add the two providers
        dm.add(p2);
        dm.add(p1);
        // add the consumer, which should have been injected with provider2 (highest rank)
        dm.add(c);
        m_ensure.waitForStep(1, 5000);
        // remove the provider2, the consumer should now be injected with provider1
        dm.remove(p2);
        Assert.assertNotNull(consumer.getProvider());
        Assert.assertEquals("provider1", consumer.getProvider().toString());
        // remove the provider1, the consumer should have been stopped
        dm.remove(p1);
        m_ensure.waitForStep(2, 5000);
        dm.clear();
    }
    
    public void testIterableField() throws Exception {
        final DependencyManager dm = getDM();
        ConsumerWithIterableField consumer = new ConsumerWithIterableField();
        Component c = createConsumer(dm, consumer);
        Component p1 = createProvider(dm, 10, new Provider() {
            public void run() { m_ensure.step(); }
            public String toString() { return "provider1"; }
        });
        Component p2 = createProvider(dm, 20, new Provider() {
            public void run() { m_ensure.step();}
            public String toString() { return "provider2"; }
        });

        dm.add(p2);
        dm.add(p1);
        dm.add(c);
        // the consumer should have been injected with all providers.
        m_ensure.waitForStep(3, 5000);
        
        // check if all providers are there
        Assert.assertNotNull(consumer.getProvider("provider1"));
        Assert.assertNotNull(consumer.getProvider("provider2"));
        
        // remove provider1
        dm.remove(p1);
        
        // check if provider1 has been removed and if provider2 is still there
        Assert.assertNull(consumer.getProvider("provider1"));
        Assert.assertNotNull(consumer.getProvider("provider2"));

        // remove provider2, the consumer should be stopped
        dm.remove(p2);
        m_ensure.waitForStep(4, 5000);
        dm.clear();
    }   
    
    public void testMapField() throws Exception {
        final DependencyManager dm = getDM();
        ConsumerWithMapField consumer = new ConsumerWithMapField();
        Component c = createConsumer(dm, consumer);
        Component p1 = createProvider(dm, 10, new Provider() {
            public void run() { m_ensure.step(); }
            public String toString() { return "provider1"; }
        });
        Component p2 = createProvider(dm, 20, new Provider() {
            public void run() { m_ensure.step();}
            public String toString() { return "provider2"; }
        });

        dm.add(p2);
        dm.add(p1);
        dm.add(c);
        // the consumer should have been injected with all providers.
        m_ensure.waitForStep(3, 5000);
        
        // check if all providers are there
        Assert.assertNotNull(consumer.getProvider("provider1"));
        Assert.assertNotNull(consumer.getProvider("provider2"));
        
        // remove provider1
        dm.remove(p1);
        
        // check if provider1 has been removed and if provider2 is still there
        Assert.assertNull(consumer.getProvider("provider1"));
        Assert.assertNotNull(consumer.getProvider("provider2"));

        // remove provider2, the consumer should be stopped
        dm.remove(p2);
        m_ensure.waitForStep(4, 5000);
        dm.clear();
    }

    private Component createProvider(DependencyManager dm, int rank, Provider provider) {
        Hashtable props = new Hashtable();
        props.put(Constants.SERVICE_RANKING, new Integer(rank));
        return dm.createComponent()
            .setImplementation(provider)
            .setInterface(Provider.class.getName(), props);
    }

    private Component createConsumer(DependencyManager dm, Object consumer) {
        return dm.createComponent()
            .setImplementation(consumer)
            .add(dm.createServiceDependency().setService(Provider.class).setRequired(true));
    }

    public static interface Provider extends Runnable {      
    }
    
    public class ConsumeWithProviderField {
        volatile Provider m_provider;
        
        void start() {
            Assert.assertNotNull(m_provider);
            Assert.assertEquals("provider2", m_provider.toString());
            m_ensure.step(1);
        }
        
        public Provider getProvider() {
            return m_provider;
        }

        void stop() {
            m_ensure.step(2);
        }
    }
    
    public class ConsumerWithIterableField {
        final Iterable<Provider> m_providers = new ConcurrentLinkedQueue<>();
        
        void start() {
            Assert.assertNotNull(m_providers);
            int found = 0;
            for (Provider provider : m_providers) {
                provider.run();
                found ++;
            }
            Assert.assertTrue(found == 2);
            m_ensure.step(3);
        }
        
        public Provider getProvider(String name) {
            System.out.println("getProvider(" + name + ") : proviers=" + m_providers);
            for (Provider provider : m_providers) {
                if (provider.toString().equals(name)) {
                    return provider;
                }
            }
            return null;
        }
        
        void stop() {
            m_ensure.step(4);
        }
    }    
    
    public class ConsumerWithMapField {
        final Map<Provider, Dictionary> m_providers = new ConcurrentHashMap<>();
        
        void start() {
            Assert.assertNotNull(m_providers);
            System.out.println("ConsumerMap.start: injected providers=" + m_providers);
            Assert.assertTrue(m_providers.size() == 2);
            for (Map.Entry<Provider, Dictionary> e : m_providers.entrySet()) {
                Provider provider = e.getKey();
                Dictionary props = e.getValue();
                
                provider.run();
                if (provider.toString().equals("provider1")) {
                    Assert.assertEquals(props.get(Constants.SERVICE_RANKING), 10);
                } else if (provider.toString().equals("provider2")) {
                    Assert.assertEquals(props.get(Constants.SERVICE_RANKING), 20);
                } else {
                    Assert.fail("Did not find any properties for provider " + provider);
                }
            }
            
            m_ensure.step(3);
        }
        
        public Provider getProvider(String name) {
            System.out.println("getProvider(" + name + ") : providers=" + m_providers);
            for (Provider provider : m_providers.keySet()) {
                if (provider.toString().equals(name)) {
                    return provider;
                }
            }
            return null;
        }

        Map<Provider, Dictionary> getProviders() {
            return m_providers;
        }
        
        void stop() {
            m_ensure.step(4);
        }
    }    
}
